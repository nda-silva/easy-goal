package com.skilltargets;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.FontTypeFace;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;

/**
 * Adds target lines (and room for a progress bar) to the game's own skill tooltip,
 * instead of drawing a second tooltip on top of it.
 */
@Singleton
class SkillTooltipExtender
{
	/**
	 * Height reserved under the text for the progress bar row.
	 */
	static final int BAR_ROW_HEIGHT = 24;
	private static final int FALLBACK_LINE_HEIGHT = 12;
	/**
	 * Minimum space between a label and its value when widening the tooltip.
	 */
	private static final int COLUMN_GAP = 4;
	/**
	 * Safety cap: the longest possible row ("XP to target: 200,000,000") only needs a few digits more.
	 */
	private static final int MAX_WIDTH_GROWTH = 40;
	private static final String MARKER = "Target: ";

	private final Client client;

	/**
	 * The tooltip text widget that was extended this frame, or null. Read by the overlay to place the bar.
	 */
	@Getter
	private Widget extendedText;
	@Getter
	private Skill extendedSkill;
	@Getter
	private SkillTarget extendedTarget;
	/**
	 * Where the extended tooltip box is on screen this frame. Worked out from the values just set, so
	 * unlike the widgets' laid-out bounds it doesn't lag a frame behind when the game moves the tooltip.
	 */
	@Getter
	private Rectangle extendedBounds;

	/**
	 * What this class last applied to the tooltip. The game can reset the tooltip's size or text on
	 * its own (mouse moves, XP changes), so every frame the current values are compared with these and
	 * anything the game reset is applied again.
	 */
	private static final int UNSET = Integer.MIN_VALUE;
	private int appliedWidth = UNSET;
	private int appliedHeight = UNSET;
	private String appliedValueSuffix;
	/**
	 * Where the tooltip was left last frame, and for which skill and mouse position. The game moves
	 * the tooltip when it refreshes it (e.g. on every XP drop), picking a side of the mouse based on
	 * the tooltip's width; while the mouse stays still it's put back so it doesn't jump around.
	 */
	private Skill placedSkill;
	private int placedMouseX;
	private int placedMouseY;
	private int placedX = UNSET;
	private int placedY = UNSET;

	@Inject
	SkillTooltipExtender(Client client)
	{
		this.client = client;
	}

	void reset()
	{
		extendedText = null;
		extendedSkill = null;
		extendedTarget = null;
		extendedBounds = null;
	}

	private void forget()
	{
		appliedWidth = UNSET;
		appliedHeight = UNSET;
		appliedValueSuffix = null;
		placedSkill = null;
		placedX = UNSET;
		placedY = UNSET;
	}

	/**
	 * Called every frame before the client draws, on the client thread.
	 */
	void update(Map<Skill, SkillTarget> targets)
	{
		reset();

		Widget tooltip = client.getWidget(InterfaceID.Stats.TOOLTIP);
		if (tooltip == null || tooltip.isHidden())
		{
			forget();
			return;
		}

		Skill skill = hoveredSkill();
		SkillTarget target = skill == null ? null : targets.get(skill);
		if (target == null)
		{
			forget();
			return;
		}

		List<Widget> parts = new ArrayList<>();
		collect(tooltip, parts);

		// The game draws the tooltip as two text columns: left-aligned labels ("Strength XP:")
		// and right-aligned values ("2,109,752").
		Widget labels = null;
		Widget values = null;
		for (Widget part : parts)
		{
			if (part.getType() != WidgetType.TEXT || part.getText() == null || part.getText().isEmpty())
			{
				continue;
			}
			if (part.getXTextAlignment() == WidgetTextAlignment.RIGHT)
			{
				values = part;
			}
			else if (labels == null)
			{
				labels = part;
			}
		}

		if (labels == null || values == null)
		{
			forget();
			return;
		}

		apply(tooltip, parts, labels, values, skill, target);

		extendedText = labels;
		extendedSkill = skill;
		extendedTarget = target;
		extendedBounds = boxOnCanvas(tooltip);
	}

