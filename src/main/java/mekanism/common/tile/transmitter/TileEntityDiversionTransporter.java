package mekanism.common.tile.transmitter;

import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.api.TileNetworkList;
import mekanism.common.Mekanism;
import mekanism.common.base.ByteBufType;
import mekanism.common.block.states.BlockStateTransmitter.TransmitterType;
import mekanism.common.content.transporter.TransporterStack;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextComponentString;

public class TileEntityDiversionTransporter extends TileEntityLogisticalTransporter {

    public int[] modes = {0, 0, 0, 0, 0, 0};

    @Override
    public TransmitterType getTransmitterType() {
        return TransmitterType.DIVERSION_TRANSPORTER;
    }

    @Override
    public boolean renderCenter() {
        return true;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbtTags) {
        super.readFromNBT(nbtTags);
        if (nbtTags.hasKey("modes")) {
            modes = nbtTags.getIntArray("modes");
        }
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbtTags) {
        super.writeToNBT(nbtTags);
        nbtTags.setIntArray("modes", modes);
        return nbtTags;
    }

    @Override
    public void readPacket(ByteBuf buf, ByteBufType type) {
        super.readPacket(buf, type);
        if(type == ByteBufType.SERVER_TO_CLIENT) {
            modes[0] = buf.readInt();
            modes[1] = buf.readInt();
            modes[2] = buf.readInt();
            modes[3] = buf.readInt();
            modes[4] = buf.readInt();
            modes[5] = buf.readInt();
        }
    }

    @Override
    public void writePacket(ByteBuf buf, ByteBufType type) {
        super.writePacket(buf, type);
        buf.writeInt(modes[0]);
        buf.writeInt(modes[1]);
        buf.writeInt(modes[2]);
        buf.writeInt(modes[3]);
        buf.writeInt(modes[4]);
        buf.writeInt(modes[5]);
    }

    @Override
    public NBTTagCompound makeSyncPacketC(int stackId, TransporterStack stack) {
        return addModes(super.makeSyncPacketC(stackId, stack));
    }

    @Override
    public NBTTagCompound makeBatchPacketC(Map<Integer, TransporterStack> updates, Set<Integer> deletes) {
        return addModes(super.makeBatchPacketC(updates, deletes));
    }

    private NBTTagCompound addModes(NBTTagCompound tag) {
        tag.setIntArray("modes", modes);
        return tag;
    }

    @Override
    protected EnumActionResult onConfigure(EntityPlayer player, int part, EnumFacing side) {
        int newMode = (modes[side.ordinal()] + 1) % 3;
        String description = "ERROR";
        modes[side.ordinal()] = newMode;
        switch (newMode) {
            case 0:
                description = LangUtils.localize("control.disabled.desc");
                break;
            case 1:
                description = LangUtils.localize("control.high.desc");
                break;
            case 2:
                description = LangUtils.localize("control.low.desc");
                break;
        }
        refreshConnections();
        notifyTileChange();
        player.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + EnumColor.GREY + " " +
                                                   LangUtils.localize("tooltip.configurator.toggleDiverter") + ": " + EnumColor.RED + description));
        Mekanism.packetHandler.sendUpdatePacket(this);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public boolean canConnect(EnumFacing side) {
        if (!super.canConnect(side)) {
            return false;
        }
        int mode = modes[side.ordinal()];
        boolean redstone = MekanismUtils.isGettingPowered(getWorld(), new Coord4D(getPos(), getWorld()));
        return (mode != 2 || !redstone) && (mode != 1 || redstone);
    }

    @Override
    public EnumColor getRenderColor() {
        return null;
    }
}