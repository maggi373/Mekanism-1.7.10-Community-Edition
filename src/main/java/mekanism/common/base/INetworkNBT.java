package mekanism.common.base;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Used for syncing specific data between client and server
 *
 * @author BloCamLimb
 */
public interface INetworkNBT {

    NBTTagCompound writeNetworkNBT(NBTTagCompound tag, NBTType type);

    void readNetworkNBT(NBTTagCompound tag, NBTType type);
}
