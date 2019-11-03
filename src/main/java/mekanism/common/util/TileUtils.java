package mekanism.common.util;

import io.netty.buffer.ByteBuf;
import java.util.EnumSet;
import mekanism.api.TileNetworkList;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTank;
import mekanism.common.handler.PacketHandler;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fml.common.network.ByteBufUtils;

//TODO: Move this and factor out the parts into proper classes. This is mainly just temp to make organization not as needed
public class TileUtils {

    // N.B. All the tank I/O functions rely on the fact that an empty NBT Compound is a singular
    // byte and that the Gas/Fluid Stacks initialize to null if they are de-serialized from an
    // empty tag.
    private static final NBTTagCompound EMPTY_TAG_COMPOUND = new NBTTagCompound();

    /** ByteBuf **/

    public static void addTankData(ByteBuf buf, GasTank tank) {
        if (tank.getGas() != null) {
            ByteBufUtils.writeTag(buf, tank.getGas().write(new NBTTagCompound()));
        } else {
            ByteBufUtils.writeTag(buf, EMPTY_TAG_COMPOUND);
        }
    }

    public static void addTankData(ByteBuf buf, FluidTank tank) {
        addFluidStack(buf, tank.getFluid());
    }

    public static void addFluidStack(ByteBuf buf, FluidStack stack) {
        if (stack != null) {
            ByteBufUtils.writeTag(buf, stack.writeToNBT(new NBTTagCompound()));
        } else {
            ByteBufUtils.writeTag(buf, EMPTY_TAG_COMPOUND);
        }
    }

    public static void readTankData(ByteBuf dataStream, GasTank tank) {
        tank.setGas(GasStack.readFromNBT(PacketHandler.readNBT(dataStream)));
    }

    public static void readTankData(ByteBuf dataStream, FluidTank tank) {
        tank.setFluid(readFluidStack(dataStream));
    }

    public static FluidStack readFluidStack(ByteBuf dataStream) {
        return FluidStack.loadFluidStackFromNBT(PacketHandler.readNBT(dataStream));
    }

    /** NBT Tag **/

    public static void addTankData(String id, NBTTagCompound tag, GasTank tank) {
        NBTTagCompound tag1 = new NBTTagCompound();
        tank.getGas().write(tag1);
        tag.setTag(id, tag1);
    }

    public static void addTankData(String id, NBTTagCompound tag, FluidTank tank) {
        NBTTagCompound tag1 = new NBTTagCompound();
        tank.writeToNBT(tag1);
        tag.setTag(id, tag1);
    }

    public static void readTankData(String id, NBTTagCompound tag, GasTank tank) {
        tank.setGas(GasStack.readFromNBT(tag.getCompoundTag(id)));
    }

    public static void readTankData(String id, NBTTagCompound tag, FluidTank tank) {
        tank.setFluid(FluidStack.loadFluidStackFromNBT(tag.getCompoundTag(id)));
    }



    //Returns true if it entered the if statement, basically for use by TileEntityGasTank
    public static boolean receiveGas(ItemStack stack, GasTank tank) {
        if (!stack.isEmpty() && (tank.getGas() == null || tank.getStored() < tank.getMaxGas())) {
            tank.receive(GasUtils.removeGas(stack, tank.getGasType(), tank.getNeeded()), true);
            return true;
        }
        return false;
    }

    public static void drawGas(ItemStack stack, GasTank tank) {
        drawGas(stack, tank, true);
    }

    public static void drawGas(ItemStack stack, GasTank tank, boolean doDraw) {
        if (!stack.isEmpty() && tank.getGas() != null) {
            tank.draw(GasUtils.addGas(stack, tank.getGas()), doDraw);
        }
    }

    public static void emitGas(TileEntityBasicBlock tile, GasTank tank, int gasOutput, EnumFacing facing) {
        if (tank.getGas() != null) {
            GasStack toSend = new GasStack(tank.getGas().getGas(), Math.min(tank.getStored(), gasOutput));
            tank.draw(GasUtils.emit(toSend, tile, EnumSet.of(facing)), true);
        }
    }
}