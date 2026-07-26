package dev.ignis.aggressiveaircraft.compat.vs;

import dev.ignis.aggressiveaircraft.mixin.AircraftEntityLastYAccessor;
import immersive_aircraft.entity.AircraftEntity;
import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

/**
 * Owns the coordinate-frame boundary between Immersive Aircraft and
 * Valkyrien Skies.
 *
 * <p>While an aircraft is supported by a ship, its delta movement is the
 * aircraft's velocity relative to the ship, expressed in world axes. VS is
 * still the sole owner of the rigid deck displacement. The local velocity is
 * retained so it rotates with the ship between ticks.</p>
 */
public final class AircraftShipContext {

    private static final long NO_SHIP = Long.MIN_VALUE;

    private long shipId = NO_SHIP;
    private boolean attached;
    private boolean tickPrepared;

    private ShipTransform tickReferenceTransform;
    private final Vector3d tickStartRelativePosition = new Vector3d();
    private final Vector3d relativePosition = new Vector3d();
    private final Vector3d localRelativeVelocity = new Vector3d();
    private final Vector3d lastRelativeDisplacementWorld = new Vector3d();
    private final Quaterniond relativeOrientation = new Quaterniond();

    private final Quaterniond networkTargetRelativeOrientation = new Quaterniond();
    private int networkOrientationSteps;

    private final Vector3d collisionStartWorld = new Vector3d();
    private final Vector3d collisionRequestedRelativeMovement = new Vector3d();
    private boolean collisionFramePrepared;
    private boolean collisionRelativeMovementResolved;
    private boolean collisionStartedAttached;

    public boolean isAttached() {
        return attached;
    }

    /**
     * Prepares IA physics against the transform on which the entity is
     * currently positioned. VS advances the ship transform before entity
     * ticks and drags entities at the end of the Minecraft tick, so the
     * entity is still expressed against {@code prevTickTransform} here.
     */
    public void beforeAircraftTick(VehicleEntity aircraft) {
        Ship resolvedShip = resolveAttachedShip(aircraft);
        if (resolvedShip == null) {
            finishDetach(aircraft);
            tickPrepared = false;
            return;
        }

        ShipTransform referenceTransform = resolvedShip.getPrevTickTransform();
        if (!attached || shipId != resolvedShip.getId()) {
            attach(aircraft, resolvedShip, referenceTransform);
        }

        tickReferenceTransform = referenceTransform;
        adoptRemoteRelativeVelocity(aircraft);

        referenceTransform.getWorldToShip().transformPosition(
                aircraft.getX(),
                aircraft.getY(),
                aircraft.getZ(),
                tickStartRelativePosition
        );
        relativePosition.set(tickStartRelativePosition);

        Vector3d worldRelativeVelocity = new Vector3d();
        referenceTransform.getShipToWorldRotation()
                .transform(localRelativeVelocity, worldRelativeVelocity);
        aircraft.setDeltaMovement(
                worldRelativeVelocity.x,
                worldRelativeVelocity.y,
                worldRelativeVelocity.z
        );

        if (aircraft instanceof AircraftEntity) {
            ((AircraftEntityLastYAccessor) aircraft)
                    .aggressiveAircraft$setLastY(
                            aircraft.getY() - lastRelativeDisplacementWorld.y
                    );
        }

        draggingInformation(aircraft).setShouldImpulseMovement(false);
        tickPrepared = true;
    }

