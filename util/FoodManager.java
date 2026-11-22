package dev.vanta.autobuilder.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

/**
 * 1.21.1: food detection uses DataComponentTypes.FOOD instead of item.isFood().
 */
public class FoodManager {

    private static final int HUNGER_THRESHOLD = 14;   // eat below this food level
    private static final int EAT_TICKS        = 52;   // ticks for one eat animation

    private int eatCooldown = 0;

    public boolean needsFood(ClientPlayerEntity player) {
        return player.getHungerManager().getFoodLevel() < HUNGER_THRESHOLD
            && findFoodSlot(player) != -1;
    }

    /** Called each tick in EATING state. Returns true when eating is done. */
    public boolean eatTick(MinecraftClient mc, ClientPlayerEntity player) {
        if (eatCooldown > 0) {
            eatCooldown--;
            if (!player.isUsingItem())
                mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
            return false;
        }

        int slot = findFoodSlot(player);
        if (slot == -1) return true;   // no food — give up and resume

        player.getInventory().selectedSlot = slot;
        mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
        eatCooldown = EAT_TICKS;
        return false;
    }

    public void reset() { eatCooldown = 0; }

    private int findFoodSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++)
            if (isFood(player.getInventory().getStack(i))) return i;
        for (int i = 9; i < 36; i++)
            if (isFood(player.getInventory().getStack(i))) return i;
        return -1;
    }

    private boolean isFood(ItemStack stack) {
        // 1.21.1: component-based food check
        return !stack.isEmpty() && stack.contains(DataComponentTypes.FOOD);
    }
}