	private void apply(Widget tooltip, List<Widget> parts, Widget labels, Widget values, Skill skill, SkillTarget target)
	{
		int xp = client.getSkillExperience(skill);
		int targetXp = target.getTargetXp();

		List<String> labelLines = new ArrayList<>();
		List<String> valueLines = new ArrayList<>();
		labelLines.add(MARKER);
		valueLines.add(target.getType() == SkillTarget.Type.LEVEL
			? "Level " + target.getValue()
			: SkillTargetsPlugin.formatNumber(targetXp) + " XP");
		if (target.getType() == SkillTarget.Type.LEVEL)
		{
			labelLines.add("Target XP:");
			valueLines.add(SkillTargetsPlugin.formatNumber(targetXp));
		}
		else
		{
			labelLines.add("Level at target:");
			valueLines.add(String.valueOf(Experience.getLevelForXp(targetXp)));
		}
		labelLines.add("XP to target:");
		valueLines.add(SkillTargetsPlugin.formatNumber(Math.max(0, targetXp - xp)));

		// Text: strip what was added last time (if the game hasn't rewritten it) and add fresh lines.
		String labelSuffix = "<br>" + String.join("<br>", labelLines);
		String valueSuffix = "<br>" + String.join("<br>", valueLines);
		String labelText = labels.getText();
		int cut = labelText.indexOf("<br>" + MARKER);
		String labelBase = cut >= 0 ? labelText.substring(0, cut) : labelText;
		String valueText = values.getText();
		String valueBase = appliedValueSuffix != null && valueText.endsWith(appliedValueSuffix)
			? valueText.substring(0, valueText.length() - appliedValueSuffix.length())
			: valueText;
		if (!labelText.equals(labelBase + labelSuffix))
		{
			labels.setText(labelBase + labelSuffix);
		}
		if (!valueText.equals(valueBase + valueSuffix))
		{
			values.setText(valueBase + valueSuffix);
		}
		appliedValueSuffix = valueSuffix;

		// Height: if it isn't what was set last frame, the game reset it; grow from the game's value.
		if (tooltip.getHeightMode() == WidgetSizeMode.ABSOLUTE && tooltip.getOriginalHeight() != appliedHeight)
		{
			int lineHeight = labels.getLineHeight() > 0 ? labels.getLineHeight() : FALLBACK_LINE_HEIGHT;
			appliedHeight = tooltip.getOriginalHeight() + labelLines.size() * lineHeight + BAR_ROW_HEIGHT;
			tooltip.setOriginalHeight(appliedHeight);
		}

		// Width: same, widening from the game's value only as much as the added rows need.
		if (tooltip.getWidthMode() == WidgetSizeMode.ABSOLUTE && tooltip.getOriginalWidth() != appliedWidth)
		{
			int base = tooltip.getOriginalWidth();
			appliedWidth = base + widthGrowth(tooltip, labels, values, labelLines, valueLines);
			if (appliedWidth != base)
			{
				tooltip.setOriginalWidth(appliedWidth);
			}
		}

		// Same skill and the mouse hasn't moved: keep the tooltip where it was.
		Point mouse = client.getMouseCanvasPosition();
		if (skill == placedSkill && mouse.getX() == placedMouseX && mouse.getY() == placedMouseY && placedX != UNSET)
		{
			tooltip.setOriginalX(placedX);
			tooltip.setOriginalY(placedY);
		}
		keepOnScreen(tooltip);
		placedSkill = skill;
		placedMouseX = mouse.getX();
		placedMouseY = mouse.getY();
		placedX = tooltip.getOriginalX();
		placedY = tooltip.getOriginalY();

		// Lay out the tooltip and all its pieces every frame. When the game moves the tooltip, its
		// background pieces otherwise follow a frame late, cutting off part of the box.
		revalidate(tooltip, parts);
	}

	private static void revalidate(Widget tooltip, List<Widget> parts)
	{
		tooltip.revalidate();
		for (Widget part : parts)
		{
			part.revalidate();
		}
	}

