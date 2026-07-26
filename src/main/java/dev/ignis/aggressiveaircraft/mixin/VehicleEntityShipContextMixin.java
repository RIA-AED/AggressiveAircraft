package dev.ignis.aggressiveaircraft.mixin;

import dev.ignis.aggressiveaircraft.compat.vs.AircraftShipContext;
import dev.ignis.aggressiveaircraft.compat.vs.AircraftShipContextProvider;
import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VehicleEntity.class, remap = false)
public abstract class VehicleEntityShipContextMixin
        implements AircraftShipContextProvider {

    @Unique
    private AircraftShipContext aggressiveAircraft$shipContext;

    @Shadow
    protected int interpolationSteps;

    @Shadow
    protected abstract float getDismountRotation();

    @Override
    public AircraftShipContext aggressiveAircraft$getShipContext() {
        if (aggressiveAircraft$shipContext == null) {
            aggressiveAircraft$shipContext = new AircraftShipContext();
        }
        return aggressiveAircraft$shipContext;
    }

    @Inject(method = "tick", at = @At("HEAD"), remap = true)
    private void aggressiveAircraft$beforeAircraftTick(CallbackInfo ci) {
        VehicleEntity aircraft = (VehicleEntity) (Object) this;
        aggressiveAircraft$getShipContext().beforeAircraftTick(aircraft);
    }

    @Inject(method = "tick", at = @At("TAIL"), remap = true)
    private void aggressiveAircraft$afterAircraftTick(CallbackInfo ci) {
        VehicleEntity aircraft = (VehicleEntity) (Object) this;
        aggressiveAircraft$getShipContext().afterAircraftTick(aircraft);
    }

    @Inject(method = "handleClientSync", at = @At("HEAD"), cancellable = true)
    private void aggressiveAircraft$useVsInterpolation(CallbackInfo ci) {
        VehicleEntity aircraft = (VehicleEntity) (Object) this;
        if (aircraft.level().isClientSide
                && !aircraft.isControlledByLocalInstance()
                && aggressiveAircraft$getShipContext().isAttached()) {
            interpolationSteps = 0;
            ci.cancel();
        }
    }

    @Inject(
            method = "lerpTo",
            at = @At("HEAD"),
            cancellable = true,
            remap = true
    )
    private void aggressiveAircraft$captureVsRotationTarget(
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            int interpolationSteps,
            boolean interpolate,
            CallbackInfo ci
    ) {
        VehicleEntity aircraft = (VehicleEntity) (Object) this;
        if (aggressiveAircraft$getShipContext()
                .handleRemoteLerp(
                        aircraft, yaw, pitch, interpolationSteps)) {
            this.interpolationSteps = 0;
            ci.cancel();
        }
    }

    @Inject(method = "move", at = @At("HEAD"), remap = true)
    private void aggressiveAircraft$prepareRelativeCollision(
            MoverType movementType,
            Vec3 movement,
            CallbackInfo ci
    ) {
        VehicleEntity aircraft = (VehicleEntity) (Object) this;
        aggressiveAircraft$getShipContext().prepareCollisionFrame(
                aircraft,
                movement
        );
    }

    @Redirect(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;length()D"
            ),
            remap = true,
            require = 1
    )
    private double aggressiveAircraft$useRelativeCollisionMovementLength(
            Vec3 movementReceiver,
            MoverType movementType,
            Vec3 movement
    ) {
        VehicleEntity aircraft = (VehicleEntity) (Object) this;
        return aggressiveAircraft$getShipContext()
                .relativeCollisionMovementLength(aircraft, movement);
    }

    @Redirect(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D"
            ),
            remap = true,
            require = 1
    )
    private double aggressiveAircraft$useRelativeCollisionSpeed(
            Vec3 prediction,
            Vec3 p_82555_,
            MoverType movementType,
            Vec3 movement
    ) {
        VehicleEntity aircraft = (VehicleEntity) (Object) this;
        return aggressiveAircraft$getShipContext().relativeCollisionError(
                aircraft,
                prediction,
                p_82555_,
                movement
        );
    }

    @Inject(
            method = "getDismountLocationForPassenger",
            at = @At("HEAD"),
            cancellable = true,
            remap = true
    )
    private void aggressiveAircraft$findShipDismountLocation(
            LivingEntity passenger,
            CallbackInfoReturnable<Vec3> cir
    ) {
        VehicleEntity aircraft = (VehicleEntity) (Object) this;
        Vec3 dismountLocation = aggressiveAircraft$getShipContext()
                .findShipDismountLocation(
                        aircraft,
                        passenger,
                        getDismountRotation()
                );
        if (dismountLocation != null) {
            cir.setReturnValue(dismountLocation);
        }
    }
}
