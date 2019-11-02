package mekanism.common.tile.prefab;

import mekanism.common.misc.Upgrade;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.base.NBTType;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.util.MekanismUtils;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;

public abstract class TileEntityOperationalMachine extends TileEntityMachine implements IComparatorSupport {

    public int operatingTicks;

    public int BASE_TICKS_REQUIRED;

    public int ticksRequired;

    protected TileEntityOperationalMachine(String sound, MachineType type, int upgradeSlot, int baseTicksRequired) {
        super(sound, type, upgradeSlot);
        ticksRequired = BASE_TICKS_REQUIRED = baseTicksRequired;
    }

    public double getScaledProgress() {
        return (double) operatingTicks / (double) ticksRequired;
    }

    @Override
    public NBTTagCompound writeNetworkNBT(NBTTagCompound tag, NBTType type) {
        super.writeNetworkNBT(tag, type);
        if(type.isAllSave() || type.isTileUpdate()) {
            tag.setInteger("operatingTicks", operatingTicks);
        }
        if(type.isTileUpdate()) {
            tag.setInteger("ticksRequired", ticksRequired);
        }
        return tag;
    }

    @Override
    public void readNetworkNBT(NBTTagCompound tag, NBTType type) {
        super.readNetworkNBT(tag, type);
        if(type.isAllSave() || type.isTileUpdate()) {
            operatingTicks = tag.getInteger("operatingTicks");
        }
        if(type.isTileUpdate()) {
            ticksRequired = tag.getInteger("ticksRequired");
        }
    }

    @Override
    public void recalculateUpgradables(Upgrade upgrade) {
        super.recalculateUpgradables(upgrade);
        switch (upgrade) {
            case ENERGY:
                energyPerTick = MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK); // incorporate speed upgrades
                break;
            case SPEED:
                ticksRequired = MekanismUtils.getTicks(this, BASE_TICKS_REQUIRED);
                energyPerTick = MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK);
                break;
            default:
                break;
        }
    }

    @Override
    public int getRedstoneLevel() {
        return Container.calcRedstoneFromInventory(this);
    }
}