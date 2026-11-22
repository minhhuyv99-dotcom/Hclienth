package dev.vanta.autobuilder.scaffold;

import dev.vanta.autobuilder.util.BlockUtils;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class ScaffoldManager {

    private final Deque<BlockPos> placed = new ArrayDeque<>();
    private final Set<BlockPos>   set    = new HashSet<>();

    private static final net.minecraft.block.Block[] SCAFFOLD_MATERIALS = {
        Blocks.DIRT, Blocks.COBBLESTONE, Blocks.GRAVEL,
        Blocks.SAND, Blocks.STONE, Blocks.NETHERRACK, Blocks.COBBLED_DEEPSLATE
    };

    public boolean placeBlock(MinecraftClient mc, ClientPlayerEntity player, BlockPos pos) {
        if (!mc.world.getBlockState(pos).isAir()) return false;

        Direction face = BlockUtils.findPlaceFace(mc.world, pos);
        if (face == null) return false;

        equipScaffold(mc, player);

        BlockPos support = pos.offset(face.getOpposite());
        BlockUtils.lookAt(player, Vec3d.ofCenter(support));

        BlockHitResult hit = new BlockHitResult(
            Vec3d.ofCenter(pos).offset(face, -0.5), face, support, false);

        var result = mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) {
            placed.addLast(pos);
            set.add(pos);
            player.swingHand(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }

    public int     count()                { return placed.size(); }
    public boolean isScaffold(BlockPos p) { return set.contains(p); }

    public BlockPos nextToRemove() {
        return placed.isEmpty() ? null : placed.peekLast();
    }

    public void markRemoved(BlockPos pos) {
        placed.remove(pos);
        set.remove(pos);
    }

    public void reset() {
        placed.clear();
        set.clear();
    }

    private void equipScaffold(MinecraftClient mc, ClientPlayerEntity player) {
        if (mc.player.getAbilities().creativeMode) {
            int slot = player.getInventory().selectedSlot;
            ItemStack dirt = new ItemStack(Blocks.DIRT.asItem(), 64);
            player.getInventory().setStack(slot, dirt);
            mc.interactionManager.clickCreativeStack(dirt, 36 + slot);
            return;
        }
        for (net.minecraft.block.Block b : SCAFFOLD_MATERIALS) {
            for (int i = 0; i < 9; i++) {
                if (player.getInventory().getStack(i).getItem() == b.asItem()) {
                    player.getInventory().selectedSlot = i;
                    return;
                }
            }
        }
    }
}
