/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.mixin;

import net.minecraft.CrashReportCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Objects;

@Mixin(CrashReportCategory.class)
public abstract class CrashReportCategoryMixin {

    @Redirect(
            method = "validateStackTrace",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z",
                    ordinal = 1
            )
    )
    private boolean starRailExpress$compareNullableFileNames(String fileName, Object otherFileName) {
        return starRailExpress$fileNamesEqual(fileName, otherFileName);
    }

    @Unique
    private static boolean starRailExpress$fileNamesEqual(String fileName, Object otherFileName) {
        return Objects.equals(fileName, otherFileName);
    }
}
