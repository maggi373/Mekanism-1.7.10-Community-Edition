package mekanism.common.tile;

import mekanism.common.tile.prefab.TileEntityBasicBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Multi-block used by wind turbines, solar panels, and other machines
 */
public class TileEntityBoundingBlock extends TileEntity {

    private BlockPos mainPos = BlockPos.ORIGIN;

    public boolean receivedCoords;

    public int prevPower;

    public void setMainLocation(BlockPos pos) {
        receivedCoords = pos != null;
        mainPos = pos;
        if(!world.isRemote) {
            sendPackets();
        }
    }

    public void sendPackets() {
        IBlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 3);
    }

    public BlockPos getMainPos() {
        return mainPos;
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
                if(!world.isRemote) {
                    sendPackets();
                }
            }
        }
    }

    public void onPower() { }

    public void onNoPower() { }

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
    public void readFromNBT(NBTTagCompound nbtTags) {
        super.readFromNBT(nbtTags);
        mainPos = new BlockPos(nbtTags.getInteger("mainX"), nbtTags.getInteger("mainY"), nbtTags.getInteger("mainZ"));
        prevPower = nbtTags.getInteger("prevPower");
        receivedCoords = nbtTags.getBoolean("receivedCoords");
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(super.getUpdateTag());
    }

}