    /**
     * Captures IA's relative motion and control changes before VS applies the
     * current ship transform at the end of the Minecraft tick.
     */
    public void afterAircraftTick(VehicleEntity aircraft) {
        Ship currentShip = resolveAttachedShip(aircraft);
        if (currentShip == null) {
            finishDetach(aircraft);
            tickPrepared = false;
            return;
        }

        if (!attached || currentShip.getId() != shipId) {
            /*
             * Collision detection can mark an entity as ship-supported during
             * IA's move call, after beforeAircraftTick has already run. Capture
             * the attitude and velocity against the transform that still owns
             * the entity's current position so the first drag cannot snap it
             * to the ship axes. This also handles a direct ship-to-ship switch.
             */
            attach(aircraft, currentShip, currentShip.getPrevTickTransform());
            return;
        }

        if (!tickPrepared || tickReferenceTransform == null) {
            return;
        }

        Vector3d endRelativePosition = new Vector3d();
        tickReferenceTransform.getWorldToShip().transformPosition(
                aircraft.getX(),
                aircraft.getY(),
                aircraft.getZ(),
                endRelativePosition
        );

        Vector3d localDisplacement = endRelativePosition
                .sub(tickStartRelativePosition, new Vector3d());
        tickReferenceTransform.getShipToWorldRotation()
                .transform(localDisplacement, lastRelativeDisplacementWorld);
        relativePosition.set(endRelativePosition);

        Quaterniond inverseReferenceRotation =
                new Quaterniond(tickReferenceTransform.getShipToWorldRotation()).invert();
        inverseReferenceRotation.transform(
                new Vector3d(
                        aircraft.getDeltaMovement().x,
                        aircraft.getDeltaMovement().y,
                        aircraft.getDeltaMovement().z
                ),
                localRelativeVelocity
        );

        Quaterniond worldOrientation = vehicleOrientation(aircraft);
        inverseReferenceRotation.mul(worldOrientation, relativeOrientation).normalize();
        tickPrepared = false;
    }

    /**
     * Runs after VS's EntityDragger. Position remains owned by VS; this method
     * applies the missing pitch/roll composition and rotates IA's relative
     * velocity into the ship's new world orientation.
     */
    public void afterShipDrag(VehicleEntity aircraft) {
        Ship resolvedShip = resolveAttachedShip(aircraft);
        if (resolvedShip == null) {
            finishDetach(aircraft);
            return;
        }

        ShipTransform currentTransform = resolvedShip.getTransform();
        if (!attached || shipId != resolvedShip.getId()) {
            // Defensive fallback for entities that did not run an IA tick.
            attach(aircraft, resolvedShip, currentTransform);
        }
        adoptRemoteRelativeVelocity(aircraft);
        interpolateRemotePosition(aircraft, currentTransform);

        if (networkOrientationSteps > 0) {
            double alpha = 1.0 / networkOrientationSteps;
            relativeOrientation.slerp(networkTargetRelativeOrientation, alpha).normalize();
            networkOrientationSteps--;
        }

        Quaterniond worldOrientation =
                new Quaterniond(currentTransform.getShipToWorldRotation())
                        .mul(relativeOrientation)
                        .normalize();
        applyVehicleOrientation(aircraft, worldOrientation);

        Vector3d worldRelativeVelocity = new Vector3d();
        currentTransform.getShipToWorldRotation()
                .transform(localRelativeVelocity, worldRelativeVelocity);
        aircraft.setDeltaMovement(
                worldRelativeVelocity.x,
                worldRelativeVelocity.y,
                worldRelativeVelocity.z
        );

        currentTransform.getWorldToShip().transformPosition(
                aircraft.getX(),
                aircraft.getY(),
                aircraft.getZ(),
                relativePosition
        );
        draggingInformation(aircraft).setShouldImpulseMovement(false);
    }

    public void prepareCollisionFrame(
            VehicleEntity aircraft,
            Vec3 requestedMovement
    ) {
        collisionStartWorld.set(
                aircraft.getX(),
                aircraft.getY(),
                aircraft.getZ()
        );
        collisionRequestedRelativeMovement.set(
                requestedMovement.x,
                requestedMovement.y,
                requestedMovement.z
        );
        collisionStartedAttached = attached;
        collisionRelativeMovementResolved = false;
        collisionFramePrepared = true;
    }

    public double relativeCollisionMovementLength(
            VehicleEntity aircraft,
            Vec3 requestedMovement
    ) {
        return collisionRequestedRelativeMovement(
                aircraft,
                requestedMovement
        ).length();
    }

