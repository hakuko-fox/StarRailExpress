package org.agmas.noellesroles.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-only visual blocks for Cake Maker. They are never added to collision tracking. */
@Environment(EnvType.CLIENT)
public final class ClientCakeMakerBlocks {
    private static final Map<UUID, Entry> BLOCKS = new HashMap<>();
    private ClientCakeMakerBlocks() { }
    public static void put(UUID id, BlockPos pos, boolean cake, int bites, int ticks) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.getBlockState(pos).isAir()) return;
        BlockState state = cake ? Blocks.CAKE.defaultBlockState().setValue(net.minecraft.world.level.block.CakeBlock.BITES, bites) : Blocks.SMOKER.defaultBlockState();
        level.setBlock(pos, state, 3);
        BLOCKS.put(id, new Entry(pos, level.getBlockState(pos), ticks));
    }
    public static void remove(UUID id) {
        Entry entry = BLOCKS.remove(id);
        ClientLevel level = Minecraft.getInstance().level;
        if (entry != null && level != null) level.setBlock(entry.pos, Blocks.AIR.defaultBlockState(), 3);
    }
    public static boolean isCake(BlockPos pos) { return BLOCKS.values().stream().anyMatch(e -> e.pos.equals(pos) && e.state.is(Blocks.CAKE)); }
    public static UUID cakeId(BlockPos pos) { return BLOCKS.entrySet().stream().filter(e -> e.getValue().pos.equals(pos) && e.getValue().state.is(Blocks.CAKE)).map(Map.Entry::getKey).findFirst().orElse(null); }
    public static void tick() {
        for (var it = BLOCKS.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            if (--entry.getValue().ticks <= 0) { ClientLevel l = Minecraft.getInstance().level; if (l != null) l.setBlock(entry.getValue().pos, Blocks.AIR.defaultBlockState(), 3); it.remove(); }
        }
    }
    private static final class Entry { final BlockPos pos; final BlockState state; int ticks; Entry(BlockPos p, BlockState s, int t) { pos=p; state=s; ticks=t; } }
}
