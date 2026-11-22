package dev.vanta.autobuilder.builder;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public class BuildTask {
    public final BlockPos   worldPos;
    public final BlockState targetState;
    public       boolean    completed = false;

    public BuildTask(BlockPos worldPos, BlockState targetState) {
        this.worldPos    = worldPos;
        this.targetState = targetState;
    }
}
