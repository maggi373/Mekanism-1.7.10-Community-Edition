package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.IConfigCardAccess.ISpecialConfigData;
import mekanism.api.TileNetworkList;
import mekanism.common.Mekanism;
import mekanism.common.base.ByteBufType;
import mekanism.common.base.IRedstoneControl;
import mekanism.common.base.ISustainedData;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.content.filter.IFilter;
import mekanism.common.handler.PacketHandler;
import mekanism.common.misc.HashList;
import mekanism.common.misc.OreDictCache;
import mekanism.common.network.PacketByteBuf;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StackUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;

public class TileEntityOredictionificator extends TileEntityContainerBlock implements IRedstoneControl, ISpecialConfigData, ISustainedData, ISecurityTile {

    public static final int MAX_LENGTH = 24;
    private static final int[] SLOTS = {0, 1};
    public static List<String> possibleFilters = Arrays.asList("ingot", "ore", "dust", "nugget");
    public HashList<OredictionificatorFilter> filters = new HashList<>();
    public RedstoneControl controlType = RedstoneControl.DISABLED;

    public boolean didProcess;

    public TileComponentSecurity securityComponent = new TileComponentSecurity(this);

    public TileEntityOredictionificator() {
        super(MachineType.OREDICTIONIFICATOR.getBlockName());
        inventory = NonNullList.withSize(2, ItemStack.EMPTY);
        doAutoSync = false;
    }

    @Override
    public void onUpdate() {
        if (!world.isRemote) {
            if (playersUsing.size() > 0) {
                for (EntityPlayer player : playersUsing) {
                    Mekanism.packetHandler.sendTo(new PacketByteBuf.ByteBufMessage(this, ByteBufType.SERVER_TO_CLIENT, 1), (EntityPlayerMP) player);
                }
            }

            didProcess = false;
            ItemStack inputStack = inventory.get(0);
            if (MekanismUtils.canFunction(this) && !inputStack.isEmpty() && getValidName(inputStack) != null) {
                ItemStack result = getResult(inputStack);
                if (!result.isEmpty()) {
                    ItemStack outputStack = inventory.get(1);
                    if (outputStack.isEmpty()) {
                        inputStack.shrink(1);
                        if (inputStack.getCount() <= 0) {
                            inventory.set(0, ItemStack.EMPTY);
                        }
                        inventory.set(1, result);
                        didProcess = true;
                    } else if (ItemHandlerHelper.canItemStacksStack(outputStack, result) && outputStack.getCount() < outputStack.getMaxStackSize()) {
                        inputStack.shrink(1);
                        if (inputStack.getCount() <= 0) {
                            inventory.set(0, ItemStack.EMPTY);
                        }
                        outputStack.grow(1);
                        didProcess = true;
                    }
                    markDirty();
                }
            }
        }
    }

    public String getValidName(ItemStack stack) {
        List<String> def = OreDictCache.getOreDictName(stack);
        for (String s : def) {
            for (String pre : possibleFilters) {
                if (s.startsWith(pre)) {
                    return s;
                }
            }
        }
        return null;
    }

