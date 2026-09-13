package org.agmas.noellesroles.role.vtuber;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.cca.SREPlayerNoteComponent;
import io.wifi.starrailexpress.content.entity.NoteEntity;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.roles.vtuber.VtuberRoleRuntime;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;

public class YuyueRole extends NormalRole {
    public YuyueRole(ResourceLocation id, int color, boolean innocent, boolean killer,
            MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    @Override
    public boolean onUseGun(Player player) {
        return !VtuberRoleRuntime.isWeaponBlocked(player);
    }

    @Override
    public boolean onUseKnife(Player player) {
        return !VtuberRoleRuntime.isWeaponBlocked(player);
    }

    @Override
    public List<ShopEntry> getShopEntries() {

        var YUYUE_SHOP = new ArrayList<ShopEntry>();
        YUYUE_SHOP.add(new ShopEntry(ModItems.YUYUE_NOTE.getDefaultInstance(), 25, ShopEntry.Type.TOOL));
        return YUYUE_SHOP;
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;
        player.removeEffect(MobEffects.BLINDNESS);
    }

    public static InteractionResult attachNote(ItemStack stack, Player player, LivingEntity target,
            java.util.function.Function<Level, NoteEntity> factory) {
        if (!player.isAlive() || player.isSpectator() || !target.isAlive() || target.level() != player.level()) {
            return InteractionResult.FAIL;
        }
        SREPlayerNoteComponent component = SREPlayerNoteComponent.KEY.get(player);
        if (!component.written) {
            player.displayClientMessage(Component.translatable("message.note.write_sth")
                    .withColor(Mth.hsvToRgb(0.0F, 1.0F, 0.6F)), true);
            return InteractionResult.PASS;
        }
        Level level = player.level();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        var note = factory.apply(level);
        if (note == null) {
            return InteractionResult.PASS;
        }
        note.setAttached(ModRoles.ENTITY_NOTE_MAKER, target.getUUID().toString());
        note.setYRot(target.getYHeadRot());
        note.setPos(target.getX(), target.getY() + 1.0D, target.getZ());
        note.setDirection(Direction.EAST);
        note.setLines(component.text);
        if (!level.addFreshEntity(note)) return InteractionResult.FAIL;
        player.displayClientMessage(Component.translatable("message.note.put_back", target.getName())
                .withColor(Mth.hsvToRgb(0.0F, 1.0F, 0.6F)), true);
        if (!player.isCreative()) {
            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(stack.getItem()));
            }
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
