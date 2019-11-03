package mekanism.generators.common.tile.turbine;

import io.netty.buffer.ByteBuf;
import javax.annotation.Nonnull;
import mekanism.api.Coord4D;
import mekanism.api.TileNetworkList;
import mekanism.api.energy.IStrictEnergyStorage;
import mekanism.common.Mekanism;
import mekanism.common.base.ByteBufType;
import mekanism.common.config.MekanismConfig;
import mekanism.common.multiblock.MultiblockCache;
import mekanism.common.multiblock.MultiblockManager;
import mekanism.common.multiblock.UpdateProtocol;
import mekanism.common.tile.TileEntityGasTank.GasMode;
import mekanism.common.tile.TileEntityMultiblock;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import mekanism.generators.common.MekanismGenerators;
import mekanism.generators.common.content.turbine.SynchronizedTurbineData;
import mekanism.generators.common.content.turbine.TurbineCache;
import mekanism.generators.common.content.turbine.TurbineUpdateProtocol;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.CapabilityItemHandler;

public class TileEntityTurbineCasing extends TileEntityMultiblock<SynchronizedTurbineData> implements IStrictEnergyStorage {

    public TileEntityTurbineCasing() {
        this("TurbineCasing");
    }

    public TileEntityTurbineCasing(String name) {
        super(name);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (!world.isRemote) {
            if (structure != null) {
                if (structure.fluidStored != null && structure.fluidStored.amount <= 0) {
                    structure.fluidStored = null;
                    markDirty();
                }

                if (isRendering) {
                    structure.lastSteamInput = structure.newSteamInput;
                    structure.newSteamInput = 0;

                    int stored = structure.fluidStored != null ? structure.fluidStored.amount : 0;
                    double proportion = (double) stored / (double) structure.getFluidCapacity();
                    double flowRate = 0;

                    if (stored > 0 && getEnergy() < structure.getEnergyCapacity()) {
                        double energyMultiplier = (MekanismConfig.current().general.maxEnergyPerSteam.val() / TurbineUpdateProtocol.MAX_BLADES) *
                                                  Math.min(structure.blades, structure.coils * MekanismConfig.current().generators.turbineBladesPerCoil.val());
                        double rate = structure.lowerVolume * (structure.getDispersers() * MekanismConfig.current().generators.turbineDisperserGasFlow.val());
                        rate = Math.min(rate, structure.vents * MekanismConfig.current().generators.turbineVentGasFlow.val());

                        double origRate = rate;
                        rate = Math.min(Math.min(stored, rate), (getMaxEnergy() - getEnergy()) / energyMultiplier) * proportion;

                        flowRate = rate / origRate;
                        setEnergy(getEnergy() + (int) rate * energyMultiplier);

                        structure.fluidStored.amount -= rate;
                        structure.clientFlow = (int) rate;
                        structure.flowRemaining = Math.min((int) rate, structure.condensers * MekanismConfig.current().generators.condenserRate.val());
                        if (structure.fluidStored.amount == 0) {
                            structure.fluidStored = null;
                        }
                    } else {
                        structure.clientFlow = 0;
                    }

                    if (structure.dumpMode == GasMode.DUMPING && structure.fluidStored != null) {
                        structure.fluidStored.amount -= Math.min(structure.fluidStored.amount, Math.max(structure.fluidStored.amount / 50, structure.lastSteamInput * 2));
                        if (structure.fluidStored.amount == 0) {
                            structure.fluidStored = null;
                        }
                    }

                    float newRotation = (float) flowRate;
                    boolean needsRotationUpdate = false;

                    if (Math.abs(newRotation - structure.clientRotation) > SynchronizedTurbineData.ROTATION_THRESHOLD) {
                        structure.clientRotation = newRotation;
                        needsRotationUpdate = true;
                    }

                    if (structure.needsRenderUpdate() || needsRotationUpdate) {
                        sendPacketToRenderer();
                    }
                    structure.prevFluid = structure.fluidStored != null ? structure.fluidStored.copy() : null;
                }
            }
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize("gui.industrialTurbine");
    }

    @Override
    public boolean onActivate(EntityPlayer player, EnumHand hand, ItemStack stack) {
        if (!player.isSneaking() && structure != null) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            player.openGui(MekanismGenerators.instance, 6, world, getPos().getX(), getPos().getY(), getPos().getZ());
            return true;
        }
        return false;
    }

