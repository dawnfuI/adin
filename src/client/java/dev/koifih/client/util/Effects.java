package dev.koifih.client.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Effects {
    public static boolean splash(ItemStack stack, Holder<MobEffect> effect) {
        return stack.is(Items.SPLASH_POTION) && contains(stack, effect);
    }

    public static boolean contains(ItemStack stack, Holder<MobEffect> effect) {
        for (MobEffectInstance instance : stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getAllEffects()) {
            if (instance.is(effect)) return true;
        }
        return false;
    }
}
