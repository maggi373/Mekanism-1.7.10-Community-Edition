package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import javax.annotation.Nonnull;
import mekanism.api.Coord4D;
import mekanism.common.Mekanism;
import mekanism.common.base.ByteBufType;
import mekanism.common.base.ITileByteBuf;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.network.PacketByteBuf;
import mekanism.common.network.PacketDataRequest.DataRequestMessage;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;

/**
 * Multi-block used by wind turbines, solar panels, and other machines
 */
public class TileEntityBoundingBlock extends TileEntity implements ITileByteBuf {

    private BlockPos mainPos = BlockPos.ORIGIN;

    public boolean receivedCoords;

    public int prevPower;

    public void setMainLocation(BlockPos pos) {
        receivedCoords = pos != null;
        if (!world.isRemote) {
            mainPos = pos;
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
    }

    public BlockPos getMainPos() {
        if (mainPos == null) {
            mainPos = BlockPos.ORIGIN;
        }
        return mainPos;
    }

    @Override
    public void validate() {
        super.validate();
        if (world.isRemote) {
            Mekanism.packetHandler.sendToServer(new DataRequestMessage(Coord4D.get(this)));
        }
    }

    public TileEntity getMainTile() {
        if (receivedCoords && world.isBlockLoaded(getMainPos())) {
            return world.getTileEntity(getMainPos());
        }
        return null;
    }

    public void onNeighborChange() {
        final TileEntity tile = getMainTile();
        if (tile instanceof TileEntityBasicBlock) {
            int power = world.getRedstonePowerFromNeighbors(getPos());
            if (prevPower != power) {
                if (power > 0) {
                    onPower();
                } else {
                    onNoPower();
                }
                prevPower = power;
                Mekanism.packetHandler.sendToAllTracking(new PacketByteBuf.ByteBufMessage(this, ByteBufType.SERVER_TO_CLIENT), this);
            }
        }
    }

    public void onPower() {
    }

    public void onNoPower() {
    }

    @Override
    public void readFromNBT(NBTTagCompound nbtTags) {
        super.readFromNBT(nbtTags);
        mainPos = new BlockPos(nbtTags.getInteger("mainX"), nbtTags.getInteger("mainY"), nbtTags.getInteger("mainZ"));
        prevPower = nbtTags.getInteger("prevPower");
        receivedCoords = nbtTags.getBoolean("receivedCoords");
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbtTags) {
        super.writeToNBT(nbtTags);
        nbtTags.setInteger("mainX", getMainPos().getX());
        nbtTags.setInteger("mainY", getMainPos().getY());
        nbtTags.setInteger("mainZ", getMainPos().getZ());
        nbtTags.setInteger("prevPower", prevPower);
        nbtTags.setBoolean("receivedCoords", receivedCoords);
        return nbtTags;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing facing) {
        return capability == Capabilities.TILE_BYTE_BUF || super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing facing) {
        if (capability == Capabilities.TILE_BYTE_BUF) {
            return Capabilities.TILE_BYTE_BUF.cast(this);
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public void writePacket(ByteBuf buf, ByteBufType type, Object... obj) {
        buf.writeInt(getMainPos().getX());
        buf.writeInt(getMainPos().getY());
        buf.writeInt(getMainPos().getZ());
        buf.writeInt(prevPower);
        buf.writeBoolean(receivedCoords);
    }

    @Override
    public void readPacket(ByteBuf buf, ByteBufType type) {
        if(type == ByteBufType.SERVER_TO_CLIENT) {
            mainPos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
            prevPower = buf.readInt();
            receivedCoords = buf.readBoolean();
        }
    }
}