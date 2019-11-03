package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import javax.annotation.Nonnull;
import mekanism.api.TileNetworkList;
import mekanism.api.energy.IStrictEnergyStorage;
import mekanism.common.base.ByteBufType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.tier.InductionCellTier;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;

public class TileEntityInductionCell extends TileEntityBasicBlock implements IStrictEnergyStorage {

    public InductionCellTier tier = InductionCellTier.BASIC;

    public double electricityStored;

    @Override
    public void onUpdate() {
    }

    public String getName() {
        return LangUtils.localize(getBlockType().getTranslationKey() + ".InductionCell" + tier.getBaseTier().getSimpleName() + ".name");
    }

    @Override
    public void readPacket(ByteBuf buf, ByteBufType type) {
        if(type == ByteBufType.SERVER_TO_CLIENT) {
            InductionCellTier prevTier = tier;
            tier = InductionCellTier.values()[buf.readInt()];
            super.readPacket(buf, type);
            electricityStored = buf.readDouble();
            if (prevTier != tier) {
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    @Override
    public void writePacket(ByteBuf buf, ByteBufType type, Object... obj) {
        if(type == ByteBufType.SERVER_TO_CLIENT) {
            buf.writeInt(tier.ordinal());
        }
        super.writePacket(buf, type, obj);
        if(type == ByteBufType.SERVER_TO_CLIENT) {
            buf.writeDouble(electricityStored);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbtTags) {
        super.readFromNBT(nbtTags);
        tier = InductionCellTier.values()[nbtTags.getInteger("tier")];
        electricityStored = nbtTags.getDouble("electricityStored");
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbtTags) {
        super.writeToNBT(nbtTags);
        nbtTags.setInteger("tier", tier.ordinal());
        nbtTags.setDouble("electricityStored", electricityStored);
        return nbtTags;
    }

    @Override
    public double getEnergy() {
        return electricityStored;
    }

    @Override
    public void setEnergy(double energy) {
        electricityStored = Math.min(energy, getMaxEnergy());
    }

    @Override
    public double getMaxEnergy() {
        return tier.getMaxEnergy();
    }

    @Override
    public boolean hasCapability(@Nonnull net.minecraftforge.common.capabilities.Capability<?> capability, EnumFacing facing) {
        return capability == Capabilities.ENERGY_STORAGE_CAPABILITY || super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing facing) {
        if (capability == Capabilities.ENERGY_STORAGE_CAPABILITY) {
            return Capabilities.ENERGY_STORAGE_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, facing);
    }
}
