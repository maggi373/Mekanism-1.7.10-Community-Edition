package mekanism.common.misc;

import javax.annotation.Nonnull;

import mekanism.common.registry.MekanismItems;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;

public class CreativeTabMekanism extends CreativeTabs {

    public CreativeTabMekanism() {
        super("tabMekanism");
    }

    @Nonnull
    @Override
    public ItemStack createIcon() {
        return new ItemStack(MekanismItems.AtomicAlloy);
    }
}