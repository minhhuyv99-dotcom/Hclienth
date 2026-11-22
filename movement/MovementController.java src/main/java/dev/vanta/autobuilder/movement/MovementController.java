package dev.vanta.autobuilder.movement;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public class MovementController {

    public volatile float   forward  = 0f;
    public volatile float   sideways = 0f;
    public volatile boolean jump     = false;

    private static final float CLOSE = 0.5f;

    public void toward(ClientPlayerEntity player, Vec3d target) {
        double dx   = target.x - player.getX();
        double dz   = target.z - player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist < CLOSE) { stop(); return; }

        float yaw   = (float)(Math.atan2(-dx, dz) * 180.0 / Math.PI);
        double dy   = target.y - player.getY();
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.max(dist, 0.01)));

        player.setYaw(yaw);
        player.setPitch(net.minecraft.util.math.MathHelper.clamp(pitch, -80f, 80f));

        forward  = 1.0f;
        sideways = 0.0f;
        if (dy > 0.4) jump = true;
    }

    public void requestJump() { jump = true; }

    /** Move forward in whatever direction the player is currently facing. */
    public void forceForward() { forward = 1.0f; }

    public void stop() {
        forward  = 0f;
        sideways = 0f;
        jump     = false;
    }
}
