package mekanism.common.tile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import mekanism.api.Coord4D;
import mekanism.common.Mekanism;
import mekanism.common.base.NBTType;
import mekanism.common.multiblock.IMultiblock;
import mekanism.common.multiblock.IStructuralMultiblock;
import mekanism.common.multiblock.MultiblockCache;
import mekanism.common.multiblock.MultiblockManager;
import mekanism.common.multiblock.SynchronizedData;
import mekanism.common.multiblock.UpdateProtocol;
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
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

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

                sendPackets();
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
                    sendPackets();
                }
            }
        }
    }

    protected abstract T getNewStructure();

    public abstract MultiblockCache<T> getNewCache();

    protected abstract UpdateProtocol<T> getProtocol();

    public abstract MultiblockManager<T> getManager();

    @Override
    public NBTTagCompound writeNetworkNBT(NBTTagCompound tag, NBTType type) {
        super.writeNetworkNBT(tag, type);
        if(type.isAllSave()) {
            if (cachedID != null) {
                tag.setString("cachedID", cachedID);
                cachedData.save(tag);
            }
        }
        if(type.isTileUpdate()) {
            tag.setBoolean("1", isRendering);
            tag.setBoolean("2", structure != null);

            if (structure != null && isRendering) {
                if (sendStructure) {
                    sendStructure = false;

                    tag.setBoolean("3", true);

                    tag.setInteger("4", structure.volHeight);
                    tag.setInteger("5", structure.volWidth);
                    tag.setInteger("6", structure.volLength);

                    structure.renderLocation.write(tag);
                    tag.setBoolean("7", structure.inventoryID != null);
                    if (structure.inventoryID != null) {
                        tag.setString("8", structure.inventoryID);
                    }
                } else {
                    tag.setBoolean("3", false);
                }
            }
        }
        return tag;
    }

    @Override
    public void readNetworkNBT(NBTTagCompound tag, NBTType type) {
        super.readNetworkNBT(tag, type);
        if(type.isAllSave()) {
            if (structure == null) {
                if (tag.hasKey("cachedID")) {
                    cachedID = tag.getString("cachedID");
                    cachedData.load(tag);
                }
            }
        }
        if(type.isTileUpdate()) {
            if (structure == null) {
                structure = getNewStructure();
            }
            isRendering = tag.getBoolean("1");
            clientHasStructure = tag.getBoolean("2");
            if (clientHasStructure && isRendering) {
                if (tag.getBoolean("3")) {
                    structure.volHeight = tag.getInteger("4");
                    structure.volWidth = tag.getInteger("5");
                    structure.volLength = tag.getInteger("6");
                    structure.renderLocation = Coord4D.read(tag);
                    if (tag.getBoolean("7")) {
                        structure.inventoryID = tag.getString("8");
                    } else {
                        structure.inventoryID = null;
                    }
                }
            }
        }
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