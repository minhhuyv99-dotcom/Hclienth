package dev.vanta.autobuilder.schematic;

import dev.vanta.autobuilder.AutoBuilderMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads vanilla Minecraft .nbt structure files (saved with /structure save).
 * Compatible with 1.21.1 — uses NbtIo Path API.
 */
@SuppressWarnings({"unchecked","rawtypes"})
public final class SchematicReader {

    public static SchematicData read(File file) throws IOException {
        NbtCompound root;
        try {
            root = NbtIo.readCompressed(
                file.toPath(), NbtTagSizeTracker.ofUnlimited());
        } catch (Exception e) {
            // Fallback: uncompressed
            NbtCompound fallback = NbtIo.read(file.toPath());
            if (fallback == null)
                throw new IOException("Could not read NBT from: " + file.getPath());
            root = fallback;
        }

        // Size
        NbtList sizeList = root.getList("size", NbtElement.INT_TYPE);
        int sizeX = sizeList.getInt(0);
        int sizeY = sizeList.getInt(1);
        int sizeZ = sizeList.getInt(2);

        // Palette
        NbtList paletteNbt = root.getList("palette", NbtElement.COMPOUND_TYPE);
        List<BlockState> palette = new ArrayList<>(paletteNbt.size());
        for (int i = 0; i < paletteNbt.size(); i++) {
            NbtCompound entry = paletteNbt.getCompound(i);

            // 1.21: Identifier.of() preferred over new Identifier()
            Identifier id = Identifier.of(entry.getString("Name"));
            Block block   = Registries.BLOCK.getOrEmpty(id).orElse(Blocks.AIR);
            BlockState state = block.getDefaultState();

            if (entry.contains("Properties", NbtElement.COMPOUND_TYPE)) {
                NbtCompound props = entry.getCompound("Properties");
                StateManager<Block, BlockState> mgr = block.getStateManager();
                for (String key : props.getKeys()) {
                    Property prop = mgr.getProperty(key);
                    if (prop == null) continue;
                    Optional<Comparable> val = prop.parse(props.getString(key));
                    if (val.isPresent()) //noinspection unchecked
                        state = state.with(prop, val.get());
                }
            }
            palette.add(state);
        }

        // Blocks — skip air and all fluids (water / lava)
        NbtList blocksNbt = root.getList("blocks", NbtElement.COMPOUND_TYPE);
        List<SchematicData.PlacedBlock> blocks = new ArrayList<>(blocksNbt.size());
        for (int i = 0; i < blocksNbt.size(); i++) {
            NbtCompound entry = blocksNbt.getCompound(i);
            int idx            = entry.getInt("state");
            BlockState state   = idx < palette.size()
                ? palette.get(idx) : Blocks.AIR.getDefaultState();

            if (state.isAir())                     continue;
            if (!state.getFluidState().isEmpty())   continue;   // skip water/lava

            NbtList pos = entry.getList("pos", NbtElement.INT_TYPE);
            blocks.add(new SchematicData.PlacedBlock(
                new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), state));
        }

        AutoBuilderMod.LOG.info("Schematic: {}×{}×{} → {} solid blocks",
            sizeX, sizeY, sizeZ, blocks.size());
        return new SchematicData(sizeX, sizeY, sizeZ, blocks);
    }
              }
