package com.skilltargets;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Notification;

@ConfigGroup(SkillTargetsPlugin.CONFIG_GROUP)
public interface SkillTargetsConfig extends Config
{
	@ConfigItem(
		keyName = "showTargetIcon",
		name = "Show target icon",
		description = "Show a small target icon on skills that have a target",
		position = 1
	)
	default boolean showTargetIcon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTooltip",
		name = "Add target to tooltip",
		description = "Add target details and a progress bar to the game's skill tooltip",
		position = 2
	)
	default boolean showTooltip()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tooltipBarColor",
		name = "Tooltip bar color",
		description = "Color of the progress bar inside the skill tooltip",
		position = 3
	)
	default Color tooltipBarColor()
	{
		return new Color(0, 200, 0);
	}

	@ConfigItem(
		keyName = "targetReachedNotification",
		name = "Notify on target reached",
		description = "Send a notification when a skill target is reached",
		position = 4
	)
	default Notification targetReachedNotification()
	{
		return Notification.ON;
	}

	@ConfigItem(
		keyName = "clearOnReach",
		name = "Clear target when reached",
		description = "Remove the target once it has been reached, like RS3 does",
		position = 5
	)
	default boolean clearOnReach()
	{
		return true;
	}
}
