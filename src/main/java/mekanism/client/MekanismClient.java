package mekanism.client;

import java.util.HashMap;
import java.util.Map;

import mekanism.api.MekanismAPI;
import mekanism.api.MekanismAPI.BoxBlacklistEvent;
import mekanism.client.sound.SoundHandler;
import mekanism.common.Mekanism;
import mekanism.common.base.IModule;
import mekanism.common.content.boiler.SynchronizedBoilerData;
import mekanism.common.network.PacketKey.KeyMessage;
import mekanism.common.security.SecurityData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;

public class MekanismClient extends Mekanism
{
	public static Map<String, SecurityData> clientSecurityMap = new HashMap<String, SecurityData>();

	public static long ticksPassed = 0;

	public static void updateKey(KeyBinding key, int type)
	{
		boolean down = Minecraft.getMinecraft().currentScreen == null ? key.getIsKeyPressed() : false;

		if(down != keyMap.has(Minecraft.getMinecraft().thePlayer, type))
		{
			Mekanism.packetHandler.sendToServer(new KeyMessage(type, down));
			keyMap.update(Minecraft.getMinecraft().thePlayer, type, down);
		}
	}

	public static void reset()
	{
		clientSecurityMap.clear();

		ClientTickHandler.tickingSet.clear();

		MekanismAPI.getBoxIgnore().clear();
		MinecraftForge.EVENT_BUS.post(new BoxBlacklistEvent());

		Mekanism.jetpackOn.clear();
		Mekanism.gasmaskOn.clear();
		Mekanism.flamethrowerActive.clear();
		Mekanism.activeVibrators.clear();
		
		SynchronizedBoilerData.clientHotMap.clear();
		
		for(IModule module : Mekanism.modulesLoaded)
		{
			module.resetClient();
		}

		SoundHandler.soundMaps.clear();

		Mekanism.proxy.loadConfiguration();

		Mekanism.logger.info("Reloaded config.");
	}
}
