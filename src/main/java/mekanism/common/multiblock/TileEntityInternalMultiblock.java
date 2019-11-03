package mekanism.common.multiblock;

import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import mekanism.common.base.ByteBufType;
import mekanism.common.handler.PacketHandler;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;

public class TileEntityInternalMultiblock extends TileEntityBasicBlock {

    public String multiblockUUID;

    @Override
    public void onUpdate() {
    }

    @Override
    public void readPacket(ByteBuf buf, ByteBufType type) {
        super.readPacket(buf, type);
        if(type == ByteBufType.SERVER_TO_CLIENT) {
            if (buf.readBoolean()) {
                multiblockUUID = PacketHandler.readString(buf);
            } else {
                multiblockUUID = null;
            }
        }
    }

    @Override
    public void writePacket(ByteBuf buf, ByteBufType type, Object... obj) {
        super.writePacket(buf, type, obj);
        if(type == ByteBufType.SERVER_TO_CLIENT) {
            if (multiblockUUID != null) {
                buf.writeBoolean(true);
                ByteBufUtils.writeUTF8String(buf, multiblockUUID);
            } else {
                buf.writeBoolean(false);
            }
        }
    }

    public void setMultiblock(String id) {
        multiblockUUID = id;
    }

    public String getMultiblock() {
        return multiblockUUID;
    }
}