package mekanism.common.capabilities;

import io.netty.buffer.ByteBuf;
import mekanism.common.base.ByteBufType;
import mekanism.common.base.ITileByteBuf;
import net.minecraftforge.common.capabilities.CapabilityManager;

public class DefaultTileByteBuf implements ITileByteBuf {

    public static void register() {
        CapabilityManager.INSTANCE.register(ITileByteBuf.class, new DefaultStorageHelper.NullStorage<>(), DefaultTileByteBuf::new);
    }

    @Override
    public void writePacket(ByteBuf buf, ByteBufType type, Object... obj) {

    }

    @Override
    public void readPacket(ByteBuf buf, ByteBufType type) {

    }
}
