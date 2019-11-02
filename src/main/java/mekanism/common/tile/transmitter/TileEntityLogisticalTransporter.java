package mekanism.common.tile.transmitter;

import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nonnull;
import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Mekanism;
import mekanism.common.base.ByteBufType;
import mekanism.common.base.ILogisticalTransporter;
import mekanism.common.base.INetworkNBT;
import mekanism.common.base.NBTType;
import mekanism.common.block.property.PropertyColor;
import mekanism.common.block.states.BlockStateTransmitter.TransmitterType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.content.transporter.PathfinderCache;
import mekanism.common.content.transporter.TransitRequest;
import mekanism.common.content.transporter.TransitRequest.TransitResponse;
import mekanism.common.content.transporter.TransporterStack;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.TransporterTier;
import mekanism.common.transmitters.TransporterImpl;
import mekanism.common.transmitters.grid.InventoryNetwork;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TextComponentGroup;
import mekanism.common.util.TransporterUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.util.Constants;

public class TileEntityLogisticalTransporter extends TileEntityTransmitter<TileEntity, InventoryNetwork, Void> implements INetworkNBT {

    private final int SYNC_PACKET = 1;
    private final int BATCH_PACKET = 2;

    public TransporterTier tier = TransporterTier.BASIC;

    private int delay = 0;
    private int delayCount = 0;

    public TileEntityLogisticalTransporter() {
        transmitterDelegate = new TransporterImpl(this);
    }

    @Override
    public BaseTier getBaseTier() {
        return tier.getBaseTier();
    }

    @Override
    public void setBaseTier(BaseTier baseTier) {
        tier = TransporterTier.get(baseTier);
    }

    @Override
    public TransmitterType getTransmitterType() {
        return TransmitterType.LOGISTICAL_TRANSPORTER;
    }

    @Override
    public TransmissionType getTransmissionType() {
        return TransmissionType.ITEM;
    }

    @Override
    public void onWorldSeparate() {
        super.onWorldSeparate();
        if (!getWorld().isRemote) {
            PathfinderCache.onChanged(new Coord4D(getPos(), getWorld()));
        }
    }

    @Override
    public TileEntity getCachedAcceptor(EnumFacing side) {
        return getCachedTile(side);
    }

    @Override
    public boolean isValidTransmitter(TileEntity tileEntity) {
        ILogisticalTransporter transporter = CapabilityUtils.getCapability(tileEntity, Capabilities.LOGISTICAL_TRANSPORTER_CAPABILITY, null);
        if (getTransmitter().getColor() == null || transporter.getColor() == null || getTransmitter().getColor() == transporter.getColor()) {
            return super.isValidTransmitter(tileEntity);
        }
        return false;
    }

    @Override
    public boolean isValidAcceptor(TileEntity tile, EnumFacing side) {
        return TransporterUtils.isValidAcceptorOnSide(tile, side);
    }

    @Override
    public boolean handlesRedstone() {
        return false;
    }

    @Override
    public void update() {
        super.update();
        getTransmitter().update();
    }

    public void pullItems() {
        // If a delay has been imposed, wait a bit
        if (delay > 0) {
            delay--;
            return;
        }

        // Reset delay to 3 ticks; if nothing is available to insert OR inserted, we'll try again
        // in 3 ticks
        delay = 3;

        // Attempt to pull
        for (EnumFacing side : getConnections(ConnectionType.PULL)) {
            final TileEntity tile = MekanismUtils.getTileEntity(world, getPos().offset(side));
            if (tile != null) {
                TransitRequest request = TransitRequest.buildInventoryMap(tile, side, tier.getPullAmount());

                // There's a stack available to insert into the network...
                if (!request.isEmpty()) {
                    TransitResponse response = TransporterUtils.insert(tile, getTransmitter(), request, getTransmitter().getColor(), true, 0);

                    // If the insert succeeded, remove the inserted count and try again for another 10 ticks
                    if (!response.isEmpty()) {
                        response.getInvStack(tile, side.getOpposite()).use(response.getSendingAmount());
                        delay = 10;
                    } else {
                        // Insert failed; increment the backoff and calculate delay. Note that we cap retries
                        // at a max of 40 ticks (2 seocnds), which would be 4 consecutive retries
                        delayCount++;
                        delay = Math.min(40, (int) Math.exp(delayCount));
                    }
                }
            }
        }
    }

