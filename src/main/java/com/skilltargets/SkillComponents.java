package com.skilltargets;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;

/**
 * Maps skills to their component in the skills tab, and back.
 */
final class SkillComponents
{
	private static final Map<Skill, Integer> BY_SKILL = new EnumMap<>(Skill.class);
	private static final Map<Integer, Skill> BY_COMPONENT = new HashMap<>();

	static
	{
		put(Skill.ATTACK, InterfaceID.Stats.ATTACK);
		put(Skill.STRENGTH, InterfaceID.Stats.STRENGTH);
		put(Skill.DEFENCE, InterfaceID.Stats.DEFENCE);
		put(Skill.RANGED, InterfaceID.Stats.RANGED);
		put(Skill.PRAYER, InterfaceID.Stats.PRAYER);
		put(Skill.MAGIC, InterfaceID.Stats.MAGIC);
		put(Skill.RUNECRAFT, InterfaceID.Stats.RUNECRAFT);
		put(Skill.CONSTRUCTION, InterfaceID.Stats.CONSTRUCTION);
		put(Skill.HITPOINTS, InterfaceID.Stats.HITPOINTS);
		put(Skill.AGILITY, InterfaceID.Stats.AGILITY);
		put(Skill.HERBLORE, InterfaceID.Stats.HERBLORE);
		put(Skill.THIEVING, InterfaceID.Stats.THIEVING);
		put(Skill.CRAFTING, InterfaceID.Stats.CRAFTING);
		put(Skill.FLETCHING, InterfaceID.Stats.FLETCHING);
		put(Skill.SLAYER, InterfaceID.Stats.SLAYER);
		put(Skill.HUNTER, InterfaceID.Stats.HUNTER);
		put(Skill.MINING, InterfaceID.Stats.MINING);
		put(Skill.SMITHING, InterfaceID.Stats.SMITHING);
		put(Skill.FISHING, InterfaceID.Stats.FISHING);
		put(Skill.COOKING, InterfaceID.Stats.COOKING);
		put(Skill.FIREMAKING, InterfaceID.Stats.FIREMAKING);
		put(Skill.WOODCUTTING, InterfaceID.Stats.WOODCUTTING);
		put(Skill.FARMING, InterfaceID.Stats.FARMING);
		put(Skill.SAILING, InterfaceID.Stats.SAILING);
	}

	private SkillComponents()
	{
	}

	private static void put(Skill skill, int componentId)
	{
		BY_SKILL.put(skill, componentId);
		BY_COMPONENT.put(componentId, skill);
	}

	static Integer componentFor(Skill skill)
	{
		return BY_SKILL.get(skill);
	}

	static Skill skillFor(int componentId)
	{
		return BY_COMPONENT.get(componentId);
	}
}
