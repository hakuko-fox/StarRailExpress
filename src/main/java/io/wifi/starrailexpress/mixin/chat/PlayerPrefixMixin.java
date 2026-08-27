/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.mixin.chat;

import net.exmo.sre.nametag.NameTagInventoryComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Player.class, priority = 1100)
public class PlayerPrefixMixin {
    @Unique
    private static MutableComponent somePrefix(Player mainPlayer) {
        var component = NameTagInventoryComponent.KEY.get(mainPlayer).generate();
        return component != null
                ? Component.literal("[").append(component.copy()).append("] ")
                : null;
    }

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    public void getDisplayName(CallbackInfoReturnable<Component> cir) {
        Player mainPlayer = (Player) (Object) this;
        if (!mainPlayer.level().isClientSide()) {
            MutableComponent prefix = somePrefix(mainPlayer);
            if (prefix != null) {
                cir.setReturnValue(prefix.append(cir.getReturnValue()));
            }
        }
    }
}
