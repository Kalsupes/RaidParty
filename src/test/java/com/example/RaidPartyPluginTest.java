package com.example;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RaidPartyPluginTest
{
	public static void main(String[] args) throws Exception
	{
		try
		{
			ExternalPluginManager.loadBuiltin(RaidPartyPlugin.class);
			RuneLite.main(args);
		}
		catch (Throwable e)
		{
			System.err.println("FATAL ERROR IN TEST MAIN:");
			e.printStackTrace();
			throw e;
		}
	}
}
