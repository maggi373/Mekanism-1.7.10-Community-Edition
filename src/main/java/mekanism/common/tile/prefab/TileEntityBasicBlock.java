package mekanism.common.tile.prefab;

import mekanism.api.Coord4D;
import mekanism.common.Mekanism;
import mekanism.common.base.INetworkNBT;
import mekanism.common.base.ITileComponent;
import mekanism.common.base.NBTType;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.config.MekanismConfig;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.FrequencyManager;
import mekanism.common.frequency.IFrequencyHandler;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.security.ISecurityTile;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.fml.common.Optional.Interface;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Interface(iface = "ic2.api.tile.IWrenchable", modid = MekanismHooks.IC2_MOD_ID)
public abstract class TileEntityBasicBlock extends TileEntity implements INetworkNBT, IFrequencyHandler, ITickable {

    /**
     * The direction this block is facing.
     */
    public EnumFacing facing = EnumFacing.NORTH;

    /**
     * The players currently using this block.
     */
    public Set<EntityPlayer> playersUsing = new HashSet<>();

    /**
     * A timer used to send packets to clients.
     */
    public int ticker;

    public boolean redstone = false;
    public boolean redstoneLastTick = false;

    public boolean doAutoSync = true;

    public List<ITileComponent> components = new ArrayList<>();

    @Override
    public void onLoad() {
        super.onLoad();
        if (!world.isRemote && MekanismConfig.current().general.destroyDisabledBlocks.val()) {
            MachineType type = MachineType.get(getBlockType(), getBlockMetadata());
            if (type != null && !type.isEnabled()) {
                Mekanism.logger.info("Destroying machine of type '" + type.getBlockName() + "' at coords " + Coord4D.get(this) + " as according to config.");
                world.setBlockToAir(getPos());
            }
        }
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, -1, writeNetworkNBT(new NBTTagCompound(), NBTType.TILE_UPDATE));
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readNetworkNBT(pkt.getNbtCompound(), NBTType.TILE_UPDATE);
        world.markBlockRangeForRenderUpdate(pos, pos);
    }

    @Override
    public void update() {
        for (ITileComponent component : components) {
            component.tick();
        }
        onUpdate();
        if (!world.isRemote) {
            if (doAutoSync && playersUsing.size() > 0) {
                sendPackets();
            }
        }
        ticker++;
        redstoneLastTick = redstone;
    }

    @Override
    public final void readFromNBT(NBTTagCompound nbtTags) {
        super.readFromNBT(nbtTags);
        this.readNetworkNBT(nbtTags, NBTType.ALL_SAVE);
    }

    @Nonnull
    @Override
    public final NBTTagCompound writeToNBT(NBTTagCompound nbtTags) {
        super.writeToNBT(nbtTags);
        this.writeNetworkNBT(nbtTags, NBTType.ALL_SAVE);
        return nbtTags;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(super.getUpdateTag());
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        readFromNBT(tag);
    }

    @Override
    public NBTTagCompound writeNetworkNBT(NBTTagCompound tag, NBTType type) {
        if(type.isAllSave() || type.isTileUpdate()) {
            tag.setInteger("facing", facing.getIndex());
            tag.setBoolean("redstone", redstone);
            components.iterator().forEachRemaining(iTileComponent -> iTileComponent.write(tag));
        }
        return tag;
    }

    @Override
    public void readNetworkNBT(NBTTagCompound tag, NBTType type) {
        if(type.isAllSave() || type.isTileUpdate()) {
            facing = EnumFacing.byIndex(tag.getInteger("facing"));
            redstone = tag.getBoolean("redstone");
            components.iterator().forEachRemaining(iTileComponent -> iTileComponent.read(tag));
        }
    }

    @Override
    public void updateContainingBlockInfo() {
        super.updateContainingBlockInfo();
        onAdded();
    }

    public void open(EntityPlayer player) {
        playersUsing.add(player);
    }

    public void close(EntityPlayer player) {
        playersUsing.remove(player);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        for (ITileComponent component : components) {
            component.invalidate();
        }
    }

    /**
     * Update call for machines. Use instead of updateEntity -- it's called every tick.
     */
    public abstract void onUpdate();

    public void setFacing(@Nonnull EnumFacing direction) {
        if (canSetFacing(direction)) {
            facing = direction;
        }
        if (!world.isRemote) {
            sendPackets();
        }
    }

    public void sendPackets() {
        IBlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 3);
    }

    /**
     * Whether or not this block's orientation can be changed to a specific direction. True by default.
     *
     * @param facing - facing to check
     *
     * @return if the block's orientation can be changed
     */
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return true;
    }

    public boolean isPowered() {
        return redstone;
    }

    public boolean wasPowered() {
        return redstoneLastTick;
    }

    public void onPowerChange() { }

    public void onNeighborChange(Block block) {
        if (!world.isRemote) {
            updatePower();
        }
    }

    private void updatePower() {
        boolean power = world.getRedstonePowerFromNeighbors(getPos()) > 0;
        if (redstone != power) {
            redstone = power;
            sendPackets();
            onPowerChange();
        }
    }

    /**
     * Called when block is placed in world
     */
    public void onAdded() {
        updatePower();
    }

    @Override
    public Frequency getFrequency(FrequencyManager manager) {
        if (manager == Mekanism.securityFrequencies && this instanceof ISecurityTile) {
            return ((ISecurityTile) this).getSecurity().getFrequency();
        }
        return null;
    }
}