    @Override
    public double getEnergy() {
        return structure != null ? structure.electricityStored : 0;
    }

    @Override
    public void setEnergy(double energy) {
        if (structure != null) {
            structure.electricityStored = Math.max(Math.min(energy, getMaxEnergy()), 0);
            MekanismUtils.saveChunk(this);
        }
    }

    @Override
    public double getMaxEnergy() {
        return structure != null ? structure.getEnergyCapacity() : 0;
    }

    public int getScaledFluidLevel(long i) {
        if (structure == null || structure.getFluidCapacity() == 0 || structure.fluidStored == null) {
            return 0;
        }
        return (int) (structure.fluidStored.amount * i / structure.getFluidCapacity());
    }

    @Override
    public void writePacket(ByteBuf buf, ByteBufType type, Object... obj) {
        if(type == ByteBufType.GUI_TO_SERVER) {
            buf.writeInt((Integer) obj[0]);
            return;
        }
        super.writePacket(buf, type, obj);
        if(type == ByteBufType.SERVER_TO_CLIENT) {
            if (structure != null) {
                buf.writeInt(structure.volume);
                buf.writeInt(structure.lowerVolume);
                buf.writeInt(structure.vents);
                buf.writeInt(structure.blades);
                buf.writeInt(structure.coils);
                buf.writeInt(structure.condensers);
                buf.writeInt(structure.getDispersers());
                buf.writeDouble(structure.electricityStored);
                buf.writeInt(structure.clientFlow);
                buf.writeInt(structure.lastSteamInput);
                buf.writeInt(structure.dumpMode.ordinal());
                TileUtils.addFluidStack(buf, structure.fluidStored);
                if (isRendering) {
                    structure.complex.write(buf);
                    buf.writeFloat(structure.clientRotation);
                }
            }
        }
    }

    @Override
    public void readPacket(ByteBuf buf, ByteBufType type) {
        if(type == ByteBufType.GUI_TO_SERVER) {
            if (structure != null) {
                int type1 = buf.readInt();
                if (type1 == 0) {
                    structure.dumpMode = GasMode.values()[structure.dumpMode.ordinal() == GasMode.values().length - 1 ? 0 : structure.dumpMode.ordinal() + 1];
                }
            }
            return;
        }
        super.readPacket(buf, type);
        if(type == ByteBufType.SERVER_TO_CLIENT) {
            if (clientHasStructure) {
                structure.volume = buf.readInt();
                structure.lowerVolume = buf.readInt();
                structure.vents = buf.readInt();
                structure.blades = buf.readInt();
                structure.coils = buf.readInt();
                structure.condensers = buf.readInt();
                structure.clientDispersers = buf.readInt();
                structure.electricityStored = buf.readDouble();
                structure.clientFlow = buf.readInt();
                structure.lastSteamInput = buf.readInt();
                structure.dumpMode = GasMode.values()[buf.readInt()];

                structure.fluidStored = TileUtils.readFluidStack(buf);

                if (isRendering) {
                    structure.complex = Coord4D.read(buf);
                    structure.clientRotation = buf.readFloat();
                    SynchronizedTurbineData.clientRotationMap.put(structure.inventoryID, structure.clientRotation);
                }
            }
        }
    }

    @Override
    protected SynchronizedTurbineData getNewStructure() {
        return new SynchronizedTurbineData();
    }

    @Override
    public MultiblockCache<SynchronizedTurbineData> getNewCache() {
        return new TurbineCache();
    }

    @Override
    protected UpdateProtocol<SynchronizedTurbineData> getProtocol() {
        return new TurbineUpdateProtocol(this);
    }

    @Override
    public MultiblockManager<SynchronizedTurbineData> getManager() {
        return MekanismGenerators.turbineManager;
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return InventoryUtils.EMPTY;
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true;
        }
        return super.isCapabilityDisabled(capability, side);
    }
}