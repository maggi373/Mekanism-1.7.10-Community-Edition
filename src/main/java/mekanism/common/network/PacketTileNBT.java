package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.common.base.INetworkNBT;
import mekanism.common.base.NBTType;
import mekanism.common.handler.PacketHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Slower than PacketByteBuf but NBT has identifier
 */
public class PacketTileNBT implements IMessageHandler<PacketTileNBT.TileNBTMessage, IMessage> {

    @Override
    public IMessage onMessage(TileNBTMessage message, MessageContext ctx) {
        EntityPlayer player = PacketHandler.getPlayer(ctx);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            TileEntity tileEntity = message.coord4D.getTileEntity(player.world);
            if(tileEntity instanceof INetworkNBT) {
                ((INetworkNBT) tileEntity).readNetworkNBT(message.tag, message.type);
            }
        }, player);
        return null;
    }

    public static class TileNBTMessage implements IMessage {

        public Coord4D coord4D;

        public NBTType type;

        public NBTTagCompound tag;

        public TileNBTMessage() {
        }

        public TileNBTMessage(TileEntity tile, INetworkNBT nbt, NBTType type) {
            this.coord4D = Coord4D.get(tile);
            this.tag = nbt.writeNetworkNBT(new NBTTagCompound(), type);
            this.type = type;
        }

        public TileNBTMessage(TileEntity tile, NBTTagCompound tag, NBTType type) {
            this.coord4D = Coord4D.get(tile);
            this.tag = tag;
            this.type = type;
        }

        public TileNBTMessage(Coord4D coord4D, NBTTagCompound tag, NBTType type) {
            this.coord4D = coord4D;
            this.tag = tag;
            this.type = type;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            coord4D = Coord4D.read(buf);
            type = NBTType.values()[buf.readInt()];
            tag = ByteBufUtils.readTag(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            coord4D.write(buf);
            buf.writeInt(type.ordinal());
            ByteBufUtils.writeTag(buf, tag);
        }
    }
}
