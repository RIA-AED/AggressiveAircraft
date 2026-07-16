package dev.ignis.aggressiveaircraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Corrects a unit mismatch in Valkyrien Skies 2.4.10.
 *
 * <p>The server writes {@code Entity#getXRot()} to
 * {@code PacketEntityShipMotion} in degrees. The client handler nevertheless
 * calls {@link Math#toDegrees(double)} on that value before passing it to
 * {@code Entity#lerpTo}. A normal IA ground pitch such as -4 degrees therefore
 * becomes about -229 degrees for observing clients.</p>
 */
@Pseudo
@Mixin(
        targets = "org.valkyrienskies.mod.common.networking.VSGamePackets",
        remap = false
)
public abstract class VsGamePacketsPitchMixin {

    /**
     * The first toDegrees call converts packet yaw (radians) and is correct.
     * The second call handles packet pitch (already degrees), so keep it as-is.
     */
    @Redirect(
            method = "registerHandlers$lambda$11$lambda$6",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Math;toDegrees(D)D",
                    ordinal = 1,
                    remap = false
            ),
            require = 1
    )
    private static double aggressiveAircraft$keepPitchInDegrees(double angrad) {
        return angrad;
    }
}
