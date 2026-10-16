package com.createbrewery.compat;

import com.createbrewery.ModItems;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

/** Curios (only touched when it is installed): earplugs are worn in its head slot. Without it they cannot be worn. */
public final class CuriosCompat {
    private CuriosCompat() {}

    private static final boolean ON = ModList.get().isLoaded("curios");

    /** True while {@code entity} wears earplugs. Both sides: Curios syncs the slots to the client. */
    public static boolean earplugs(LivingEntity entity) {
        return ON && Api.equipped(entity);
    }

    /** Kept apart so nothing of Curios is loaded without it. */
    private static final class Api {
        static boolean equipped(LivingEntity entity) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(entity)
                .map(inv -> inv.isEquipped(ModItems.EARPLUGS.get()))
                .orElse(false);
        }
    }
}
