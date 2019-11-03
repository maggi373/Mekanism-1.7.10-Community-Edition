package mekanism.common.base;

import io.netty.buffer.ByteBuf;

/**
 * @author BloCamLimb
 */
public interface ITileByteBuf {

    /**
     * Write packet that need to send
     * @param buf Original ByteBuf
     * @param type Determine which data to write
     * @param obj Custom parameter mainly used for GUI_TO_SERVER type
     */
    void writePacket(ByteBuf buf, ByteBufType type, Object... obj);

    void readPacket(ByteBuf buf, ByteBufType type);
}
