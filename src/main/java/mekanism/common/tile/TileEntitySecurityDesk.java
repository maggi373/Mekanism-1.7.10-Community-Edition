package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.common.Mekanism;
import mekanism.common.base.ByteBufType;
import mekanism.common.base.IBoundingBlock;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.FrequencyManager;
import mekanism.common.handler.PacketHandler;
import mekanism.common.network.PacketSecurityUpdate.SecurityPacket;
import mekanism.common.network.PacketSecurityUpdate.SecurityUpdateMessage;
import mekanism.common.security.IOwnerItem;
import mekanism.common.security.ISecurityItem;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.security.SecurityData;
import mekanism.common.security.SecurityFrequency;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;
import java.util.UUID;

public class TileEntitySecurityDesk extends TileEntityContainerBlock implements IBoundingBlock {

    private static final int[] SLOTS = {0, 1};

    public UUID ownerUUID;
    public String clientOwner;

    public SecurityFrequency frequency;

    public TileEntitySecurityDesk() {
        super("SecurityDesk");
        inventory = NonNullList.withSize(SLOTS.length, ItemStack.EMPTY);
    }

    @Override
    public void onUpdate() {
        if (!world.isRemote) {
            if (ownerUUID != null && frequency != null) {
                if (!inventory.get(0).isEmpty() && inventory.get(0).getItem() instanceof IOwnerItem) {
                    IOwnerItem item = (IOwnerItem) inventory.get(0).getItem();
                    if (item.hasOwner(inventory.get(0)) && item.getOwnerUUID(inventory.get(0)) != null) {
                        if (item.getOwnerUUID(inventory.get(0)).equals(ownerUUID)) {
                            item.setOwnerUUID(inventory.get(0), null);
                            if (item instanceof ISecurityItem && ((ISecurityItem) item).hasSecurity(inventory.get(0))) {
                                ((ISecurityItem) item).setSecurity(inventory.get(0), SecurityMode.PUBLIC);
                            }
                        }
                    }
                }

                if (!inventory.get(1).isEmpty() && inventory.get(1).getItem() instanceof IOwnerItem) {
                    IOwnerItem item = (IOwnerItem) inventory.get(1).getItem();
                    if (item.hasOwner(inventory.get(1))) {
                        if (item.getOwnerUUID(inventory.get(1)) == null) {
                            item.setOwnerUUID(inventory.get(1), ownerUUID);
                        }
                        if (item.getOwnerUUID(inventory.get(1)).equals(ownerUUID)) {
                            if (item instanceof ISecurityItem && ((ISecurityItem) item).hasSecurity(inventory.get(1))) {
                                ((ISecurityItem) item).setSecurity(inventory.get(1), frequency.securityMode);
                            }
                        }
                    }
                }
            }

            if (frequency == null && ownerUUID != null) {
                setFrequency(ownerUUID);
            }

            FrequencyManager manager = getManager(frequency);
            if (manager != null) {
                if (frequency != null && !frequency.valid) {
                    frequency = (SecurityFrequency) manager.validateFrequency(ownerUUID, Coord4D.get(this), frequency);
                }
                if (frequency != null) {
                    frequency = (SecurityFrequency) manager.update(Coord4D.get(this), frequency);
                }
            } else {
                frequency = null;
            }
        }
    }

    public FrequencyManager getManager(Frequency freq) {
        if (ownerUUID == null || freq == null) {
            return null;
        }
        return Mekanism.securityFrequencies;
    }

    public void setFrequency(UUID owner) {
        FrequencyManager manager = Mekanism.securityFrequencies;
        manager.deactivate(Coord4D.get(this));
        for (Frequency freq : manager.getFrequencies()) {
            if (freq.ownerUUID.equals(owner)) {
                frequency = (SecurityFrequency) freq;
                frequency.activeCoords.add(Coord4D.get(this));
                return;
            }
        }

        Frequency freq = new SecurityFrequency(owner).setPublic(true);
        freq.activeCoords.add(Coord4D.get(this));
        manager.addFrequency(freq);
        frequency = (SecurityFrequency) freq;
        MekanismUtils.saveChunk(this);
        markDirty();
    }

