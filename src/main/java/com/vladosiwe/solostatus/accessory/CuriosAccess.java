package com.vladosiwe.solostatus.accessory;

import com.vladosiwe.solostatus.SoloStatus;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Jewellery worn in Curios slots, read without a compile dependency:
 * CuriosApi.getCuriosInventory(LivingEntity) → Optional&lt;ICuriosItemHandler&gt;, then getEquippedCurios().
 * If Curios is absent or its API differs, this returns nothing and the System slots still work.
 */
public final class CuriosAccess {
    private static boolean resolved;
    private static MethodHandle getInventory, getEquipped, setEquipped;
    private static String error = "not resolved";

    private CuriosAccess() {}

    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;
        if (!ModList.get().isLoaded("curios")) {
            error = "Curios is not installed";
            return;
        }
        try {
            ClassLoader cl = CuriosAccess.class.getClassLoader();
            MethodHandles.Lookup l = MethodHandles.publicLookup();
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi", false, cl);
            Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler", false, cl);
            getInventory = l.findStatic(api, "getCuriosInventory", MethodType.methodType(Optional.class, LivingEntity.class));
            getEquipped = l.findVirtual(handler, "getEquippedCurios", MethodType.methodType(IItemHandlerModifiable.class));
            try {
                setEquipped = l.findVirtual(handler, "setEquippedCurio", MethodType.methodType(void.class, String.class, int.class, ItemStack.class));
            } catch (Exception ignored) {
                // only used by the game test
            }
            error = null;
            SoloStatus.LOGGER.info("Curios: jewellery in ring/necklace slots counts for the System");
        } catch (Throwable t) {
            error = t.toString();
            SoloStatus.LOGGER.warn("Curios: cannot read equipped jewellery ({}); the System slots still work", error);
        }
    }

    public static boolean ready() {
        resolve();
        return error == null;
    }

    public static String error() {
        resolve();
        return error;
    }

    /** Every non-empty stack in the entity's Curios slots. */
    public static List<ItemStack> equipped(LivingEntity entity) {
        if (!ready()) return List.of();
        try {
            Optional<?> inv = (Optional<?>) getInventory.invoke(entity);
            if (inv.isEmpty()) return List.of();
            IItemHandlerModifiable items = (IItemHandlerModifiable) getEquipped.invoke(inv.get());
            List<ItemStack> out = new ArrayList<>();
            for (int i = 0; i < items.getSlots(); i++) {
                ItemStack s = items.getStackInSlot(i);
                if (!s.isEmpty()) out.add(s);
            }
            return out;
        } catch (Throwable t) {
            return List.of();
        }
    }

    /** Test helper: put a stack into a Curios slot ("ring", "necklace"). */
    public static boolean put(LivingEntity entity, String slot, int index, ItemStack stack) {
        if (!ready() || setEquipped == null) return false;
        try {
            Optional<?> inv = (Optional<?>) getInventory.invoke(entity);
            if (inv.isEmpty()) return false;
            setEquipped.invoke(inv.get(), slot, index, stack);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