    /**
     * Replaces IA's world-space collision error with the amount of requested
     * ship-relative movement that was actually blocked. Rigid deck correction
     * and deck motion are therefore not treated as an aircraft impact.
     */
    public double relativeCollisionError(
            VehicleEntity aircraft,
            Vec3 prediction,
            Vec3 actualPosition,
            Vec3 requestedMovement
    ) {
        Vector3dc requestedRelativeMovement =
                collisionRequestedRelativeMovement(
                        aircraft,
                        requestedMovement
                );
        if (!collisionStartedAttached
                && requestedRelativeMovement.equals(
                        requestedMovement.x,
                        requestedMovement.y,
                        requestedMovement.z
                )) {
            return prediction.distanceTo(actualPosition);
        }

        double requestedLength = requestedRelativeMovement.length();
        if (requestedLength < 1.0E-8) {
            return 0.0;
        }

        Vector3d actualRelativeMovement = new Vector3d(
                actualPosition.x - collisionStartWorld.x,
                actualPosition.y - collisionStartWorld.y,
                actualPosition.z - collisionStartWorld.z
        );
        double actualAlongRequest =
                actualRelativeMovement.dot(requestedRelativeMovement)
                        / requestedLength;
        return Mth.clamp(
                requestedLength - actualAlongRequest,
                0.0,
                requestedLength
        );
    }

    private Vector3dc collisionRequestedRelativeMovement(
            VehicleEntity aircraft,
            Vec3 requestedMovement
    ) {
        if (!collisionFramePrepared) {
            prepareCollisionFrame(aircraft, requestedMovement);
        }
        if (collisionRelativeMovementResolved) {
            return collisionRequestedRelativeMovement;
        }

        collisionRequestedRelativeMovement.set(
                requestedMovement.x,
                requestedMovement.y,
                requestedMovement.z
        );
        if (!collisionStartedAttached) {
            EntityDraggingInformation draggingInformation =
                    draggingInformation(aircraft);
            Long landingShipId = draggingInformation.getLastShipStoodOn();
            if (landingShipId != null
                    && draggingInformation.isEntityBeingDraggedByAShip()) {
                Ship landingShip = ValkyrienSkies.getShipById(
                        aircraft.level(),
                        landingShipId
                );
                if (landingShip != null) {
                    Vector3d surfaceEnd = landingShip.getPrevTickTransform()
                            .getWorldToShip()
                            .transformPosition(
                                    collisionStartWorld,
                                    new Vector3d()
                            );
                    landingShip.getTransform().getShipToWorld()
                            .transformPosition(surfaceEnd);
                    collisionRequestedRelativeMovement.sub(
                            surfaceEnd.x - collisionStartWorld.x,
                            surfaceEnd.y - collisionStartWorld.y,
                            surfaceEnd.z - collisionStartWorld.z
                    );
                }
            }
        }

        collisionRelativeMovementResolved = true;
        return collisionRequestedRelativeMovement;
    }

