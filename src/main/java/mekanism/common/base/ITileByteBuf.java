package mekanism.common.base;

import io.netty.buffer.ByteBuf;

public interface ITileByteBuf {

    void writePacket(ByteBuf buf, ByteBufType type);

    void readPacket(ByteBuf buf, ByteBufType type);
}
