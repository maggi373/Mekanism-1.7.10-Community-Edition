package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.common.Mekanism;
import mekanism.common.base.ByteBufType;
import mekanism.common.handler.PacketHandler;
import mekanism.common.multiblock.*;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class TileEntityMultiblock<T extends SynchronizedData<T>> extends TileEntityContainerBlock implements IMultiblock<T> {

    /**
     * The multiblock data for this structure.
     */
    @Nullable
    public T structure;

    /**
     * Whether or not to send this multiblock's structure in the next update packet.
     */
    public boolean sendStructure;

    /**
     * This multiblock's previous "has structure" state.
     */
    public boolean prevStructure;

    /**
     * Whether or not this multiblock has it's structure, for the client side mechanics.
     */
    public boolean clientHasStructure;

    /**
     * Whether or not this multiblock segment is rendering the structure.
     */
    public boolean isRendering;

    /**
     * This multiblock segment's cached data
     */
    public MultiblockCache<T> cachedData = getNewCache();

    /**
     * This multiblock segment's cached inventory ID
     */
    @Nullable
    public String cachedID = null;

    public TileEntityMultiblock(String name) {
        super(name);
    }

    @Override
    public void onUpdate() {
        if (world.isRemote) {
            if (structure == null) {
                structure = getNewStructure();
            }
            if (structure != null && structure.renderLocation != null && clientHasStructure && isRendering && !prevStructure) {
                Mekanism.proxy.doMultiblockSparkle(this, structure.renderLocation.getPos(), structure.volLength, structure.volWidth, structure.volHeight,
                      tile -> MultiblockManager.areEqual(this, tile));
            }
            prevStructure = clientHasStructure;
        }

        if (playersUsing.size() > 0 && ((world.isRemote && !clientHasStructure) || (!world.isRemote && structure == null))) {
            for (EntityPlayer player : playersUsing) {
                player.closeScreen();
            }
        }

        if (!world.isRemote) {
            if (structure == null) {
                isRendering = false;
                if (cachedID != null) {
                    getManager().updateCache(this);
                }
                if (ticker == 5) {
                    doUpdate();
                }
            }

            if (prevStructure == (structure == null)) {
                if (structure != null && !structure.hasRenderer) {
                    structure.hasRenderer = true;
                    isRendering = true;
                    sendStructure = true;
                }

                Coord4D thisCoord = Coord4D.get(this);
                for (EnumFacing side : EnumFacing.VALUES) {
                    Coord4D obj = thisCoord.offset(side);
                    if (structure != null && (structure.locations.contains(obj) || structure.internalLocations.contains(obj))) {
                        continue;
                    }
                    TileEntity tile = obj.getTileEntity(world);
                    if (!obj.isAirBlock(world) && (tile == null || tile.getClass() != getClass()) && !(tile instanceof IStructuralMultiblock || tile instanceof IMultiblock)) {
                        MekanismUtils.notifyNeighborofChange(world, obj, getPos());
                    }
                }

                Mekanism.packetHandler.sendUpdatePacket(this);
            }

            prevStructure = structure != null;

            if (structure != null) {
                structure.didTick = false;
                if (structure.inventoryID != null) {
                    cachedData.sync(structure);
                    cachedID = structure.inventoryID;
                    getManager().updateCache(this);
                }
            }
        }
    }

    @Override
    public void doUpdate() {
        if (!world.isRemote && (structure == null || !structure.didTick)) {
            getProtocol().doUpdate();
            if (structure != null) {
                structure.didTick = true;
            }
        }
    }

    public void sendPacketToRenderer() {
        if (structure != null) {
            for (Coord4D obj : structure.locations) {
                TileEntityMultiblock<T> tileEntity = (TileEntityMultiblock<T>) obj.getTileEntity(world);
                if (tileEntity != null && tileEntity.isRendering) {
                    Mekanism.packetHandler.sendUpdatePacket(tileEntity);
                }
            }
        }
    }

    protected abstract T getNewStructure();

    public abstract MultiblockCache<T> getNewCache();

    protected abstract UpdateProtocol<T> getProtocol();

    public abstract MultiblockManager<T> getManager();

    @Override
    public void writePacket(ByteBuf buf, ByteBufType type) {
        super.writePacket(buf, type);
        buf.writeBoolean(isRendering);
        buf.writeBoolean(structure != null);

        if (structure != null && isRendering) {
            if (sendStructure) {
                sendStructure = false;

                buf.writeBoolean(true);

                buf.writeInt(structure.volHeight);
                buf.writeInt(structure.volWidth);
                buf.writeInt(structure.volLength);

                structure.renderLocation.write(buf);
                buf.writeBoolean(structure.inventoryID != null);//boolean for if has inv id
                if (structure.inventoryID != null) {
                    ByteBufUtils.writeUTF8String(buf, structure.inventoryID);
                }
            } else {
                buf.writeBoolean(false);
            }
        }
    }

    @Override
    public void readPacket(ByteBuf buf, ByteBufType type) {
        super.readPacket(buf, type);
        if(type == ByteBufType.SERVER_TO_CLIENT) {
            if (structure == null) {
                structure = getNewStructure();
            }

            isRendering = buf.readBoolean();
            clientHasStructure = buf.readBoolean();
            if (clientHasStructure && isRendering) {
                if (buf.readBoolean()) {
                    structure.volHeight = buf.readInt();
                    structure.volWidth = buf.readInt();
                    structure.volLength = buf.readInt();
                    structure.renderLocation = Coord4D.read(buf);
                    if (buf.readBoolean()) {
                        structure.inventoryID = PacketHandler.readString(buf);
                    } else {
                        structure.inventoryID = null;
                    }
                }
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbtTags) {
        super.readFromNBT(nbtTags);
        if (structure == null) {
            if (nbtTags.hasKey("cachedID")) {
                cachedID = nbtTags.getString("cachedID");
                cachedData.load(nbtTags);
            }
        }
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbtTags) {
        super.writeToNBT(nbtTags);
        if (cachedID != null) {
            nbtTags.setString("cachedID", cachedID);
            cachedData.save(nbtTags);
        }
        return nbtTags;
    }

    @Override
    protected NonNullList<ItemStack> getInventory() {
        return structure != null ? structure.getInventory() : null;
    }

    @Override
    public boolean onActivate(EntityPlayer player, EnumHand hand, ItemStack stack) {
        return false;
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }

    @Override
    public boolean handleInventory() {
        return false;
    }

    @Override
    public T getSynchronizedData() {
        return structure;
    }
}