    @Override
    public void onWorldJoin() {
        super.onWorldJoin();
        PathfinderCache.onChanged(new Coord4D(getPos(), getWorld()));
    }

    @Override
    public InventoryNetwork createNewNetwork() {
        return new InventoryNetwork();
    }

    @Override
    public InventoryNetwork createNetworkByMerging(Collection<InventoryNetwork> networks) {
        return new InventoryNetwork(networks);
    }

    @Override
    public void readPacket(ByteBuf buf, ByteBufType type) {
        if(type == ByteBufType.SERVER_TO_CLIENT) {
            int type2 = buf.readInt();
            if (type2 == 0) {
                super.readPacket(buf, type);
                tier = TransporterTier.values()[buf.readInt()];
                int c = buf.readInt();
                EnumColor prev = getTransmitter().getColor();
                if (c != -1) {
                    getTransmitter().setColor(TransporterUtils.colors.get(c));
                } else {
                    getTransmitter().setColor(null);
                }
                if (prev != getTransmitter().getColor()) {
                    MekanismUtils.updateBlock(world, pos);
                }
                getTransmitter().readFromPacket(buf);
            }
        }
    }

    @Override
    public void writePacket(ByteBuf buf, ByteBufType type) {
        buf.writeInt(0);
        super.writePacket(buf, type);
        buf.writeInt(tier.ordinal());
        if (getTransmitter().getColor() != null) {
            buf.writeInt(TransporterUtils.colors.indexOf(getTransmitter().getColor()));
        } else {
            buf.writeInt(-1);
        }

        // Serialize all the in-flight stacks (this includes their ID)
        getTransmitter().writeToPacket(buf);
    }

    @Override
    public void readNetworkNBT(NBTTagCompound tag, NBTType type) {
        switch (type) {
            case SYNC_PACKET:
                readStack(tag);
                break;
            case BATCH_PACKET:
                NBTTagList list = tag.getTagList("l", Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < list.tagCount(); i++) {
                    readStack(list.getCompoundTagAt(i));
                }
                int deletes = tag.getInteger("2");
                for (int i = 0; i < deletes; i++) {
                    getTransmitter().deleteStack(tag.getInteger("d" + i));
                }
                break;
        }
    }

