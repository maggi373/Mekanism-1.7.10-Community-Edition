package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.common.base.ByteBufType;
import mekanism.common.base.ITileByteBuf;
import mekanism.common.handler.PacketHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketByteBuf implements IMessageHandler<PacketByteBuf.ByteBufMessage, IMessage> {

    @Override
    public IMessage onMessage(ByteBufMessage message, MessageContext ctx) {
        EntityPlayer player = PacketHandler.getPlayer(ctx);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            TileEntity tileEntity = message.coord4D.getTileEntity(player.world);
            if(tileEntity instanceof ITileByteBuf) {
                ((ITileByteBuf) tileEntity).readPacket(message.buf, message.type);
            }
            message.buf.release();
        }, player);
        return null;
    }

    public static class ByteBufMessage implements IMessage {

        public ITileByteBuf tile; // Origin
        public Coord4D coord4D; // Network
        public ByteBuf buf; // Network
        public ByteBufType type; // Network
        public Object[] obj; // Origin

        public ByteBufMessage() {

        }

        public <T extends TileEntity & ITileByteBuf> ByteBufMessage(T tile, ByteBufType type, Object... obj) {
            this.coord4D = Coord4D.get(tile);
            this.tile = tile;
            this.type = type;
            this.obj = obj;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.coord4D = Coord4D.read(buf);
            this.type = ByteBufType.values()[buf.readInt()];
            this.buf = buf.retain();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            coord4D.write(buf);
            buf.writeInt(type.ordinal());
            tile.writePacket(buf, type, obj);
        }
    }
}
