package com.skilltargets;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

class SkillTargetsOverlay extends Overlay
{
	private static final int ICON_SIZE = 9;
	private static final int ICON_MARGIN = 1;
	private static final Color ICON_RED = new Color(200, 30, 30);
	private static final Color ICON_WHITE = new Color(240, 240, 240);
	private static final Color ICON_OUTLINE = Color.BLACK;

	private static final int TOOLTIP_PADDING = 4;
	private static final int TOOLTIP_BAR_HEIGHT = 16;
	private static final Color TOOLTIP_BAR_BORDER = Color.BLACK;
	private static final Color TOOLTIP_BAR_EMPTY = new Color(90, 0, 0);

	private final Client client;
	private final SkillTargetsPlugin plugin;
	private final SkillTargetsConfig config;
	private final SkillTooltipExtender tooltipExtender;

	@Inject
	SkillTargetsOverlay(Client client, SkillTargetsPlugin plugin, SkillTargetsConfig config, SkillTooltipExtender tooltipExtender)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.tooltipExtender = tooltipExtender;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Map<Skill, SkillTarget> targets = plugin.getTargets();
		if (targets.isEmpty())
		{
			return null;
		}

		if (config.showTargetIcon())
		{
			// Overlays draw above all widgets, including the game's skill tooltip. Clip the tooltip's
			// area out so the icons look like they're underneath it.
			Shape previousClip = graphics.getClip();
			Rectangle tooltip = tooltipExtender.getExtendedBounds();
			if (tooltip == null)
			{
				tooltip = visibleTooltipBounds();
			}
			if (tooltip != null)
			{
				Area clip = new Area(previousClip != null ? previousClip
					: new Rectangle(0, 0, client.getCanvasWidth(), client.getCanvasHeight()));
				clip.subtract(new Area(tooltip));
				graphics.setClip(clip);
			}

			for (Skill skill : targets.keySet())
			{
				Integer componentId = SkillComponents.componentFor(skill);
				Widget widget = componentId == null ? null : client.getWidget(componentId);
				if (widget != null && !widget.isHidden())
				{
					drawTargetIcon(graphics, widget.getBounds());
				}
			}

			graphics.setClip(previousClip);
		}

		drawTooltipBar(graphics);
		return null;
	}

	/**
	 * The area covered by the game's skill tooltip when it hasn't been extended, or null when it
	 * isn't showing: the union of its visible pieces. The tooltip box itself is left out, because the
	 * game can leave it in place (not hidden) for a while after the tooltip disappears.
	 */
	private Rectangle visibleTooltipBounds()
	{
		Widget tooltip = client.getWidget(InterfaceID.Stats.TOOLTIP);
		if (tooltip == null || tooltip.isHidden())
		{
			return null;
		}

		Rectangle union = null;
		for (Widget[] children : new Widget[][]{tooltip.getStaticChildren(), tooltip.getDynamicChildren()})
		{
			if (children == null)
			{
				continue;
			}
			for (Widget child : children)
			{
				if (child == null || child.isHidden() || child.getWidth() <= 1 || child.getHeight() <= 1)
				{
					continue;
				}
				Rectangle bounds = child.getBounds();
				union = union == null ? new Rectangle(bounds) : union.union(bounds);
			}
		}
		return union;
	}

	/**
	 * A small red and white bullseye in the top-right corner of the skill, marking that it has a target.
	 */
	private static void drawTargetIcon(Graphics2D graphics, Rectangle bounds)
	{
		int x = bounds.x + bounds.width - ICON_SIZE - ICON_MARGIN;
		int y = bounds.y + ICON_MARGIN;

		Object previous = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		graphics.setColor(ICON_RED);
		graphics.fillOval(x, y, ICON_SIZE, ICON_SIZE);
		graphics.setColor(ICON_WHITE);
		graphics.fillOval(x + 2, y + 2, ICON_SIZE - 4, ICON_SIZE - 4);
		graphics.setColor(ICON_RED);
		graphics.fillOval(x + 3, y + 3, ICON_SIZE - 6, ICON_SIZE - 6);
		graphics.setColor(ICON_OUTLINE);
		graphics.drawOval(x, y, ICON_SIZE - 1, ICON_SIZE - 1);

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, previous);
	}

	/**
	 * Draws the progress bar in the row {@link SkillTooltipExtender} reserved at the bottom of the game's tooltip.
	 */
	private void drawTooltipBar(Graphics2D graphics)
	{
		Widget text = tooltipExtender.getExtendedText();
		SkillTarget target = tooltipExtender.getExtendedTarget();
		if (text == null || target == null || text.isHidden())
		{
			return;
		}

		Rectangle boxBounds = tooltipExtender.getExtendedBounds();
		if (boxBounds == null)
		{
			Widget box = text.getParent();
			boxBounds = box != null ? box.getBounds() : text.getBounds();
		}

		double progress = target.getProgress(client.getSkillExperience(tooltipExtender.getExtendedSkill()));
		String percent = (int) Math.floor(progress * 100) + "%";

		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = graphics.getFontMetrics();
		int x = boxBounds.x + TOOLTIP_PADDING;
		int width = boxBounds.width - TOOLTIP_PADDING * 2;
		int rowTop = boxBounds.y + boxBounds.height - SkillTooltipExtender.BAR_ROW_HEIGHT - TOOLTIP_PADDING / 2;
		int y = rowTop + (SkillTooltipExtender.BAR_ROW_HEIGHT - TOOLTIP_BAR_HEIGHT) / 2;
		if (width <= 0)
		{
			return;
		}

		graphics.setColor(TOOLTIP_BAR_EMPTY);
		graphics.fillRect(x, y, width, TOOLTIP_BAR_HEIGHT);
		graphics.setColor(config.tooltipBarColor());
		graphics.fillRect(x, y, (int) Math.round(width * progress), TOOLTIP_BAR_HEIGHT);
		graphics.setColor(TOOLTIP_BAR_BORDER);
		graphics.drawRect(x, y, width - 1, TOOLTIP_BAR_HEIGHT - 1);

		// Center on the glyphs' actual drawn height; the font's ascent includes empty space above digits.
		Rectangle2D glyphs = graphics.getFont().createGlyphVector(graphics.getFontRenderContext(), percent).getVisualBounds();
		int textY = (int) Math.round(y + (TOOLTIP_BAR_HEIGHT - glyphs.getHeight()) / 2.0 - glyphs.getY());
		int textX = x + (width - fm.stringWidth(percent)) / 2;
		graphics.setColor(Color.BLACK);
		graphics.drawString(percent, textX + 1, textY + 1);
		graphics.setColor(Color.WHITE);
		graphics.drawString(percent, textX, textY);
	}
}