    public NBTTagCompound makeSyncPacketC(int stackId, TransporterStack stack) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("0", stackId);
        stack.writePacketNBT(getTransmitter(), tag);
        return tag;
    }

    public NBTTagCompound makeBatchPacketC(Map<Integer, TransporterStack> updates, Set<Integer> deletes) {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (Entry<Integer, TransporterStack> entry : updates.entrySet()) {
            NBTTagCompound tag1 = new NBTTagCompound();
            tag1.setInteger("0", entry.getKey());
            entry.getValue().writePacketNBT(getTransmitter(), tag1);
            list.appendTag(tag1);
        }
        tag.setTag("l", list);
        tag.setInteger("2", deletes.size());
        int c = 0;
        for(Integer i : deletes) {
            tag.setInteger("d"+c++, i);
        }
        return tag;
    }

    private void readStack(NBTTagCompound tag) {
        int id = tag.getInteger("0");
        TransporterStack stack = TransporterStack.readFromPacket(tag);
        if (stack.progress == 0) {
            stack.progress = 5;
        }
        getTransmitter().addStack(id, stack);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbtTags) {
        super.readFromNBT(nbtTags);
        if (nbtTags.hasKey("tier")) {
            tier = TransporterTier.values()[nbtTags.getInteger("tier")];
        }
        getTransmitter().readFromNBT(nbtTags);
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbtTags) {
        super.writeToNBT(nbtTags);
        nbtTags.setInteger("tier", tier.ordinal());
        if (getTransmitter().getColor() != null) {
            nbtTags.setInteger("color", TransporterUtils.colors.indexOf(getTransmitter().getColor()));
        }
        NBTTagList stacks = new NBTTagList();
        for (TransporterStack stack : getTransmitter().getTransit()) {
            NBTTagCompound tagCompound = new NBTTagCompound();
            stack.write(tagCompound);
            stacks.appendTag(tagCompound);
        }
        if (stacks.tagCount() != 0) {
            nbtTags.setTag("stacks", stacks);
        }
        return nbtTags;
    }

    @Override
    protected EnumActionResult onConfigure(EntityPlayer player, int part, EnumFacing side) {
        TransporterUtils.incrementColor(getTransmitter());
        onPartChanged(null);
        PathfinderCache.onChanged(new Coord4D(getPos(), getWorld()));
        Mekanism.packetHandler.sendUpdatePacket(this);
        TextComponentGroup msg = new TextComponentGroup(TextFormatting.GRAY).string(Mekanism.LOG_TAG + " ", TextFormatting.DARK_BLUE)
              .translation("tooltip.configurator.toggleColor").string(": ");

        if (getTransmitter().getColor() != null) {
            msg.appendSibling(getTransmitter().getColor().getTranslatedColouredComponent());
        } else {
            msg.translation("gui.none");
        }
        player.sendMessage(msg);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public EnumActionResult onRightClick(EntityPlayer player, EnumFacing side) {
        super.onRightClick(player, side);
        TextComponentGroup msg = new TextComponentGroup(TextFormatting.GRAY).string(Mekanism.LOG_TAG + " ", TextFormatting.DARK_BLUE)
              .translation("tooltip.configurator.viewColor").string(": ");

        if (getTransmitter().getColor() != null) {
            msg.appendSibling(getTransmitter().getColor().getTranslatedColouredComponent());
        } else {
            msg.translation("gui.none");
        }
        player.sendMessage(msg);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public EnumColor getRenderColor() {
        return getTransmitter().getColor();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (!getWorld().isRemote) {
            for (TransporterStack stack : getTransmitter().getTransit()) {
                TransporterUtils.drop(getTransmitter(), stack);
            }
        }
    }

    @Override
    public int getCapacity() {
        return 0;
    }

    @Override
    public Void getBuffer() {
        return null;
    }

    @Override
    public void takeShare() {
    }

    @Override
    public void updateShare() {
    }

    @Override
    public TransporterImpl getTransmitter() {
        return (TransporterImpl) transmitterDelegate;
    }

    public double getCost() {
        return (double) TransporterTier.ULTIMATE.getSpeed() / (double) tier.getSpeed();
    }

    @Override
    public boolean upgrade(int tierOrdinal) {
        if (tier.ordinal() < BaseTier.ULTIMATE.ordinal() && tierOrdinal == tier.ordinal() + 1) {
            tier = TransporterTier.values()[tier.ordinal() + 1];
            markDirtyTransmitters();
            sendDesc = true;
            return true;
        }
        return false;
    }

    @Override
    public IBlockState getExtendedState(IBlockState state) {
        return ((IExtendedBlockState) super.getExtendedState(state)).withProperty(PropertyColor.INSTANCE, new PropertyColor(getRenderColor()));
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        return capability == Capabilities.LOGISTICAL_TRANSPORTER_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (capability == Capabilities.LOGISTICAL_TRANSPORTER_CAPABILITY) {
            return Capabilities.LOGISTICAL_TRANSPORTER_CAPABILITY.cast(getTransmitter());
        }
        return super.getCapability(capability, side);
    }

    @Override
    public NBTTagCompound writeNetworkNBT(NBTTagCompound tag, NBTType type) {
        return tag;
    }
}