    /**
     * Finds a dismount position on the ship's real block grid, then validates
     * the resulting world-space passenger box against both world and ship
     * collision shapes.
     */
    @Nullable
    public Vec3 findShipDismountLocation(
            VehicleEntity aircraft,
            LivingEntity passenger,
            float preferredRotationDegrees
    ) {
        Ship ship = attachedShip(aircraft);
        if (ship == null) {
            return null;
        }

        ShipTransform transform = closestShipFrameTransform(aircraft, ship);
        double distance = (
                aircraft.getBbWidth() * Mth.SQRT_OF_TWO
                        + passenger.getBbWidth() * Mth.SQRT_OF_TWO
                        + 1.0E-5
        ) * 0.5;
        float[] rotations = {
                preferredRotationDegrees,
                preferredRotationDegrees + 180.0F,
                preferredRotationDegrees + 90.0F,
                preferredRotationDegrees - 90.0F
        };
        int[] verticalOffsets = {0, -1, 1, -2};

        for (float rotationDegrees : rotations) {
            double rotationRadians = Math.toRadians(rotationDegrees);
            Vector3d localOffset = new Vector3d(
                    -Math.sin(rotationRadians),
                    0.0,
                    Math.cos(rotationRadians)
            ).mul(distance);
            relativeOrientation.transform(localOffset);

            Vector3d candidateLocal =
                    new Vector3d(relativePosition).add(localOffset);
            BlockPos localBase = BlockPos.containing(
                    candidateLocal.x,
                    candidateLocal.y,
                    candidateLocal.z
            );

            for (int verticalOffset : verticalOffsets) {
                BlockPos localExitBlock = localBase.offset(
                        0,
                        verticalOffset,
                        0
                );
                double floorHeight =
                        aircraft.level().getBlockFloorHeight(localExitBlock);
                if (!DismountHelper.isBlockFloorValid(floorHeight)) {
                    continue;
                }

                Vector3d localExit = new Vector3d(
                        candidateLocal.x,
                        localExitBlock.getY() + floorHeight,
                        candidateLocal.z
                );
                Vector3d worldExit = transform.getShipToWorld()
                        .transformPosition(localExit, new Vector3d());
                Vec3 validPosition = findCollisionFreeDismountPose(
                        aircraft,
                        passenger,
                        worldExit
                );
                if (validPosition != null) {
                    ((IEntityDraggingInformationProvider) passenger)
                            .vs$dragImmediately(ship);
                    return validPosition;
                }
            }
        }

        return null;
    }

    /**
     * Dismount may run before or after EntityDragger in the server tick. Pick
     * the ship transform that actually owns the aircraft's current world
     * position so the exit point cannot jump by one deck-motion frame.
     */
    private ShipTransform closestShipFrameTransform(
            VehicleEntity aircraft,
            Ship ship
    ) {
        ShipTransform current = ship.getTransform();
        ShipTransform previous = ship.getPrevTickTransform();
        Vector3d currentWorld = current.getShipToWorld()
                .transformPosition(relativePosition, new Vector3d());
        Vector3d previousWorld = previous.getShipToWorld()
                .transformPosition(relativePosition, new Vector3d());
        Vector3d aircraftPosition = new Vector3d(
                aircraft.getX(),
                aircraft.getY(),
                aircraft.getZ()
        );
        return currentWorld.distanceSquared(aircraftPosition)
                <= previousWorld.distanceSquared(aircraftPosition)
                ? current
                : previous;
    }

    @Nullable
    private static Vec3 findCollisionFreeDismountPose(
            VehicleEntity aircraft,
            LivingEntity passenger,
            Vector3dc floorPosition
    ) {
        for (Pose pose : passenger.getDismountPoses()) {
            for (int upwardStep = 0; upwardStep <= 20; upwardStep++) {
                Vec3 candidate = new Vec3(
                        floorPosition.x(),
                        floorPosition.y() + upwardStep * 0.1 + 1.0E-4,
                        floorPosition.z()
                );
                AABB passengerBounds =
                        passenger.getLocalBoundsForPose(pose).move(candidate);
                if (!DismountHelper.canDismountTo(
                        aircraft.level(),
                        passenger,
                        passengerBounds
                )) {
                    continue;
                }
                if (!EntityShipCollisionUtils.INSTANCE
                        .getShipPolygonsCollidingWithEntity(
                                passenger,
                                Vec3.ZERO,
                                passengerBounds,
                                aircraft.level()
                        )
                        .isEmpty()) {
                    continue;
                }

                passenger.setPose(pose);
                return candidate;
            }
        }

        return null;
    }