    @Override
    public void readPacket(ByteBuf buf, ByteBufType type) {
        if(type == ByteBufType.GUI_TO_SERVER) {
            int type1 = buf.readInt();
            if (type1 == 0) {
                if (frequency != null) {
                    frequency.trusted.add(PacketHandler.readString(buf));
                }
            } else if (type1 == 1) {
                if (frequency != null) {
                    frequency.trusted.remove(PacketHandler.readString(buf));
                }
            } else if (type1 == 2) {
                if (frequency != null) {
                    frequency.override = !frequency.override;
                    Mekanism.packetHandler.sendToAll(new SecurityUpdateMessage(SecurityPacket.UPDATE, ownerUUID, new SecurityData(frequency)));
                }
            } else if (type1 == 3) {
                if (frequency != null) {
                    frequency.securityMode = SecurityMode.values()[buf.readInt()];
                    Mekanism.packetHandler.sendToAll(new SecurityUpdateMessage(SecurityPacket.UPDATE, ownerUUID, new SecurityData(frequency)));
                }
            }
            MekanismUtils.saveChunk(this);
            return;
        }
        super.readPacket(buf, type);
        if(type == ByteBufType.SERVER_TO_CLIENT) {
            if (buf.readBoolean()) {
                clientOwner = PacketHandler.readString(buf);
                ownerUUID = PacketHandler.readUUID(buf);
            } else {
                clientOwner = null;
                ownerUUID = null;
            }
            if (buf.readBoolean()) {
                frequency = new SecurityFrequency(buf);
            } else {
                frequency = null;
            }
        }
    }

    @Override
    public void writePacket(ByteBuf buf, ByteBufType type, Object... obj) {
        if(type == ByteBufType.GUI_TO_SERVER) {
            buf.writeInt((Integer) obj[0]);
            try {
                Object obj2 = obj[1];
                if (obj2 instanceof String) {
                    ByteBufUtils.writeUTF8String(buf, (String) obj2);
                } else {
                    buf.writeInt((Integer) obj2);
                }
            } catch (Exception ignored) {

            }
            return;
        }
        super.writePacket(buf, type, obj);
        if(type == ByteBufType.SERVER_TO_CLIENT) {
            if (ownerUUID != null) {
                buf.writeBoolean(true);
                ByteBufUtils.writeUTF8String(buf, MekanismUtils.getLastKnownUsername(ownerUUID));
                PacketHandler.writeUUID(buf, ownerUUID);
            } else {
                buf.writeBoolean(false);
            }
            if (frequency != null) {
                buf.writeBoolean(true);
                frequency.write(buf);
            } else {
                buf.writeBoolean(false);
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbtTags) {
        super.readFromNBT(nbtTags);
        if (nbtTags.hasKey("ownerUUID")) {
            ownerUUID = UUID.fromString(nbtTags.getString("ownerUUID"));
        }
        if (nbtTags.hasKey("frequency")) {
            frequency = new SecurityFrequency(nbtTags.getCompoundTag("frequency"));
            frequency.valid = false;
        }
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbtTags) {
        super.writeToNBT(nbtTags);
        if (ownerUUID != null) {
            nbtTags.setString("ownerUUID", ownerUUID.toString());
        }
        if (frequency != null) {
            NBTTagCompound frequencyTag = new NBTTagCompound();
            frequency.write(frequencyTag);
            nbtTags.setTag("frequency", frequencyTag);
        }
        return nbtTags;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (!world.isRemote) {
            if (frequency != null) {
                FrequencyManager manager = getManager(frequency);
                if (manager != null) {
                    manager.deactivate(Coord4D.get(this));
                }
            }
        }
    }

    @Override
    public void onPlace() {
        MekanismUtils.makeBoundingBlock(world, getPos().up(), Coord4D.get(this));
    }

    @Override
    public void onBreak() {
        world.setBlockToAir(getPos().up());
        world.setBlockToAir(getPos());
    }

    @Override
    public Frequency getFrequency(FrequencyManager manager) {
        if (manager == Mekanism.securityFrequencies) {
            return frequency;
        }
        return null;
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        //Even though there are inventory slots make this return none as
        // accessible by automation, as then people could lock items to other
        // people unintentionally
        return InventoryUtils.EMPTY;
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            //For the same reason as the getSlotsForFace does not give any slots, don't expose this here
            return true;
        }
        return super.isCapabilityDisabled(capability, side);
    }
}