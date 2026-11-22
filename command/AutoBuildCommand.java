package dev.vanta.autobuilder.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.vanta.autobuilder.AutoBuilderMod;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public final class AutoBuildCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(literal("autobuild")
            .then(literal("start")
                .then(argument("schematic", StringArgumentType.string())
                    .then(argument("x", IntegerArgumentType.integer())
                    .then(argument("y", IntegerArgumentType.integer())
                    .then(argument("z", IntegerArgumentType.integer())
                        .executes(ctx -> {
                            String n = StringArgumentType.getString(ctx, "schematic");
                            int x = IntegerArgumentType.getInteger(ctx, "x");
                            int y = IntegerArgumentType.getInteger(ctx, "y");
                            int z = IntegerArgumentType.getInteger(ctx, "z");
                            var mc = ctx.getSource().getClient();
                            AutoBuilderMod.BUILDER.start(mc, n, new BlockPos(x, y, z));
                            ctx.getSource().sendFeedback(
                                Text.literal("§aStarting at " + x + " " + y + " " + z));
                            return 1;
                        })))
                    .executes(ctx -> {
                        String n = StringArgumentType.getString(ctx, "schematic");
                        var p = ctx.getSource().getClient().player;
                        BlockPos o = p != null ? p.getBlockPos() : BlockPos.ORIGIN;
                        AutoBuilderMod.BUILDER.start(ctx.getSource().getClient(), n, o);
                        ctx.getSource().sendFeedback(
                            Text.literal("§aStarting at player pos " + o));
                        return 1;
                    })
                )
            )
            .then(literal("stop").executes(ctx -> {
                AutoBuilderMod.BUILDER.stop();
                ctx.getSource().sendFeedback(Text.literal("§cStopped."));
                return 1;
            }))
            .then(literal("setchest").executes(ctx -> {
                var p = ctx.getSource().getClient().player;
                if (p != null) {
                    AutoBuilderMod.BUILDER.setChest(p.getBlockPos());
                    ctx.getSource().sendFeedback(
                        Text.literal("§aChest set at " + p.getBlockPos()));
                }
                return 1;
            }))
            .then(literal("status").executes(ctx -> {
                ctx.getSource().sendFeedback(
                    Text.literal(AutoBuilderMod.BUILDER.getStatus()));
                return 1;
            }))
        );
    }
}
