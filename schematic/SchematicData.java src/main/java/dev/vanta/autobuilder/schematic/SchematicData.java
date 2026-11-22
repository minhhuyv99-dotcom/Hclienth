package dev.vanta.autobuilder.schematic;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import java.util.List;

public record SchematicData(int sizeX, int sizeY, int sizeZ,
                             List<PlacedBlock> blocks) {
    public record PlacedBlock(BlockPos localPos, BlockState state) {}
}
