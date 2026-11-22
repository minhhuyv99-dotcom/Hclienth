package dev.vanta.autobuilder.gui;

import dev.vanta.autobuilder.builder.AutoBuilder;
import dev.vanta.autobuilder.builder.BuildState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class BuilderScreen extends Screen {

    private static final int W = 310;
    private static final int H = 210;

    private final AutoBuilder builder;

    private TextFieldWidget schematicField;
    private TextFieldWidget xField, yField, zField;

    // ── save input across re-inits ─────────────────────────────────────────
    private static String savedSchematic = "";
    private static String savedX = "0", savedY = "0", savedZ = "0";

    public BuilderScreen(AutoBuilder builder) {
        super(Text.literal("AutoBuilder"));
        this.builder = builder;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Init
    // ══════════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        int px = (this.width  - W) / 2;
        int py = (this.height - H) / 2;

        // ── schematic field ───────────────────────────────────────────────
        schematicField = new TextFieldWidget(textRenderer,
            px + 90, py + 22, 210, 14, Text.empty());
        schematicField.setMaxLength(256);
        schematicField.setPlaceholderText(Text.literal("my_house.nbt"));
        schematicField.setText(savedSchematic);
        addDrawableChild(schematicField);

        // ── origin fields ─────────────────────────────────────────────────
        xField = coord(px + 32,  py + 44, savedX);
        yField = coord(px + 112, py + 44, savedY);
        zField = coord(px + 192, py + 44, savedZ);

        // ── Use Player Position ───────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(
            Text.literal("My Pos"), b -> fillPlayerPos()
        ).dimensions(px + 250, py + 44, 50, 14).build());

        // ── action buttons ────────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(
            Text.literal("▶ Start"), b -> startBuild()
        ).dimensions(px + 10, py + 68, 86, 18).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal("■ Stop"), b -> {
                builder.stop();
                sendMsg("§cAutoBuilder stopped.");
            }
        ).dimensions(px + 103, py + 68, 60, 18).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Set Chest"), b -> setChest()
        ).dimensions(px + 170, py + 68, 80, 18).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal("✕ Close"), b -> this.close()
        ).dimensions(px + 257, py + 68, 44, 18).build());
    }

    private TextFieldWidget coord(int x, int y, String defaultVal) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, 68, 14, Text.empty());
        f.setMaxLength(10);
        f.setText(defaultVal);
        addDrawableChild(f);
        return f;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Render
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        int px = (this.width  - W) / 2;
        int py = (this.height - H) / 2;

        // ── panel background ──────────────────────────────────────────────
        ctx.fill(px - 6, py - 6, px + W + 6, py + H + 6, 0xCC000000);
        // top accent bar
        ctx.fill(px - 6, py - 6, px + W + 6, py - 4, 0xFF5B9BD5);
        // inner border
        ctx.drawBorder(px - 2, py - 2, W + 4, H + 4, 0xFF2A2A2A);

        // ── title ─────────────────────────────────────────────────────────
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("§b⚒ AutoBuilder §8| §7Minecraft 1.21.1"),
            this.width / 2, py + 8, 0xFFFFFF);

        // ── labels ───────────────────────────────────────────────────────
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("§7Schematic:"), px + 10, py + 25, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("§7X:"),  px + 10,  py + 47, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("§7Y:"),  px + 90,  py + 47, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("§7Z:"),  px + 170, py + 47, 0xAAAAAA);

        // ── separator ────────────────────────────────────────────────────
        ctx.fill(px + 8, py + 94, px + W - 8, py + 95, 0xFF444444);

        // ── status ────────────────────────────────────────────────────────
        BuildState state = builder.getState();
        int sColor = stateColor(state);
        ctx.fill(px + 10, py + 100, px + 20, py + 110, sColor);  // status dot
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("§7State: §f" + stateName(state)),
            px + 24, py + 102, 0xFFFFFF);

        // ── progress bar ──────────────────────────────────────────────────
        int total  = builder.getTotalTasks();
        int done   = builder.getCompletedTasks();
        float pct  = total > 0 ? (float) done / total : 0f;

        int barX = px + 10, barY = py + 118, barW = W - 20, barH = 10;
        ctx.fill(barX, barY, barX + barW, barY + barH, 0xFF1A1A1A);
        int fill = (int)(barW * pct);
        if (fill > 0) ctx.fill(barX, barY, barX + fill, barY + barH, sColor);
        ctx.drawBorder(barX - 1, barY - 1, barW + 2, barH + 2, 0xFF555555);

        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal(done + " §8/ §7" + total + " §8(§f" + (int)(pct * 100) + "%§8)"),
            px + W / 2, barY + barH + 4, 0xCCCCCC);

        // ── stats ─────────────────────────────────────────────────────────
        String mode = builder.isCreativeMode()
            ? "§bCreative" : "§aSurvival";
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("§7Scaffolds: §f" + builder.getScaffoldCount() +
                         "  §7Escapes: §f"   + builder.getEscapeCount() +
                         "  §7Mode: " + mode),
            px + 10, py + 143, 0xAAAAAA);

        // ── chest status ──────────────────────────────────────────────────
        BlockPos cp = builder.getChestPos();
        String chestTxt = cp != null
            ? "§a" + cp.getX() + ", " + cp.getY() + ", " + cp.getZ()
            : "§cNot set  §8(use /autobuild setchest or §7Set Chest §8button)";
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("§7Chest: " + chestTxt),
            px + 10, py + 158, 0xAAAAAA);

        // ── hints ─────────────────────────────────────────────────────────
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("§8Schematics: .minecraft/schematics/*.nbt"),
            px + 10, py + 173, 0x666666);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("§8Press §7B §8to toggle this screen"),
            px + 10, py + 184, 0x666666);

        super.render(ctx, mx, my, delta);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Actions
    // ══════════════════════════════════════════════════════════════════════

    private void fillPlayerPos() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        BlockPos p = mc.player.getBlockPos();
        xField.setText(String.valueOf(p.getX()));
        yField.setText(String.valueOf(p.getY()));
        zField.setText(String.valueOf(p.getZ()));
    }

    private void startBuild() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        savedSchematic = schematicField.getText().trim();
        savedX = xField.getText().trim();
        savedY = yField.getText().trim();
        savedZ = zField.getText().trim();

        if (savedSchematic.isEmpty()) {
            sendMsg("§cSchematic name cannot be empty.");
            return;
        }
        int x, y, z;
        try {
            x = Integer.parseInt(savedX);
            y = Integer.parseInt(savedY);
            z = Integer.parseInt(savedZ);
        } catch (NumberFormatException e) {
            sendMsg("§cInvalid coordinates — integers only.");
            return;
        }

        builder.start(mc, savedSchematic, new BlockPos(x, y, z));
    }

    private void setChest() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        builder.setChest(mc.player.getBlockPos());
        sendMsg("§aChest set at " + mc.player.getBlockPos());
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════════

    private static int stateColor(BuildState s) {
        return switch (s) {
            case IDLE                -> 0xFF666666;
            case MOVING_TO_POSITION  -> 0xFF4488FF;
            case PLACING_BLOCK       -> 0xFF44FF44;
            case BREAKING_WRONG_BLOCK-> 0xFFFF4444;
            case BUILDING_SCAFFOLD   -> 0xFFFFAA00;
            case REMOVING_SCAFFOLD   -> 0xFFFF6600;
            case UNSTUCK             -> 0xFFFF00AA;
            case EATING              -> 0xFFAAFF00;
            case GOING_TO_CHEST      -> 0xFF00AAFF;
            case FETCHING_FROM_CHEST -> 0xFF00FFEE;
            case DONE                -> 0xFF00FF00;
        };
    }

    private static String stateName(BuildState s) {
        return switch (s) {
            case IDLE                -> "Idle";
            case MOVING_TO_POSITION  -> "Moving to position";
            case PLACING_BLOCK       -> "Placing block";
            case BREAKING_WRONG_BLOCK-> "Breaking wrong block";
            case BUILDING_SCAFFOLD   -> "Building scaffold";
            case REMOVING_SCAFFOLD   -> "Removing scaffold";
            case UNSTUCK             -> "Escaping stuck";
            case EATING              -> "Eating";
            case GOING_TO_CHEST      -> "Going to chest";
            case FETCHING_FROM_CHEST -> "Fetching items";
            case DONE                -> "Done ✓";
        };
    }

    private static void sendMsg(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null)
            mc.player.sendMessage(Text.literal(msg), false);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        // Save field values before closing
        if (schematicField != null) savedSchematic = schematicField.getText();
        if (xField != null) savedX = xField.getText();
        if (yField != null) savedY = yField.getText();
        if (zField != null) savedZ = zField.getText();
        super.close();
    }
          }
