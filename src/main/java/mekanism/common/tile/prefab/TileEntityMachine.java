package mekanism.common.tile.prefab;

import java.util.Objects;
import javax.annotation.Nonnull;

import mekanism.common.misc.Upgrade;
import mekanism.common.base.IRedstoneControl;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.base.NBTType;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.util.MekanismUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

public abstract class TileEntityMachine extends TileEntityEffectsBlock implements IUpgradeTile, IRedstoneControl, ISecurityTile {

    public double prevEnergy;

    public double BASE_ENERGY_PER_TICK;

    public double energyPerTick;

    /**
     * This machine's current RedstoneControl type.
     */
    private RedstoneControl controlType = RedstoneControl.DISABLED;

    public TileComponentUpgrade upgradeComponent;
    public TileComponentSecurity securityComponent = new TileComponentSecurity(this);

    public TileEntityMachine(String sound, MachineType type, int upgradeSlot) {
        super(sound, type.getBlockName(), type.getStorage());
        energyPerTick = BASE_ENERGY_PER_TICK = type.getUsage();

        upgradeComponent = new TileComponentUpgrade(this, upgradeSlot);
        upgradeComponent.setSupported(Upgrade.MUFFLING);
    }

    @Override
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return facing != EnumFacing.DOWN && facing != EnumFacing.UP;
    }

    @Override
    public boolean renderUpdate() {
        return true;
    }

    @Override
    public boolean lightUpdate() {
        return true;
    }

    @Override
    public NBTTagCompound writeNetworkNBT(NBTTagCompound tag, NBTType type) {
        super.writeNetworkNBT(tag, type);
        if(type.isAllSave() || type.isTileUpdate()) {
            tag.setInteger("controlType", controlType.ordinal());
        }
        if(type.isTileUpdate()) {
            tag.setDouble("energyPerTick", energyPerTick);
            tag.setDouble("maxEnergy", maxEnergy);
        }
        return tag;
    }

    @Override
    public void readNetworkNBT(NBTTagCompound tag, NBTType type) {
        super.readNetworkNBT(tag, type);
        if(type.isAllSave() || type.isTileUpdate()) {
            controlType = RedstoneControl.values()[tag.getInteger("controlType")];
        }
        if(type.isTileUpdate()) {
            energyPerTick = tag.getDouble("energyPerTick");
            maxEnergy = tag.getDouble("maxEnergy");
        }
    }

    @Override
    public RedstoneControl getControlType() {
        return controlType;
    }

    @Override
    public void setControlType(@Nonnull RedstoneControl type) {
        controlType = Objects.requireNonNull(type);
        MekanismUtils.saveChunk(this);
    }

    @Override
    public boolean canPulse() {
        return false;
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    @Override
    public TileComponentUpgrade getComponent() {
        return upgradeComponent;
    }

    @Override
    public void recalculateUpgradables(Upgrade upgrade) {
        super.recalculateUpgradables(upgrade);
        if (upgrade == Upgrade.ENERGY) {
            maxEnergy = MekanismUtils.getMaxEnergy(this, BASE_MAX_ENERGY);
            energyPerTick = MekanismUtils.getBaseEnergyPerTick(this, BASE_ENERGY_PER_TICK);
            setEnergy(Math.min(getMaxEnergy(), getEnergy()));
        }
    }
}