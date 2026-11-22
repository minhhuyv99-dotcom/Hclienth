package dev.vanta.autobuilder;

import dev.vanta.autobuilder.builder.AutoBuilder;
import dev.vanta.autobuilder.command.AutoBuildCommand;
import dev.vanta.autobuilder.gui.BuilderScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoBuilderMod implements ClientModInitializer {

    public static final String      MOD_ID  = "autobuilder";
    public static final Logger      LOG     = LoggerFactory.getLogger(MOD_ID);
    public static final AutoBuilder BUILDER = new AutoBuilder();

    private static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.autobuilder.opengui",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.autobuilder"
        ));

        ClientCommandRegistrationCallback.EVENT.register(
            (dispatcher, reg) -> AutoBuildCommand.register(dispatcher)
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // Open GUI on keybind
            while (openGuiKey.wasPressed()) {
                client.setScreen(new BuilderScreen(BUILDER));
            }

            BUILDER.tick(client);
        });
    }
}