	/**
	 * How much wider the tooltip must be for the added rows (e.g. "XP to target:" with a 9 digit
	 * number) to fit next to each other. Zero when they already fit.
	 */
	private static int widthGrowth(Widget tooltip, Widget labels, Widget values, List<String> addedLabels, List<String> addedValues)
	{
		FontTypeFace labelFont = labels.getFont();
		FontTypeFace valueFont = values.getFont();
		if (labelFont == null || valueFont == null)
		{
			return 0;
		}

		int needed = 0;
		for (int i = 0; i < addedLabels.size(); i++)
		{
			needed = Math.max(needed, labelFont.getTextWidth(addedLabels.get(i)) + COLUMN_GAP + valueFont.getTextWidth(addedValues.get(i)));
		}

		// The text columns are sized relative to the tooltip; the difference is the padding around them.
		// Both laid-out widths come from the same (possibly previous) layout, so the difference holds,
		// but compare against the width the game just set rather than the laid-out one.
		int inset = tooltip.getWidth() - Math.min(labels.getWidth(), values.getWidth());
		return Math.max(0, Math.min(needed + inset - tooltip.getOriginalWidth(), MAX_WIDTH_GROWTH));
	}

	/**
	 * The game places the tooltip for its original size, and depending on where the mouse enters it
	 * can end up partly outside the skills tab, where it gets cut off. Keep it inside the visible area
	 * (the canvas and every container it sits in): the right and bottom edges flush with that area's
	 * edges if they'd overflow, and never past its left or top edge.
	 * Returns true if it was moved.
	 */
	private void keepOnScreen(Widget tooltip)
	{
		Rectangle box = boxOnCanvas(tooltip);
		if (box == null)
		{
			return;
		}

		Rectangle area = new Rectangle(0, 0, client.getCanvasWidth(), client.getCanvasHeight());
		for (Widget container = tooltip.getParent(); container != null; container = container.getParent())
		{
			area = area.intersection(container.getBounds());
		}
		if (area.isEmpty())
		{
			return;
		}

		tooltip.setOriginalX(tooltip.getOriginalX() + shiftInto(box.x, box.width, area.x, area.width));
		tooltip.setOriginalY(tooltip.getOriginalY() + shiftInto(box.y, box.height, area.y, area.height));
	}

	/**
	 * Where the tooltip box is on screen, worked out from its position and size settings rather than
	 * getBounds(): the laid-out bounds only catch up a frame later. Null if it can't be worked out.
	 */
	private static Rectangle boxOnCanvas(Widget tooltip)
	{
		Widget parent = tooltip.getParent();
		if (parent == null
			|| tooltip.getXPositionMode() != WidgetPositionMode.ABSOLUTE_LEFT
			|| tooltip.getYPositionMode() != WidgetPositionMode.ABSOLUTE_TOP
			|| tooltip.getWidthMode() != WidgetSizeMode.ABSOLUTE
			|| tooltip.getHeightMode() != WidgetSizeMode.ABSOLUTE)
		{
			return null;
		}
		Point origin = parent.getCanvasLocation();
		if (origin == null)
		{
			return null;
		}
		Rectangle box = new Rectangle(origin.getX() + tooltip.getOriginalX(), origin.getY() + tooltip.getOriginalY(),
			tooltip.getOriginalWidth(), tooltip.getOriginalHeight());
		return box.isEmpty() ? null : box;
	}

	/**
	 * How far to move a span so it fits inside the area: back from the far edge first, then
	 * forward from the near edge, so the near edge wins when the span is bigger than the area.
	 */
	private static int shiftInto(int start, int size, int areaStart, int areaSize)
	{
		int shift = Math.min(0, (areaStart + areaSize) - (start + size));
		if (start + shift < areaStart)
		{
			shift = areaStart - start;
		}
		return shift;
	}

	private Skill hoveredSkill()
	{
		Point mouse = client.getMouseCanvasPosition();
		for (Skill skill : Skill.values())
		{
			Integer componentId = SkillComponents.componentFor(skill);
			Widget widget = componentId == null ? null : client.getWidget(componentId);
			if (widget != null && !widget.isHidden() && widget.getBounds().contains(mouse.getX(), mouse.getY()))
			{
				return skill;
			}
		}
		return null;
	}

	private static void collect(Widget widget, List<Widget> out)
	{
		addAll(widget.getStaticChildren(), out);
		addAll(widget.getDynamicChildren(), out);
		addAll(widget.getNestedChildren(), out);
	}

	private static void addAll(Widget[] children, List<Widget> out)
	{
		if (children == null)
		{
			return;
		}
		for (Widget child : children)
		{
			if (child != null)
			{
				out.add(child);
				collect(child, out);
			}
		}
	}
}
