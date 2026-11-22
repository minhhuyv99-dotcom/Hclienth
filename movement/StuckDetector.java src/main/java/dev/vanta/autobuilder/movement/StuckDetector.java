package dev.vanta.autobuilder.movement;

import net.minecraft.util.math.Vec3d;

public class StuckDetector {

    private static final double EPSILON   = 0.05;
    private static final int    THRESHOLD = 4;

    private Vec3d lastPos    = null;
    private int   stuckCount = 0;

    public void sample(Vec3d pos) {
        if (lastPos != null && pos.distanceTo(lastPos) < EPSILON) {
            stuckCount++;
        } else {
            stuckCount = 0;
        }
        lastPos = pos;
    }

    public boolean isStuck() { return stuckCount >= THRESHOLD; }

    public void reset() {
        stuckCount = 0;
        lastPos    = null;
    }
}