    public ItemStack getResult(ItemStack stack) {
        String s = getValidName(stack);
        if (s == null) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> ores = OreDictionary.getOres(s, false);
        for (OredictionificatorFilter filter : filters) {
            if (filter.filter.equals(s)) {
                if (ores.size() - 1 >= filter.index) {
                    return StackUtils.size(ores.get(filter.index), 1);
                }
                return ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        if (side != facing) {
            return SLOTS;
        }
        return InventoryUtils.EMPTY;
    }

    @Override
    public boolean canExtractItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        return slotID == 1;
    }

    @Override
    public boolean isItemValidForSlot(int slotID, @Nonnull ItemStack itemstack) {
        return slotID == 0 && !getResult(itemstack).isEmpty();

    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbtTags) {
        super.writeToNBT(nbtTags);
        nbtTags.setInteger("controlType", controlType.ordinal());
        NBTTagList filterTags = new NBTTagList();
        for (OredictionificatorFilter filter : filters) {
            NBTTagCompound tagCompound = new NBTTagCompound();
            filter.write(tagCompound);
            filterTags.appendTag(tagCompound);
        }
        if (filterTags.tagCount() != 0) {
            nbtTags.setTag("filters", filterTags);
        }
        return nbtTags;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbtTags) {
        super.readFromNBT(nbtTags);
        controlType = RedstoneControl.values()[nbtTags.getInteger("controlType")];
        if (nbtTags.hasKey("filters")) {
            NBTTagList tagList = nbtTags.getTagList("filters", NBT.TAG_COMPOUND);
            for (int i = 0; i < tagList.tagCount(); i++) {
                filters.add(OredictionificatorFilter.readFromNBT(tagList.getCompoundTagAt(i)));
            }
        }

        //to fix any badly placed blocks in the world
        if (facing.getAxis() == EnumFacing.Axis.Y) {
            facing = EnumFacing.NORTH;
        }
    }

    @Override
    public void readPacket(ByteBuf buf, ByteBufType type) {
        super.readPacket(buf, type);
        if(type == ByteBufType.SERVER_TO_CLIENT) {
            int i1 = buf.readInt();
            if (i1 == 0) {
                controlType = RedstoneControl.values()[buf.readInt()];
                didProcess = buf.readBoolean();
                filters.clear();

                int amount = buf.readInt();
                for (int i = 0; i < amount; i++) {
                    filters.add(OredictionificatorFilter.readFromPacket(buf));
                }
            } else if (i1 == 1) {
                controlType = RedstoneControl.values()[buf.readInt()];
                didProcess = buf.readBoolean();
            } else if (i1 == 2) {
                filters.clear();
                int amount = buf.readInt();
                for (int i = 0; i < amount; i++) {
                    filters.add(OredictionificatorFilter.readFromPacket(buf));
                }
            }
        }
    }

    @Override
    public void writePacket(ByteBuf buf, ByteBufType type, Object... obj) {
        super.writePacket(buf, type, obj);
        if(type == ByteBufType.SERVER_TO_CLIENT) {
            int packetType = 0;
            if(obj.length > 0) {
                try {
                    packetType = (int) obj[0];
                } catch (final Exception ignored) {

                }
            }
            switch (packetType) {
                case 0:
                    buf.writeInt(0);
                    buf.writeInt(controlType.ordinal());
                    buf.writeBoolean(didProcess);
                    buf.writeInt(filters.size());
                    for (OredictionificatorFilter filter : filters) {
                        filter.write(buf);
                    }
                    break;
                case 1:
                    writeGenericPacket(buf);
                    break;
                case 2:
                    writeFilterPacket(buf);
                    break;
            }
        }
    }

    public void writeGenericPacket(ByteBuf buf) {
        buf.writeInt(1);
        buf.writeInt(controlType.ordinal());
        buf.writeBoolean(didProcess);
    }

    public void writeFilterPacket(ByteBuf buf) {
        buf.writeInt(2);
        buf.writeInt(filters.size());
        for (OredictionificatorFilter filter : filters) {
            filter.write(buf);
        }
    }

    @Override
    public void openInventory(@Nonnull EntityPlayer player) {
        if (!world.isRemote) {
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
    }

    @Override
    public NBTTagCompound getConfigurationData(NBTTagCompound nbtTags) {
        NBTTagList filterTags = new NBTTagList();
        for (OredictionificatorFilter filter : filters) {
            NBTTagCompound tagCompound = new NBTTagCompound();
            filter.write(tagCompound);
            filterTags.appendTag(tagCompound);
        }
        if (filterTags.tagCount() != 0) {
            nbtTags.setTag("filters", filterTags);
        }
        return nbtTags;
    }

    @Override
    public void setConfigurationData(NBTTagCompound nbtTags) {
        if (nbtTags.hasKey("filters")) {
            NBTTagList tagList = nbtTags.getTagList("filters", NBT.TAG_COMPOUND);
            for (int i = 0; i < tagList.tagCount(); i++) {
                filters.add(OredictionificatorFilter.readFromNBT(tagList.getCompoundTagAt(i)));
            }
        }
    }

    @Override
    public String getDataType() {
        return getBlockType().getTranslationKey() + "." + fullName + ".name";
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        ItemDataUtils.setBoolean(itemStack, "hasOredictionificatorConfig", true);
        NBTTagList filterTags = new NBTTagList();
        for (OredictionificatorFilter filter : filters) {
            NBTTagCompound tagCompound = new NBTTagCompound();
            filter.write(tagCompound);
            filterTags.appendTag(tagCompound);
        }
        if (filterTags.tagCount() != 0) {
            ItemDataUtils.setList(itemStack, "filters", filterTags);
        }
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (ItemDataUtils.hasData(itemStack, "hasOredictionificatorConfig")) {
            if (ItemDataUtils.hasData(itemStack, "filters")) {
                NBTTagList tagList = ItemDataUtils.getList(itemStack, "filters");
                for (int i = 0; i < tagList.tagCount(); i++) {
                    filters.add(OredictionificatorFilter.readFromNBT(tagList.getCompoundTagAt(i)));
                }
            }
        }
    }

    @Override
    public RedstoneControl getControlType() {
        return controlType;
    }

    @Override
    public void setControlType(RedstoneControl type) {
        controlType = type;
    }

    @Override
    public boolean canPulse() {
        return true;
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return false;
        }
        return capability == Capabilities.CONFIG_CARD_CAPABILITY || capability == Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return null;
        } else if (capability == Capabilities.CONFIG_CARD_CAPABILITY || capability == Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY) {
            return (T) this;
        }
        return super.getCapability(capability, side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return side == facing;
        }
        return super.isCapabilityDisabled(capability, side);
    }

    @Override
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return facing != EnumFacing.DOWN && facing != EnumFacing.UP;
    }

    public static class OredictionificatorFilter implements IFilter {

        public String filter;
        public int index;

        public static OredictionificatorFilter readFromNBT(NBTTagCompound nbtTags) {
            OredictionificatorFilter filter = new OredictionificatorFilter();
            filter.read(nbtTags);
            return filter;
        }

        public static OredictionificatorFilter readFromPacket(ByteBuf dataStream) {
            OredictionificatorFilter filter = new OredictionificatorFilter();
            filter.read(dataStream);
            return filter;
        }

        public void write(NBTTagCompound nbtTags) {
            nbtTags.setString("filter", filter);
            nbtTags.setInteger("index", index);
        }

        protected void read(NBTTagCompound nbtTags) {
            filter = nbtTags.getString("filter");
            index = nbtTags.getInteger("index");
        }

        public void write(ByteBuf buf) {
            ByteBufUtils.writeUTF8String(buf, filter);
            buf.writeInt(index);
        }

        protected void read(ByteBuf dataStream) {
            filter = PacketHandler.readString(dataStream);
            index = dataStream.readInt();
        }

        @Override
        public OredictionificatorFilter clone() {
            OredictionificatorFilter newFilter = new OredictionificatorFilter();
            newFilter.filter = filter;
            newFilter.index = index;
            return newFilter;
        }

        @Override
        public int hashCode() {
            int code = 1;
            code = 31 * code + filter.hashCode();
            return code;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof OredictionificatorFilter && ((OredictionificatorFilter) obj).filter.equals(filter);
        }
    }
}