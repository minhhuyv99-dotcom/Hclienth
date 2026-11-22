package dev.vanta.autobuilder.inventory;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.screen.slot.SlotActionType;

public class InventoryManager {

    /** True if any hotbar/inventory slot holds the block's item. */
    public boolean has(ClientPlayerEntity player, Block block) {
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getStack(i).getItem() == block.asItem())
                return true;
        }
        return false;
    }

    public void equip(MinecraftClient mc, ClientPlayerEntity player, Block block) {
        if (mc.player.getAbilities().creativeMode) {
            equipCreative(mc, player, block);
            return;
        }
        // Hotbar first
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() == block.asItem()) {
                player.getInventory().selectedSlot = i;
                return;
            }
        }
        // Swap from main inventory
        for (int inv = 9; inv < 36; inv++) {
            if (player.getInventory().getStack(inv).getItem() == block.asItem()) {
                int hot = freeHotbarSlot(player);
                mc.interactionManager.clickSlot(
                    player.playerScreenHandler.syncId,
                    inv, hot, SlotActionType.SWAP, player);
                player.getInventory().selectedSlot = hot;
                return;
            }
        }
    }

    private void equipCreative(MinecraftClient mc, ClientPlayerEntity player, Block block) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() == block.asItem()) {
                player.getInventory().selectedSlot = i;
                return;
            }
        }
        int slot = player.getInventory().selectedSlot;
        ItemStack stack = new ItemStack(block.asItem(), 64);
        player.getInventory().setStack(slot, stack);
        // Sync to server via creative pick-block packet
        mc.interactionManager.clickCreativeStack(stack, 36 + slot);
    }

    public void equipBestTool(MinecraftClient mc, ClientPlayerEntity player,
                               BlockState state) {
        if (mc.player.getAbilities().creativeMode) return;

        int best = player.getInventory().selectedSlot;
        float bestSpd = player.getInventory().getStack(best)
                              .getMiningSpeedMultiplier(state);

        for (int i = 0; i < 9; i++) {
            ItemStack s   = player.getInventory().getStack(i);
            float spd     = s.getMiningSpeedMultiplier(state);
            if (spd > bestSpd && !(s.getItem() instanceof SwordItem)) {
                bestSpd = spd;
                best    = i;
            }
        }
        player.getInventory().selectedSlot = best;
    }

    private int freeHotbarSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isEmpty()) return i;
        }
        return 7; // last-resort: evict slot 7
    }
}