    /**
     * VS's packet handler fills its ship-relative three-step interpolation
     * state for every entity, but VS 2.4.10 only consumes that state from a
     * LivingEntity mixin. IA vehicles are plain Entity subclasses and would
     * otherwise fall back to IA's unrelated ten-step world interpolation.
     * Consume the VS target here so position has exactly one interpolator.
     */
    private void interpolateRemotePosition(
            VehicleEntity aircraft,
            ShipTransform currentTransform
    ) {
        if (!aircraft.level().isClientSide
                || aircraft.isControlledByLocalInstance()) {
            return;
        }

        EntityDraggingInformation draggingInformation =
                draggingInformation(aircraft);
        int steps = draggingInformation.getLerpSteps();
        Vector3dc target = draggingInformation.getLerpPositionOnShip();
        Vector3dc current = draggingInformation.getRelativePositionOnShip();
        if (steps <= 0
                || target == null
                || current == null
                || !target.isFinite()
                || !current.isFinite()) {
            return;
        }

        Vector3d interpolated = new Vector3d(current)
                .lerp(target, 1.0 / steps);
        Vector3d worldPosition = new Vector3d();
        currentTransform.getShipToWorld().transformPosition(
                interpolated,
                worldPosition
        );

        aircraft.setPos(worldPosition.x, worldPosition.y, worldPosition.z);
        relativePosition.set(interpolated);
        draggingInformation.setRelativePositionOnShip(interpolated);
        draggingInformation.setLerpSteps(steps - 1);
    }

    /**
     * Captures the rotation target delivered by VS's PacketEntityShipMotion
     * and prevents it from entering IA's independent ten-tick world-space
     * interpolation.
     */
    public boolean handleRemoteLerp(
            VehicleEntity aircraft,
            float shipRelativeYawDegrees,
            float worldPitchDegrees,
            int interpolationSteps
    ) {
        if (!aircraft.level().isClientSide || aircraft.isControlledByLocalInstance()) {
            return false;
        }

        Ship resolvedShip = resolveAttachedShip(aircraft);
        if (resolvedShip == null) {
            return false;
        }

        if (!attached || shipId != resolvedShip.getId()) {
            attach(aircraft, resolvedShip, resolvedShip.getTransform());
        }

        Quaterniondc shipRotation = resolvedShip.getTransform().getShipToWorldRotation();
        double relativeYawRadians = Math.toRadians(shipRelativeYawDegrees);
        Vector3d worldForward = new Vector3d(
                Math.sin(relativeYawRadians),
                0.0,
                Math.cos(relativeYawRadians)
        );
        shipRotation.transform(worldForward);

        float worldYawDegrees = (float) Math.toDegrees(
                Math.atan2(worldForward.x, worldForward.z)
        );
        float worldRollDegrees =
                aircraft instanceof AircraftEntity aircraftEntity
                        ? aircraftEntity.getRoll()
                        : 0.0F;

        Quaterniond targetWorldOrientation = vehicleOrientation(
                worldYawDegrees,
                worldPitchDegrees,
                worldRollDegrees
        );
        new Quaterniond(shipRotation)
                .invert()
                .mul(targetWorldOrientation, networkTargetRelativeOrientation)
                .normalize();
        networkOrientationSteps = Math.max(1, Math.min(3, interpolationSteps));
        return true;
    }

    private void attach(
            VehicleEntity aircraft,
            Ship resolvedShip,
            ShipTransform referenceTransform
    ) {
        attached = true;
        shipId = resolvedShip.getId();
        tickPrepared = false;
        networkOrientationSteps = 0;
        lastRelativeDisplacementWorld.zero();

        referenceTransform.getWorldToShip().transformPosition(
                aircraft.getX(),
                aircraft.getY(),
                aircraft.getZ(),
                relativePosition
        );
        tickStartRelativePosition.set(relativePosition);

        Quaterniond inverseShipRotation =
                new Quaterniond(referenceTransform.getShipToWorldRotation()).invert();
        inverseShipRotation.transform(
                new Vector3d(
                        aircraft.getDeltaMovement().x,
                        aircraft.getDeltaMovement().y,
                        aircraft.getDeltaMovement().z
                ),
                localRelativeVelocity
        );
        inverseShipRotation
                .mul(vehicleOrientation(aircraft), relativeOrientation)
                .normalize();
    }

    private Ship resolveAttachedShip(VehicleEntity aircraft) {
        EntityDraggingInformation draggingInformation = draggingInformation(aircraft);
        Long lastShipStoodOn = draggingInformation.getLastShipStoodOn();
        if (lastShipStoodOn == null
                || !draggingInformation.isEntityBeingDraggedByAShip()) {
            return null;
        }

        return ValkyrienSkies.getShipById(
                aircraft.level(),
                lastShipStoodOn
        );
    }

