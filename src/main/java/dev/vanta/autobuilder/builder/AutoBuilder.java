package dev.vanta.autobuilder.builder;

import dev.vanta.autobuilder.AutoBuilderMod;
import dev.vanta.autobuilder.inventory.ChestManager;
import dev.vanta.autobuilder.inventory.InventoryManager;
import dev.vanta.autobuilder.movement.MovementController;
import dev.vanta.autobuilder.movement.StuckDetector;
import dev.vanta.autobuilder.scaffold.ScaffoldManager;
import dev.vanta.autobuilder.schematic.SchematicData;
import dev.vanta.autobuilder.schematic.SchematicReader;
import dev.vanta.autobuilder.util.BlockUtils;
import dev.vanta.autobuilder.util.FoodManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AutoBuilder {

    // ── state ─────────────────────────────────────────────────────────────
    private volatile BuildState state     = BuildState.IDLE;
    private volatile BuildState prevState = BuildState.IDLE;

    // ── build data ────────────────────────────────────────────────────────
    private SchematicData        schematic;
    private BlockPos             origin;
    private final List<BuildTask> buildQueue = new ArrayList<>();
    private volatile int         taskIndex  = 0;

    // ── subsystems ────────────────────────────────────────────────────────
    private final MovementController movement  = new MovementController();
    private final StuckDetector      stuck     = new StuckDetector();
    private final ScaffoldManager    scaffold  = new ScaffoldManager();
    private final InventoryManager   inventory = new InventoryManager();
    private final ChestManager       chest     = new ChestManager();
    private final FoodManager        food      = new FoodManager();

    // ── misc ──────────────────────────────────────────────────────────────
    private volatile BlockPos chestPos       = null;
    private          int      globalTick     = 0;
    private          int      breakTicks     = 0;
    private volatile BlockPos breakingPos    = null;
    private          Direction breakingFace  = Direction.UP;
    private          int      scaffoldPause  = 0;

    // ── stats exposed to GUI ──────────────────────────────────────────────
    private volatile int escapeCount        = 0;
    private volatile boolean creativeMode   = false;

    // ══════════════════════════════════════════════════════════════════════
    //  Public API — called from GUI and commands
    // ══════════════════════════════════════════════════════════════════════

    public void start(MinecraftClient mc, String name, BlockPos origin) {
        this.origin = origin;
        reset();

        File file = resolve(mc, name);
        if (file == null) {
            chat(mc, "§cSchematic not found: §f" + name +
                     "\n§7Place .nbt in §f.minecraft/schematics/");
            return;
        }

        try {
            schematic = SchematicReader.read(file);
        } catch (IOException e) {
            chat(mc, "§cRead error: " + e.getMessage());
            AutoBuilderMod.LOG.error("Schematic read failed", e);
            return;
        }

        buildPlan();
        creativeMode = mc.player != null && mc.player.getAbilities().creativeMode;
        state = BuildState.MOVING_TO_POSITION;
        chat(mc, "§aAutoBuilder started — §f" + buildQueue.size() + " §ablocks. " +
                 (creativeMode ? "§b[Creative]" : "§a[Survival]"));
    }

    public void stop() {
        reset();
        state = BuildState.IDLE;
    }

    public void setChest(BlockPos pos) {
        chestPos = pos;
        AutoBuilderMod.LOG.info("Chest position set: {}", pos);
    }

    public boolean isActive() {
        return state != BuildState.IDLE && state != BuildState.DONE;
    }

    // ── GUI getters ───────────────────────────────────────────────────────
    public BuildState getState()          { return state; }
    public int        getTotalTasks()     { return buildQueue.size(); }
    public int        getCompletedTasks() { return Math.min(taskIndex, buildQueue.size()); }
    public int        getScaffoldCount()  { return scaffold.count(); }
    public int        getEscapeCount()    { return escapeCount; }
    public BlockPos   getChestPos()       { return chestPos; }
    public boolean    isCreativeMode()    { return creativeMode; }
    public MovementController getMovement() { return movement; }

    public String getStatus() {
        return String.format("[AutoBuilder] %s | %d/%d blocks | scaffolds: %d | escapes: %d",
            state, taskIndex, buildQueue.size(), scaffold.count(), escapeCount);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Tick — END_CLIENT_TICK
    // ══════════════════════════════════════════════════════════════════════

    public void tick(MinecraftClient mc) {
        if (state == BuildState.IDLE || state == BuildState.DONE) {
            movement.stop();
            return;
        }

        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        globalTick++;

        // Update creative mode flag each tick in case /gamemode changed
        creativeMode = player.getAbilities().creativeMode;

        // Food priority (not during unstuck/chest ops)
        if (state != BuildState.EATING
            && state != BuildState.UNSTUCK
            && state != BuildState.GOING_TO_CHEST
            && state != BuildState.FETCHING_FROM_CHEST) {
            if (food.needsFood(player)) {
                prevState = state;
                state     = BuildState.EATING;
            }
        }

        // Stuck detection — sample every 40 ticks while walking
        if (state == BuildState.MOVING_TO_POSITION && globalTick % 40 == 0) {
            stuck.sample(player.getPos());
            if (stuck.isStuck()) {
                AutoBuilderMod.LOG.info("Stuck at {} — escaping", player.getBlockPos());
                prevState = state;
                state     = BuildState.UNSTUCK;
                stuck.reset();
            }
        }

        switch (state) {
            case EATING               -> tickEating(mc, player);
            case GOING_TO_CHEST       -> tickGoingToChest(mc, player);
            case FETCHING_FROM_CHEST  -> tickFetchingFromChest(mc, player);
            case MOVING_TO_POSITION   -> tickMoving(mc, player);
            case BUILDING_SCAFFOLD    -> tickBuildScaffold(mc, player);
            case PLACING_BLOCK        -> tickPlaceBlock(mc, player);
            case BREAKING_WRONG_BLOCK -> tickBreakBlock(mc, player);
            case REMOVING_SCAFFOLD    -> tickRemoveScaffold(mc, player);
            case UNSTUCK              -> tickUnstuck(mc, player);
            default -> {}
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Build plan
    // ══════════════════════════════════════════════════════════════════════

    private void buildPlan() {
        buildQueue.clear();
        for (SchematicData.PlacedBlock pb : schematic.blocks()) {
            buildQueue.add(
                new BuildTask(origin.add(pb.localPos()), pb.state()));
        }
        // Bottom → top, then sweep XZ
        buildQueue.sort(Comparator
            .comparingInt((BuildTask t) -> t.worldPos.getY())
            .thenComparingInt(t -> t.worldPos.getX())
            .thenComparingInt(t -> t.worldPos.getZ())
        );
    }

    // ══════════════════════════════════════════════════════════════════════
    //  State handlers
    // ══════════════════════════════════════════════════════════════════════

    // ── eating ────────────────────────────────────────────────────────────
    private void tickEating(MinecraftClient mc, ClientPlayerEntity player) {
        movement.stop();
        if (food.eatTick(mc, player)) state = prevState;
    }

    // ── moving ────────────────────────────────────────────────────────────
    private void tickMoving(MinecraftClient mc, ClientPlayerEntity player) {
        // All tasks done → tear down scaffold
        if (taskIndex >= buildQueue.size()) {
            movement.stop();
            state = BuildState.REMOVING_SCAFFOLD;
            return;
        }

        BuildTask task = buildQueue.get(taskIndex);

        // Already correct — skip
        if (blockCorrect(mc, task)) {
            task.completed = true;
            taskIndex++;
            return;
        }

        // Inventory / item check
        if (!hasItem(mc, player, task)) {
            if (!creativeMode && chestPos != null) {
                prevState = BuildState.MOVING_TO_POSITION;
                state     = BuildState.GOING_TO_CHEST;
            } else {
                AutoBuilderMod.LOG.warn("Missing item for {} — skipping",
                    task.targetState.getBlock());
                taskIndex++;
            }
            return;
        }

        // Height — do we need to scaffold?
        int heightDiff = task.worldPos.getY() - player.getBlockY();
        if (heightDiff > 4) {
            movement.stop();
            state = BuildState.BUILDING_SCAFFOLD;
            return;
        }

        // Find stand position
        Vec3d standPos = standPos(mc, task.worldPos);
        if (standPos == null) { taskIndex++; return; }

        // Void ahead? → bridge
        Direction moveDir = BlockUtils.horizontalToward(player.getPos(), standPos);
        if (moveDir != null
            && BlockUtils.isVoidAhead(mc.world, player.getBlockPos(), moveDir)) {
            movement.stop();
            BlockPos bridgePos = player.getBlockPos().offset(moveDir).down();
            if (mc.world.getBlockState(bridgePos).isAir())
                scaffold.placeBlock(mc, player, bridgePos);
            return;
        }

        // Obstacle directly ahead → jump over or place scaffold step
        BlockPos ahead    = player.getBlockPos().offset(moveDir != null ? moveDir : Direction.NORTH);
        BlockPos aheadTop = ahead.up();
        if (!mc.world.getBlockState(ahead).isAir()
            && mc.world.getBlockState(aheadTop).isAir()) {
            movement.requestJump();
        }

        double dist = player.getPos().distanceTo(standPos);
        if (dist < 0.6) {
            movement.stop();
            state = BuildState.PLACING_BLOCK;
        } else {
            movement.toward(player, standPos);
        }
    }

    // ── scaffold building ─────────────────────────────────────────────────
    private void tickBuildScaffold(MinecraftClient mc, ClientPlayerEntity player) {
        BuildTask task = buildQueue.get(taskIndex);
        int heightDiff = task.worldPos.getY() - player.getBlockY();

        if (heightDiff <= 4) {
            movement.stop();
            state = BuildState.MOVING_TO_POSITION;
            return;
        }

        if (scaffoldPause > 0) { scaffoldPause--; return; }

        BlockPos col = scaffoldColumn(player, task.worldPos, mc);

        double colDist = Math.sqrt(
            Math.pow(player.getX() - col.getX() - 0.5, 2) +
            Math.pow(player.getZ() - col.getZ() - 0.5, 2));

        if (colDist > 0.5) {
            movement.toward(player, Vec3d.ofCenter(col));
            return;
        }

        // Place one block above current foot level
        BlockPos newScaffold = new BlockPos(col.getX(), player.getBlockY() + 1, col.getZ());
        if (mc.world.getBlockState(newScaffold).isAir()) {
            boolean placed = scaffold.placeBlock(mc, player, newScaffold);
            if (placed) {
                movement.requestJump();
                scaffoldPause = 10;
            }
        } else {
            movement.requestJump();
            scaffoldPause = 6;
        }
    }

    // ── place block ───────────────────────────────────────────────────────
    private void tickPlaceBlock(MinecraftClient mc, ClientPlayerEntity player) {
        if (taskIndex >= buildQueue.size()) {
            state = BuildState.REMOVING_SCAFFOLD;
            return;
        }

        BuildTask task = buildQueue.get(taskIndex);

        if (blockCorrect(mc, task)) {
            task.completed = true;
            taskIndex++;
            state = BuildState.MOVING_TO_POSITION;
            return;
        }

        // Wrong block occupying the spot
        BlockState current = mc.world.getBlockState(task.worldPos);
        if (!current.isAir() && current.getBlock() != task.targetState.getBlock()) {
            breakingPos   = task.worldPos;
            breakingFace  = Direction.UP;
            breakTicks    = 0;
            prevState     = BuildState.PLACING_BLOCK;
            state         = BuildState.BREAKING_WRONG_BLOCK;
            return;
        }

        // Find support face
        Direction face = BlockUtils.findPlaceFace(mc.world, task.worldPos);
        if (face == null) {
            // No support — need scaffold
            state = BuildState.BUILDING_SCAFFOLD;
            return;
        }

        // Equip, look, place
        inventory.equip(mc, player, task.targetState.getBlock());
        BlockPos support = task.worldPos.offset(face.getOpposite());
        BlockUtils.lookAt(player, Vec3d.ofCenter(support));
        BlockUtils.placeBlock(mc, player, task.worldPos, face);

        taskIndex++;
        stuck.reset();
        state = BuildState.MOVING_TO_POSITION;
    }

    // ── break wrong block ─────────────────────────────────────────────────
    private void tickBreakBlock(MinecraftClient mc, ClientPlayerEntity player) {
        if (breakingPos == null) { state = prevState; return; }

        BlockState current = mc.world.getBlockState(breakingPos);
        if (current.isAir()) {
            breakingPos = null;
            breakTicks  = 0;
            state       = prevState;
            return;
        }

        inventory.equipBestTool(mc, player, current);
        BlockUtils.lookAt(player, Vec3d.ofCenter(breakingPos));

        // 1.21.1: attackBlock each tick continues the break
        mc.interactionManager.attackBlock(breakingPos, breakingFace);
        player.swingHand(Hand.MAIN_HAND);
        breakTicks++;

        if (breakTicks > 400) {
            AutoBuilderMod.LOG.warn("Break timeout at {} — skipping", breakingPos);
            breakingPos = null;
            taskIndex++;
            state = BuildState.MOVING_TO_POSITION;
        }
    }

    // ── scaffold teardown ─────────────────────────────────────────────────
    private void tickRemoveScaffold(MinecraftClient mc, ClientPlayerEntity player) {
        if (scaffold.count() == 0) {
            state = BuildState.DONE;
            movement.stop();
            chat(mc, "§aAutoBuilder complete! §8(" + taskIndex + " blocks placed)");
            return;
        }

        BlockPos next = scaffold.nextToRemove();
        if (next == null) { state = BuildState.DONE; return; }

        Vec3d target = Vec3d.ofCenter(next).add(0, 1, 0);
        double dist  = player.getPos().distanceTo(target);

        if (dist < 4.0) {
            movement.stop();
            BlockState bs = mc.world.getBlockState(next);
            if (bs.isAir()) {
                scaffold.markRemoved(next);
            } else {
                inventory.equipBestTool(mc, player, bs);
                BlockUtils.lookAt(player, Vec3d.ofCenter(next));
                mc.interactionManager.attackBlock(next, Direction.UP);
                player.swingHand(Hand.MAIN_HAND);
            }
        } else {
            movement.toward(player, target);
        }
    }

    // ── go to chest ───────────────────────────────────────────────────────
    private void tickGoingToChest(MinecraftClient mc, ClientPlayerEntity player) {
        if (chestPos == null) { state = prevState; return; }

        Vec3d chestVec = Vec3d.ofCenter(chestPos);
        double dist    = player.getPos().distanceTo(chestVec);

        if (dist < 2.5) {
            movement.stop();
            chest.open(mc, player, chestPos);
            state = BuildState.FETCHING_FROM_CHEST;
        } else {
            movement.toward(player, chestVec);

            // Void bridge to chest too
            Direction dir = BlockUtils.horizontalToward(player.getPos(), chestVec);
            if (dir != null && BlockUtils.isVoidAhead(mc.world, player.getBlockPos(), dir)) {
                BlockPos bridge = player.getBlockPos().offset(dir).down();
                scaffold.placeBlock(mc, player, bridge);
            }
        }
    }

    // ── fetch from chest ──────────────────────────────────────────────────
    private void tickFetchingFromChest(MinecraftClient mc, ClientPlayerEntity player) {
        // Wait for chest screen
        if (mc.currentScreen == null) {
            // Screen didn't open — try again next tick
            if (globalTick % 20 == 0) chest.open(mc, player, chestPos);
            return;
        }

        BuildTask task = buildQueue.get(Math.min(taskIndex, buildQueue.size() - 1));
        boolean got    = chest.fetch(mc, player, task.targetState.getBlock());

        chest.close(mc);
        if (!got) {
            AutoBuilderMod.LOG.warn("Item {} not in chest — skipping block",
                task.targetState.getBlock());
            taskIndex++;
        }
        state = BuildState.MOVING_TO_POSITION;
    }

    // ── unstuck ───────────────────────────────────────────────────────────
    private void tickUnstuck(MinecraftClient mc, ClientPlayerEntity player) {
        escapeCount++;
        movement.requestJump();
        movement.forceForward();

        // Dig ahead at head height and feet height
        Vec3d look  = player.getRotationVec(1.0f);
        int   signX = (int) Math.signum(look.x);
        int   signZ = (int) Math.signum(look.z);
        // If look is diagonal, pick dominant axis
        if (Math.abs(look.x) >= Math.abs(look.z)) signZ = 0; else signX = 0;

        BlockPos head = player.getBlockPos().add(signX, 1, signZ);
        BlockPos feet = player.getBlockPos().add(signX, 0, signZ);

        for (BlockPos bp : new BlockPos[]{head, feet}) {
            BlockState bs = mc.world.getBlockState(bp);
            if (!bs.isAir() && !bs.isLiquid()) {
                inventory.equipBestTool(mc, player, bs);
                mc.interactionManager.attackBlock(bp, Direction.NORTH);
            }
        }

        // Escape for 60 ticks then resume
        if (globalTick % 60 == 0) {
            state = (prevState != BuildState.UNSTUCK)
                ? prevState : BuildState.MOVING_TO_POSITION;
            movement.stop();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════════

    private boolean blockCorrect(MinecraftClient mc, BuildTask t) {
        return mc.world.getBlockState(t.worldPos).getBlock()
               == t.targetState.getBlock();
    }

    private boolean hasItem(MinecraftClient mc, ClientPlayerEntity p, BuildTask t) {
        return creativeMode || inventory.has(p, t.targetState.getBlock());
    }

    /** Best adjacent stand position to reach target. */
    private Vec3d standPos(MinecraftClient mc, BlockPos target) {
        int[][] offs = {{1,0},{-1,0},{0,1},{0,-1},{2,0},{-2,0},{0,2},{0,-2},{3,0},{0,3}};
        for (int[] o : offs) {
            BlockPos floor = target.add(o[0], -1, o[1]);
            BlockPos b0    = floor.up();
            BlockPos b1    = floor.up(2);
            if (!mc.world.getBlockState(floor).isAir()
                && mc.world.getBlockState(b0).isAir()
                && mc.world.getBlockState(b1).isAir()) {
                return Vec3d.ofCenter(b0);
            }
        }
        return Vec3d.ofCenter(target.add(1, 0,
                                         }
