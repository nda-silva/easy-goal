package com.skilltargets;

import lombok.Value;
import net.runelite.api.Experience;

/**
 * A target for a single skill. Like RS3, a target is either a level or an XP amount.
 * The XP the skill had when the target was set is kept so progress can be shown
 * from that point rather than from zero.
 */
@Value
class SkillTarget
{
	enum Type
	{
		LEVEL,
		XP
	}

	Type type;
	/**
	 * The target level for {@link Type#LEVEL}, or the target XP for {@link Type#XP}.
	 */
	int value;
	int startXp;

	int getTargetXp()
	{
		return type == Type.LEVEL ? Experience.getXpForLevel(value) : value;
	}

	double getProgress(int currentXp)
	{
		int target = getTargetXp();
		int span = target - startXp;
		if (span <= 0)
		{
			return currentXp >= target ? 1.0 : 0.0;
		}
		return Math.max(0.0, Math.min(1.0, (currentXp - startXp) / (double) span));
	}

	String serialize()
	{
		return type.name() + "," + value + "," + startXp;
	}

	static SkillTarget deserialize(String s)
	{
		if (s == null || s.isEmpty())
		{
			return null;
		}
		try
		{
			String[] parts = s.split(",");
			return new SkillTarget(Type.valueOf(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		}
		catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e)
		{
			return null;
		}
	}
}