    @Nullable
    private Ship attachedShip(VehicleEntity aircraft) {
        if (!attached || shipId == NO_SHIP) {
            return null;
        }
        return ValkyrienSkies.getShipById(aircraft.level(), shipId);
    }

    /**
     * VS normally injects the previous deck displacement at EntityDragger's
     * tick tail. Apply it before IA physics instead, so lift, drag, collision,
     * and the first airborne move all see the correct inherited momentum.
     */
    private void finishDetach(VehicleEntity aircraft) {
        if (!attached) {
            return;
        }

        EntityDraggingInformation draggingInformation =
                draggingInformation(aircraft);
        Vector3dc deckMovement = draggingInformation.getAddedMovementLastTick();
        if (deckMovement.isFinite()) {
            Vec3 relativeMovement = aircraft.getDeltaMovement();
            aircraft.setDeltaMovement(
                    relativeMovement.x + deckMovement.x(),
                    relativeMovement.y + deckMovement.y(),
                    relativeMovement.z + deckMovement.z()
            );
            aircraft.hasImpulse = true;
        }

        /*
         * Consume the VS exit impulse exactly once. EntityDragger will see a
         * zero vector later in this tick and cannot apply it a second time.
         */
        draggingInformation.setAddedMovementLastTick(new Vector3d());
        draggingInformation.setAddedYawRotLastTick(0.0);
        draggingInformation.setShouldImpulseMovement(false);
        detach();
    }

    private void adoptRemoteRelativeVelocity(VehicleEntity aircraft) {
        if (!aircraft.level().isClientSide || aircraft.isControlledByLocalInstance()) {
            return;
        }

        Vector3dc networkVelocity =
                draggingInformation(aircraft).getRelativeVelocityOnShip();
        if (networkVelocity != null && networkVelocity.isFinite()) {
            localRelativeVelocity.set(networkVelocity);
        }
    }

    private void detach() {
        attached = false;
        shipId = NO_SHIP;
        tickPrepared = false;
        tickReferenceTransform = null;
        networkOrientationSteps = 0;
        lastRelativeDisplacementWorld.zero();
    }

    private static EntityDraggingInformation draggingInformation(
            VehicleEntity aircraft
    ) {
        return ((IEntityDraggingInformationProvider) aircraft)
                .getDraggingInformation();
    }

    private static Quaterniond vehicleOrientation(VehicleEntity aircraft) {
        float roll =
                aircraft instanceof AircraftEntity aircraftEntity
                        ? aircraftEntity.getRoll()
                        : 0.0F;
        return vehicleOrientation(
                aircraft.getYRot(),
                aircraft.getXRot(),
                roll
        );
    }

    private static Quaterniond vehicleOrientation(
            float yawDegrees,
            float pitchDegrees,
            float rollDegrees
    ) {
        return new Quaterniond()
                .rotateY(Math.toRadians(-yawDegrees))
                .rotateX(Math.toRadians(pitchDegrees))
                .rotateZ(Math.toRadians(rollDegrees));
    }

    private static void applyVehicleOrientation(
            VehicleEntity aircraft,
            Quaterniondc orientation
    ) {
        Vector3d euler = orientation.getEulerAnglesYXZ(new Vector3d());
        float yaw = unwrapDegrees(
                (float) -Math.toDegrees(euler.y),
                aircraft.getYRot()
        );
        float pitch = unwrapDegrees(
                (float) Math.toDegrees(euler.x),
                aircraft.getXRot()
        );

        aircraft.setYRot(yaw);
        aircraft.setXRot(pitch);
        if (aircraft instanceof AircraftEntity aircraftEntity) {
            float roll = unwrapDegrees(
                    (float) Math.toDegrees(euler.z),
                    aircraftEntity.getRoll()
            );
            aircraftEntity.setZRot(roll);
        }
    }

    private static float unwrapDegrees(float angle, float reference) {
        return reference + Mth.wrapDegrees(angle - reference);
    }
}
