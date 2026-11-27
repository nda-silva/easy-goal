package com.skilltargets;

import com.google.inject.Provides;
import java.awt.Color;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Easy Goal",
	description = "Right-click a skill to set an RS3-style level or XP target",
	tags = {"skill", "target", "goal", "xp", "level", "progress", "rs3"}
)
public class SkillTargetsPlugin extends Plugin
{
	static final String CONFIG_GROUP = "skilltargets";
	private static final String TARGET_KEY_PREFIX = "target_";

	private static final String SET_LEVEL_TARGET = "Set level target";
	private static final String SET_XP_TARGET = "Set XP target";
	private static final String CLEAR_TARGET = "Clear target";
	// Same orange the game uses for the skill name in "View <skill> guide".
	private static final Color SKILL_NAME_COLOR = new Color(0xff981f);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private SkillTargetsConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private SkillTargetsOverlay overlay;

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Notifier notifier;

	@Inject
	private SkillTooltipExtender tooltipExtender;

	private final Map<Skill, SkillTarget> targets = new EnumMap<>(Skill.class);
	/**
	 * Last XP seen per skill, so "target reached" only fires when the target is crossed.
	 */
	private final Map<Skill, Integer> lastXp = new EnumMap<>(Skill.class);

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::loadTargets);
		}
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		chatboxPanelManager.close();
		tooltipExtender.reset();
		targets.clear();
		lastXp.clear();
	}

	Map<Skill, SkillTarget> getTargets()
	{
		return Collections.unmodifiableMap(targets);
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		lastXp.clear();
		loadTargets();
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		// Each skill in the skills tab has a "View <skill> guide" option; hook our entries in next to it.
		// The skill name is part of the option text and the target is empty, so identify the skill
		// from the component that was right-clicked.
		if (!event.getOption().startsWith("View")
			|| WidgetUtil.componentToInterface(event.getActionParam1()) != InterfaceID.STATS)
		{
			return;
		}

		Skill fromComponent = SkillComponents.skillFor(event.getActionParam1());
		final Skill skill = fromComponent != null ? fromComponent : skillFromOption(event.getOption());
		if (skill == null)
		{
			log.debug("Unknown skill for menu option '{}' on component {}", event.getOption(), event.getActionParam1());
			return;
		}

		// Entries inserted just below the top entry ("View guide") end up directly under it,
		// so insert in reverse of the order they should appear in.
		String target = ColorUtil.wrapWithColorTag(skill.getName(), SKILL_NAME_COLOR);
		if (targets.containsKey(skill))
		{
			addMenuEntry(CLEAR_TARGET, target, () -> clearTarget(skill, true));
		}
		addMenuEntry(SET_XP_TARGET, target, () -> promptXpTarget(skill));
		addMenuEntry(SET_LEVEL_TARGET, target, () -> promptLevelTarget(skill));
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		if (config.showTooltip() && !targets.isEmpty())
		{
			tooltipExtender.update(targets);
		}
		else
		{
			tooltipExtender.reset();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		int xp = event.getXp();
		Integer previous = lastXp.put(skill, xp);

		// Only announce when the target is crossed: not on login (no previous value) and not
		// on every XP drop after a target that wasn't cleared.
		SkillTarget target = targets.get(skill);
		if (target == null || previous == null || previous >= target.getTargetXp() || xp < target.getTargetXp())
		{
			return;
		}

		String what = describe(target);
		sendChatMessage("Congratulations, you've reached your " + skill.getName() + " target of " + what + "!");
		notifier.notify(config.targetReachedNotification(), "You've reached your " + skill.getName() + " target of " + what + "!");

		if (config.clearOnReach())
		{
			clearTarget(skill, false);
		}
	}

	private void addMenuEntry(String option, String target, Runnable onClick)
	{
		int index = client.getMenu().getMenuEntries().length - 1;
		client.getMenu().createMenuEntry(index)
			.setOption(option)
			.setTarget(target)
			.setType(MenuAction.RUNELITE)
			.onClick(e -> onClick.run());
	}

	private void promptLevelTarget(Skill skill)
	{
		int currentLevel = Experience.getLevelForXp(client.getSkillExperience(skill));
		if (currentLevel >= Experience.MAX_VIRT_LEVEL)
		{
			sendChatMessage("You can't set a level target for " + skill.getName() + " any higher.");
			return;
		}

		chatboxPanelManager.openTextInput("Set a level target for " + skill.getName()
				+ " (" + (currentLevel + 1) + "-" + Experience.MAX_VIRT_LEVEL + "):")
			.charValidator(Character::isDigit)
			.onDone((String input) -> clientThread.invoke(() -> applyLevelTarget(skill, input)))
			.build();
	}

	private void promptXpTarget(Skill skill)
	{
		int currentXp = client.getSkillExperience(skill);
		if (currentXp >= Experience.MAX_SKILL_XP)
		{
			sendChatMessage("You already have the maximum XP in " + skill.getName() + ".");
			return;
		}

		chatboxPanelManager.openTextInput("Set an XP target for " + skill.getName() + " (e.g. 13034431, 2.5m or 500k):")
			.charValidator(c -> Character.isDigit(c) || c == ',' || c == '.' || c == 'k' || c == 'K' || c == 'm' || c == 'M')
			.onDone((String input) -> clientThread.invoke(() -> applyXpTarget(skill, input)))
			.build();
	}

	private void applyLevelTarget(Skill skill, String input)
	{
		int currentXp = client.getSkillExperience(skill);
		int currentLevel = Experience.getLevelForXp(currentXp);
		int level;
		try
		{
			level = Integer.parseInt(input.trim());
		}
		catch (NumberFormatException e)
		{
			return;
		}

		if (level <= currentLevel || level > Experience.MAX_VIRT_LEVEL)
		{
			sendChatMessage("Your " + skill.getName() + " level target must be between "
				+ (currentLevel + 1) + " and " + Experience.MAX_VIRT_LEVEL + ".");
			return;
		}

		setTarget(skill, new SkillTarget(SkillTarget.Type.LEVEL, level, currentXp));
	}

	private void applyXpTarget(Skill skill, String input)
	{
		int currentXp = client.getSkillExperience(skill);
		Integer xp = parseXp(input);
		if (xp == null)
		{
			return;
		}

		if (xp <= currentXp || xp > Experience.MAX_SKILL_XP)
		{
			sendChatMessage("Your " + skill.getName() + " XP target must be between "
				+ formatNumber(currentXp + 1) + " and " + formatNumber(Experience.MAX_SKILL_XP) + ".");
			return;
		}

		setTarget(skill, new SkillTarget(SkillTarget.Type.XP, xp, currentXp));
	}

	private void setTarget(Skill skill, SkillTarget target)
	{
		targets.put(skill, target);
		configManager.setRSProfileConfiguration(CONFIG_GROUP, TARGET_KEY_PREFIX + skill.name(), target.serialize());
		sendChatMessage("Your " + skill.getName() + " target has been set to " + describe(target) + ".");
	}

	private void clearTarget(Skill skill, boolean announce)
	{
		if (targets.remove(skill) == null)
		{
			return;
		}
		configManager.unsetRSProfileConfiguration(CONFIG_GROUP, TARGET_KEY_PREFIX + skill.name());
		if (announce)
		{
			sendChatMessage("Your " + skill.getName() + " target has been cleared.");
		}
	}

	private void loadTargets()
	{
		targets.clear();
		for (Skill skill : Skill.values())
		{
			SkillTarget target = SkillTarget.deserialize(
				configManager.getRSProfileConfiguration(CONFIG_GROUP, TARGET_KEY_PREFIX + skill.name()));
			if (target != null)
			{
				targets.put(skill, target);
			}
		}
	}

	private void sendChatMessage(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append(message)
				.build())
			.build());
	}

	static String describe(SkillTarget target)
	{
		return target.getType() == SkillTarget.Type.LEVEL
			? "level " + target.getValue()
			: formatNumber(target.getValue()) + " XP";
	}

	static String formatNumber(long n)
	{
		return NumberFormat.getIntegerInstance(Locale.US).format(n);
	}

	/**
	 * Gets the skill out of an option like "View &lt;col=ff981f&gt;Strength&lt;/col&gt; guide".
	 */
	private static Skill skillFromOption(String option)
	{
		String name = Text.removeTags(option).replaceFirst("^View ", "").replaceFirst(" guide$", "").trim();
		for (Skill skill : Skill.values())
		{
			if (skill.getName().equalsIgnoreCase(name))
			{
				return skill;
			}
		}
		return null;
	}

	/**
	 * Parses "13034431", "13,034,431", "2.5m" or "500k". Returns null when the input isn't a number.
	 */
	static Integer parseXp(String input)
	{
		String s = input.trim().toLowerCase(Locale.US).replace(",", "");
		if (s.isEmpty())
		{
			return null;
		}

		double multiplier = 1;
		char suffix = s.charAt(s.length() - 1);
		if (suffix == 'k' || suffix == 'm')
		{
			multiplier = suffix == 'k' ? 1_000 : 1_000_000;
			s = s.substring(0, s.length() - 1);
		}

		try
		{
			double value = Double.parseDouble(s) * multiplier;
			return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.round(value);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	@Provides
	SkillTargetsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SkillTargetsConfig.class);
	}
}
