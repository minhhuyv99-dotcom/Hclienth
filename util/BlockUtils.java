package dev.vanta.autobuilder.util;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

public final class BlockUtils {

    private BlockUtils() {}

    public static void lookAt(ClientPlayerEntity player, Vec3d target) {
        Vec3d eye   = player.getEyePos();
        double dx   = target.x - eye.x;
        double dy   = target.y - eye.y;
        double dz   = target.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        player.setYaw((float)(Math.toDegrees(Math.atan2(-dx, dz))));
        player.setPitch(MathHelper.clamp(
            (float)(Math.toDegrees(-Math.atan2(dy, Math.max(horiz, 0.001)))),
            -89f, 89f));
    }

    /**
     * Finds a direction whose adjacent block at targetPos is solid enough to
     * click on to place a block at targetPos.
     */
    public static Direction findPlaceFace(World world, BlockPos targetPos) {
        for (Direction d : Direction.values()) {
            BlockPos adj = targetPos.offset(d);
            BlockState s = world.getBlockState(adj);
            if (!s.isAir() && !s.isLiquid()) return d;
        }
        return null;
    }

    public static void placeBlock(MinecraftClient mc, ClientPlayerEntity player,
                                   BlockPos targetPos, Direction placeFace) {
        BlockPos support = targetPos.offset(placeFace.getOpposite());
        Vec3d    hitVec  = Vec3d.ofCenter(targetPos).offset(placeFace.getOpposite(), 0.5);
        BlockHitResult hit = new BlockHitResult(hitVec, placeFace, support, false);
        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        player.swingHand(Hand.MAIN_HAND);
    }

    /** True if no solid block within 5 blocks below `pos`. */
    public static boolean isAboveVoid(World world, BlockPos pos) {
        for (int y = pos.getY() - 1; y >= pos.getY() - 5; y--) {
            if (y < world.getBottomY()) return true;
            if (!world.getBlockState(new BlockPos(pos.getX(), y, pos.getZ())).isAir())
                return false;
        }
        return true;
    }

    /** True if stepping one block in `dir` from `pos` would be over a void. */
    public static boolean isVoidAhead(World world, BlockPos pos, Direction dir) {
        BlockPos step = pos.offset(dir);
        return world.getBlockState(step).isAir() && isAboveVoid(world, step);
    }

    /** Cardinal Direction that best describes moving from `from` toward `to`. */
    public static Direction horizontalToward(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        if (Math.abs(dx) >= Math.abs(dz))
            return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
