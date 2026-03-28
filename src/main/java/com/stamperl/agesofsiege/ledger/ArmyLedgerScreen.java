package com.stamperl.agesofsiege.ledger;

import com.stamperl.agesofsiege.defense.DefenderRole;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class ArmyLedgerScreen extends Screen {
	private static final int SCREEN_MARGIN_X = 28;
	private static final int SCREEN_MARGIN_Y = 22;
	private static final int PANEL_GAP = 14;
	private static final int PANEL_HEADER_HEIGHT = 40;
	private static final int DETAIL_PANEL_WIDTH_MIN = 360;
	private static final int DETAIL_PANEL_WIDTH_MAX = 440;
	private static final int MAX_FRAME_WIDTH = 1400;
	private static final int MAX_FRAME_HEIGHT = 900;
	private static final int MIN_FRAME_WIDTH = 760;
	private static final int MIN_FRAME_HEIGHT = 480;
	private static final int STACKED_CONTENT_MAX_WIDTH = 1160;
	private static final float MAP_ZOOM_MIN = 1.0F;
	private static final float MAP_ZOOM_MAX = 4.0F;
	private static final float MAP_ZOOM_STEP = 0.25F;

	private static final int SCRIM_COLOR = 0x90070A0E;
	private static final int FRAME_COLOR = 0xFF12181D;
	private static final int FRAME_BORDER = 0xC02A333B;
	private static final int PANEL_COLOR = 0xFF1A2229;
	private static final int PANEL_HEADER_COLOR = 0xFF202930;
	private static final int PANEL_BORDER = 0xA033404B;
	private static final int CARD_COLOR = 0x202C363F;
	private static final int CARD_BORDER = 0x2436414B;
	private static final int TEXT_PRIMARY = 0xFFEAEFF3;
	private static final int TEXT_SECONDARY = 0xFF99A6B1;
	private static final int TEXT_MUTED = 0xFF76818A;
	private static final int TEXT_WARM = 0xFFD6C49E;
	private static final int MAP_BG = 0xFF20372A;
	private static final int MAP_GRID = 0x183A4753;
	private static final int MARKER_ARCHER = 0xFF5F83A8;
	private static final int MARKER_SOLDIER = 0xFFC7B88F;
	private static final int MARKER_SELECTED = 0xFFAD5548;
	private static final int MARKER_OFFLINE = 0xFF6E7177;
	private static final int MARKER_POST = 0x80FFFFFF;
	private static final int MARKER_BANNER = 0xFFB89B54;

	private static UUID lastSelectedDefenderId;

	private final ArmyLedgerSnapshot snapshot;

	private int selectedIndex = -1;
	private TextFieldWidget nameField;
	private ButtonWidget renameButton;
	private ButtonWidget cycleRoleButton;
	private ButtonWidget locateButton;

	private MapView cachedMapBounds;
	private int[] cachedMapColors = new int[0];
	private int cachedSamplesX = -1;
	private int cachedSamplesZ = -1;
	private long cachedMapTick = Long.MIN_VALUE;
	private float mapZoom = 1.0F;
	private float mapPanX;
	private float mapPanZ;
	private boolean mapDragging;
	private double mapDragLastX;
	private double mapDragLastY;

	private enum LayoutDensity {
		COMPACT,
		NORMAL,
		WIDE
	}

	private enum LayoutMode {
		SIDE_PANEL,
		STACKED
	}

	public ArmyLedgerScreen(ArmyLedgerSnapshot snapshot) {
		super(Text.literal("Army Command Table"));
		this.snapshot = snapshot;
	}

	@Override
	protected void init() {
		super.init();
		selectInitialGuard();

		this.nameField = new TextFieldWidget(this.textRenderer, 0, 0, 120, 20, Text.literal("Guard Name"));
		this.nameField.setMaxLength(32);
		this.addDrawableChild(this.nameField);

		this.renameButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Apply Name"), button -> sendRename())
			.dimensions(0, 0, 90, 20).build());
		this.cycleRoleButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Cycle Role"), button -> sendRoleCycle())
			.dimensions(0, 0, 90, 20).build());
		this.locateButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Locate"), button -> sendLocate())
			.dimensions(0, 0, 90, 20).build());

		refreshNameField();
		syncWidgetLayout();
		refreshControls();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		syncWidgetLayout();

		Layout layout = createLayout();
		context.fill(0, 0, this.width, this.height, SCRIM_COLOR);
		drawFrame(context, layout.frame);
		drawTopBar(context, layout);
		drawMapPanel(context, layout, mouseX, mouseY);
		drawLegend(context, layout.mapLegend);
		drawDetailPanel(context, layout, mouseX, mouseY);

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int markerIndex = findMarkerHit(mouseX, mouseY);
		if (markerIndex >= 0) {
			selectDefender(markerIndex);
			return true;
		}
		if (button == 0) {
			Rect mapBody = createLayout().mapBody;
			if (contains(mapBody, mouseX, mouseY)) {
				mapDragging = true;
				mapDragLastX = mouseX;
				mapDragLastY = mouseY;
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && mapDragging) {
			mapDragging = false;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (!mapDragging || button != 0) {
			return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
		}
		Layout layout = createLayout();
		Rect body = layout.mapBody;
		MapView view = computeMapView(body);
		float worldPerPixelX = (view.maxX - view.minX) / Math.max(1.0F, body.width);
		float worldPerPixelZ = (view.maxZ - view.minZ) / Math.max(1.0F, body.height);
		mapPanX -= (float) ((mouseX - mapDragLastX) * worldPerPixelX);
		mapPanZ -= (float) ((mouseY - mapDragLastY) * worldPerPixelZ);
		mapDragLastX = mouseX;
		mapDragLastY = mouseY;
		invalidateMapCache();
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		Layout layout = createLayout();
		Rect body = layout.mapBody;
		if (!contains(body, mouseX, mouseY) || verticalAmount == 0.0D) {
			return super.mouseScrolled(mouseX, mouseY, verticalAmount);
		}

		MapView currentView = computeMapView(body);
		float normalizedX = MathHelper.clamp((float) ((mouseX - body.x) / Math.max(1.0, body.width)), 0.0F, 1.0F);
		float normalizedZ = MathHelper.clamp((float) ((mouseY - body.y) / Math.max(1.0, body.height)), 0.0F, 1.0F);
		float worldX = MathHelper.lerp(normalizedX, currentView.minX, currentView.maxX);
		float worldZ = MathHelper.lerp(normalizedZ, currentView.minZ, currentView.maxZ);

		float previousZoom = mapZoom;
		mapZoom = MathHelper.clamp(mapZoom + (verticalAmount > 0.0D ? MAP_ZOOM_STEP : -MAP_ZOOM_STEP), MAP_ZOOM_MIN, MAP_ZOOM_MAX);
		if (previousZoom == mapZoom) {
			return true;
		}

		BaseBounds baseBounds = computeBaseBounds();
		float baseCenterX = (baseBounds.minX + baseBounds.maxX) * 0.5F;
		float baseCenterZ = (baseBounds.minZ + baseBounds.maxZ) * 0.5F;
		float aspect = body.width / (float) Math.max(1, body.height);
		float baseWidth = Math.max(baseBounds.maxX - baseBounds.minX, baseBounds.maxZ - baseBounds.minZ * aspect);
		float baseHeight = Math.max(baseBounds.maxZ - baseBounds.minZ, baseWidth / Math.max(0.01F, aspect));
		float paddedWidth = Math.max(18.0F, (baseBounds.maxX - baseBounds.minX) + 10.0F);
		float paddedHeight = Math.max(18.0F, (baseBounds.maxZ - baseBounds.minZ) + 10.0F);
		float viewWidth = Math.max(12.0F, Math.max(paddedWidth, paddedHeight * aspect) / mapZoom);
		float viewHeight = Math.max(12.0F, Math.max(paddedHeight, paddedWidth / Math.max(0.01F, aspect)) / mapZoom);
		float centerX = worldX - ((normalizedX - 0.5F) * viewWidth);
		float centerZ = worldZ - ((normalizedZ - 0.5F) * viewHeight);
		mapPanX = centerX - baseCenterX;
		mapPanZ = centerZ - baseCenterZ;
		invalidateMapCache();
		return true;
	}

	private void drawFrame(DrawContext context, Rect frame) {
		context.fill(frame.x, frame.y, frame.right(), frame.bottom(), FRAME_COLOR);
		context.drawBorder(frame.x, frame.y, frame.width, frame.height, FRAME_BORDER);
	}

	private void drawTopBar(DrawContext context, Layout layout) {
		Rect bar = layout.topBar;
		context.fill(bar.x, bar.y, bar.right(), bar.bottom(), PANEL_COLOR);
		context.drawBorder(bar.x, bar.y, bar.width, bar.height, PANEL_BORDER);

		int inset = layout.density == LayoutDensity.COMPACT ? 10 : 12;
		int gap = layout.density == LayoutDensity.COMPACT ? 8 : 10;
		int maxInnerWidth = layout.mode == LayoutMode.STACKED ? 1080 : bar.width - (inset * 2);
		int innerWidth = Math.min(bar.width - (inset * 2), maxInnerWidth);
		int innerX = bar.x + ((bar.width - innerWidth) / 2);
		int innerY = bar.y + inset;
		int innerHeight = bar.height - (inset * 2);
		int rowHeight = (innerHeight - gap) / 2;

		int settlementWidth = Math.max(220, (int) Math.round(innerWidth * 0.40F));
		Rect settlementCard = new Rect(innerX, innerY, settlementWidth, rowHeight);
		Rect commandCard = new Rect(settlementCard.right() + gap, innerY, innerWidth - settlementWidth - gap, rowHeight);

		int chipWidth = (innerWidth - (gap * 2)) / 3;
		Rect phaseCard = new Rect(innerX, settlementCard.bottom() + gap, chipWidth, rowHeight);
		Rect guardsCard = new Rect(phaseCard.right() + gap, phaseCard.y, chipWidth, rowHeight);
		Rect onPostCard = new Rect(guardsCard.right() + gap, phaseCard.y, innerWidth - chipWidth - chipWidth - gap - gap, rowHeight);

		drawInfoCard(context, settlementCard, "Settlement", safeText(snapshot.ownerName(), "Unclaimed"));
		drawCommandCard(context, commandCard, layout);
		drawInfoCard(context, phaseCard, "Phase", formatPhase(snapshot.siegePhase()));
		drawInfoCard(context, guardsCard, "Guards", Integer.toString(snapshot.defenders().size()));
		drawInfoCard(context, onPostCard, "On Post", Integer.toString(countOnlineDefenders()));
	}

	private void drawCommandCard(DrawContext context, Rect rect, Layout layout) {
		context.fill(rect.x, rect.y, rect.right(), rect.bottom(), CARD_COLOR);
		context.drawBorder(rect.x, rect.y, rect.width, rect.height, CARD_BORDER);
		drawScaledText(context, "Army Command Table", rect.x + 10, rect.y + 8, TEXT_PRIMARY, titleScale(layout.density));
		drawTrimmed(context, "Banner " + snapshot.bannerPos().toShortString(), rect.x + 10, rect.y + 30, rect.width - 20, TEXT_SECONDARY);
	}

	private void drawInfoCard(DrawContext context, Rect rect, String label, String value) {
		context.fill(rect.x, rect.y, rect.right(), rect.bottom(), CARD_COLOR);
		context.drawBorder(rect.x, rect.y, rect.width, rect.height, CARD_BORDER);
		drawScaledText(context, label.toUpperCase(), rect.x + 10, rect.y + 8, TEXT_SECONDARY, metaScale(layoutDensity()));
		drawTrimmed(context, value, rect.x + 10, rect.y + 26, rect.width - 20, TEXT_PRIMARY);
	}

	private void drawMapPanel(DrawContext context, Layout layout, int mouseX, int mouseY) {
		Rect panel = layout.mapPanel;
		Rect body = layout.mapBody;
		context.fill(panel.x, panel.y, panel.right(), panel.bottom(), PANEL_COLOR);
		context.drawBorder(panel.x, panel.y, panel.width, panel.height, PANEL_BORDER);
		context.fill(panel.x, panel.y, panel.right(), panel.y + PANEL_HEADER_HEIGHT, PANEL_HEADER_COLOR);
		context.fill(panel.x + 12, panel.y + PANEL_HEADER_HEIGHT, panel.right() - 12, panel.y + PANEL_HEADER_HEIGHT + 1, CARD_BORDER);
		drawTrimmed(context, "Tactical Map", panel.x + 14, panel.y + 13, 160, TEXT_PRIMARY);
		if (layout.density != LayoutDensity.COMPACT) {
			drawScaledText(context, String.format("Zoom %.2fx  •  Wheel to zoom  •  Drag to pan", mapZoom), panel.right() - 250, panel.y + 15, TEXT_SECONDARY, metaScale(layout.density));
		}

		drawTerrainMap(context, body);
		drawMapGrid(context, body);
		drawBannerAndPosts(context, body);
		drawGuardMarkers(context, body, mouseX, mouseY);
	}

	private void drawTerrainMap(DrawContext context, Rect body) {
		context.fill(body.x, body.y, body.right(), body.bottom(), MAP_BG);
		context.drawBorder(body.x, body.y, body.width, body.height, PANEL_BORDER);

		MapView bounds = computeMapView(body);
		ensureMapCache(body, bounds);
		if (cachedMapColors.length == 0) {
			return;
		}

		for (int sampleZ = 0; sampleZ < cachedSamplesZ; sampleZ++) {
			int y0 = body.y + Math.round(sampleZ * body.height / (float) cachedSamplesZ);
			int y1 = body.y + Math.round((sampleZ + 1) * body.height / (float) cachedSamplesZ);
			for (int sampleX = 0; sampleX < cachedSamplesX; sampleX++) {
				int x0 = body.x + Math.round(sampleX * body.width / (float) cachedSamplesX);
				int x1 = body.x + Math.round((sampleX + 1) * body.width / (float) cachedSamplesX);
				context.fill(x0, y0, x1, y1, cachedMapColors[sampleZ * cachedSamplesX + sampleX]);
			}
		}
	}

	private void drawMapGrid(DrawContext context, Rect body) {
		for (int x = body.x + 40; x < body.right(); x += 48) {
			context.fill(x, body.y, x + 1, body.bottom(), MAP_GRID);
		}
		for (int y = body.y + 40; y < body.bottom(); y += 48) {
			context.fill(body.x, y, body.right(), y + 1, MAP_GRID);
		}
	}

	private void drawBannerAndPosts(DrawContext context, Rect body) {
		MapView bounds = computeMapView(body);
		for (ArmyLedgerSnapshot.DefenderEntry defender : snapshot.defenders()) {
			Point post = worldToMap(defender.homePost(), body, bounds);
			drawDiamond(context, post.x - 6, post.y - 6, 12, MARKER_POST);
		}

		Point banner = worldToMap(snapshot.bannerPos(), body, bounds);
		context.fill(banner.x - 2, banner.y - 8, banner.x + 1, banner.y + 8, MARKER_BANNER);
		context.fill(banner.x + 1, banner.y - 8, banner.x + 10, banner.y - 3, MARKER_BANNER);
	}

	private void drawGuardMarkers(DrawContext context, Rect body, int mouseX, int mouseY) {
		MapView bounds = computeMapView(body);
		List<Marker> markers = buildMarkers(body, bounds);
		for (Marker marker : markers) {
			ArmyLedgerSnapshot.DefenderEntry defender = snapshot.defenders().get(marker.defenderIndex);
			int markerColor = defender.online()
				? (defender.role() == DefenderRole.ARCHER ? MARKER_ARCHER : MARKER_SOLDIER)
				: MARKER_OFFLINE;

			if (marker.defenderIndex == selectedIndex) {
				int ring = marker.radius + 4;
				context.drawBorder(marker.centerX - ring, marker.centerY - ring, ring * 2, ring * 2, TEXT_PRIMARY);
			}
			context.fill(marker.centerX - marker.radius, marker.centerY - marker.radius, marker.centerX + marker.radius, marker.centerY + marker.radius, markerColor);
			context.drawBorder(marker.centerX - marker.radius, marker.centerY - marker.radius, marker.radius * 2, marker.radius * 2, 0x70000000);

			if (isPointNear(mouseX, mouseY, marker.centerX, marker.centerY, marker.radius + 6) || marker.defenderIndex == selectedIndex) {
				int labelX = Math.min(body.right() - 110, marker.centerX + 10);
				int labelY = Math.max(body.y + 8, marker.centerY - 14);
				context.fill(labelX - 4, labelY - 2, labelX + 98, labelY + 12, 0xC0101418);
				context.drawBorder(labelX - 4, labelY - 2, 102, 14, PANEL_BORDER);
				drawTrimmed(context, defender.name(), labelX, labelY, 92, TEXT_PRIMARY);
			}
		}
	}

	private void drawDetailPanel(DrawContext context, Layout layout, int mouseX, int mouseY) {
		Rect panel = layout.detailPanel;
		Rect body = layout.detailBody;
		context.fill(panel.x, panel.y, panel.right(), panel.bottom(), PANEL_COLOR);
		context.drawBorder(panel.x, panel.y, panel.width, panel.height, PANEL_BORDER);
		context.fill(panel.x, panel.y, panel.right(), panel.y + PANEL_HEADER_HEIGHT, PANEL_HEADER_COLOR);
		context.fill(panel.x + 12, panel.y + PANEL_HEADER_HEIGHT, panel.right() - 12, panel.y + PANEL_HEADER_HEIGHT + 1, CARD_BORDER);
		drawTrimmed(context, "Guard Detail", panel.x + 14, panel.y + 13, 160, TEXT_PRIMARY);
		if (layout.density != LayoutDensity.COMPACT) {
			drawScaledText(context, "Selected from map", panel.right() - 110, panel.y + 15, TEXT_SECONDARY, metaScale(layout.density));
		}

		ArmyLedgerSnapshot.DefenderEntry defender = getSelectedDefender();
		if (defender == null) {
			context.fill(body.x, body.y, body.right(), body.bottom(), CARD_COLOR);
			context.drawBorder(body.x, body.y, body.width, body.height, CARD_BORDER);
			drawTrimmed(context, "Select a guard on the tactical map.", body.x + 16, body.y + 24, body.width - 32, TEXT_SECONDARY);
			return;
		}

		DetailLayout detailLayout = createDetailLayout(layout);
		Rect titleCard = detailLayout.titleCard;
		Rect portraitCard = detailLayout.portraitCard;
		Rect statsCard = detailLayout.statsCard;
		Rect actionsCard = detailLayout.actionsCard;

		drawCard(context, titleCard);
		drawCard(context, portraitCard);
		drawCard(context, statsCard);
		drawCard(context, actionsCard);

		drawTrimmed(context, defender.name(), titleCard.x + 12, titleCard.y + 12, titleCard.width - 24, TEXT_PRIMARY);
		drawScaledText(context, defender.online() ? "On Post" : "Missing", titleCard.x + 12, titleCard.y + (layout.density == LayoutDensity.WIDE ? 38 : 34), defender.online() ? TEXT_WARM : TEXT_MUTED, metaScale(layout.density));

		Entity entity = this.client == null || this.client.world == null ? null : this.client.world.getEntityById(defender.entityId());
		if (entity instanceof LivingEntity living) {
			int entityScale = computeEntityScale(layout, portraitCard);
			InventoryScreen.drawEntity(context, portraitCard.centerX(), portraitCard.bottom() - 10, entityScale, (float) (portraitCard.centerX() - mouseX), (float) (portraitCard.centerY() + 10 - mouseY), living);
		} else {
			drawTrimmed(context, "Portrait unavailable", portraitCard.x + 20, portraitCard.centerY() - 4, portraitCard.width - 40, TEXT_MUTED);
		}

		drawStatsCard(context, layout, statsCard, defender);

		drawScaledText(context, "Rename Guard", actionsCard.x + 12, actionsCard.y + 12, TEXT_SECONDARY, metaScale(layout.density));
	}

	private void drawCard(DrawContext context, Rect rect) {
		context.fill(rect.x, rect.y, rect.right(), rect.bottom(), CARD_COLOR);
		context.drawBorder(rect.x, rect.y, rect.width, rect.height, CARD_BORDER);
	}

	private int drawStatRow(DrawContext context, int x, int y, String label, String value, int width) {
		LayoutDensity density = layoutDensity();
		int valueOffset = density == LayoutDensity.WIDE ? 92 : 86;
		drawScaledText(context, label.toUpperCase(), x, y + 1, TEXT_SECONDARY, metaScale(density));
		drawTrimmed(context, value, x + valueOffset, y, width - valueOffset, TEXT_PRIMARY);
		return y + statRowHeight(density);
	}

	private void drawStatsCard(DrawContext context, Layout layout, Rect statsCard, ArmyLedgerSnapshot.DefenderEntry defender) {
		if (layout.mode == LayoutMode.STACKED || statsCard.height < 118) {
			int gap = 12;
			int columnWidth = (statsCard.width - 24 - gap) / 2;
			int leftX = statsCard.x + 12;
			int rightX = leftX + columnWidth + gap;
			int rowY = statsCard.y + 12;
			rowY = drawStatRow(context, leftX, rowY, "Role", defender.role().displayName(), columnWidth);
			rowY = drawStatRow(context, leftX, rowY, "Health", MathHelper.ceil(defender.health()) + "/" + MathHelper.ceil(defender.maxHealth()), columnWidth);
			drawStatRow(context, leftX, rowY, "Armor", defender.armorLabel(), columnWidth);

			int rightY = statsCard.y + 12;
			rightY = drawStatRow(context, rightX, rightY, "Attack", Integer.toString(defender.attackPower()), columnWidth);
			rightY = drawStatRow(context, rightX, rightY, "Post", defender.homePost().toShortString(), columnWidth);
			drawStatRow(context, rightX, rightY, "Current", defender.currentPos().toShortString(), columnWidth);
			return;
		}

		int rowY = statsCard.y + 12;
		rowY = drawStatRow(context, statsCard.x + 12, rowY, "Role", defender.role().displayName(), statsCard.width - 24);
		rowY = drawStatRow(context, statsCard.x + 12, rowY, "Health", MathHelper.ceil(defender.health()) + "/" + MathHelper.ceil(defender.maxHealth()), statsCard.width - 24);
		rowY = drawStatRow(context, statsCard.x + 12, rowY, "Armor", defender.armorLabel(), statsCard.width - 24);
		rowY = drawStatRow(context, statsCard.x + 12, rowY, "Attack", Integer.toString(defender.attackPower()), statsCard.width - 24);
		rowY = drawStatRow(context, statsCard.x + 12, rowY, "Post", defender.homePost().toShortString(), statsCard.width - 24);
		drawStatRow(context, statsCard.x + 12, rowY, "Current", defender.currentPos().toShortString(), statsCard.width - 24);
	}

	private void drawLegend(DrawContext context, Rect legend) {
		context.fill(legend.x, legend.y, legend.right(), legend.bottom(), PANEL_COLOR);
		context.drawBorder(legend.x, legend.y, legend.width, legend.height, PANEL_BORDER);

		int x = legend.x + 12;
		int chipY = legend.y + (layoutDensity() == LayoutDensity.COMPACT ? 7 : 8);
		boolean shortLabels = legend.width < 620;
		x = drawLegendChip(context, x, chipY, MARKER_ARCHER, false, shortLabels ? "Arc" : "Archer");
		x = drawLegendChip(context, x + 8, chipY, MARKER_SOLDIER, false, shortLabels ? "Sol" : "Soldier");
		x = drawLegendChip(context, x + 8, chipY, MARKER_SELECTED, false, shortLabels ? "Sel" : "Selected");
		x = drawLegendChip(context, x + 8, chipY, MARKER_BANNER, false, shortLabels ? "Ban" : "Banner");
		drawLegendChip(context, x + 8, chipY, MARKER_POST, true, "Post");
	}

	private int drawLegendChip(DrawContext context, int x, int y, int color, boolean diamond, String label) {
		int width = 52 + this.textRenderer.getWidth(label);
		context.fill(x, y - 4, x + width, y + 14, CARD_COLOR);
		context.drawBorder(x, y - 4, width, 18, CARD_BORDER);
		if (diamond) {
			drawDiamond(context, x + 10, y - 1, 10, color);
		} else {
			context.fill(x + 8, y - 1, x + 18, y + 9, color);
		}
		drawScaledText(context, label, x + 24, y, TEXT_SECONDARY, metaScale(layoutDensity()));
		return x + width;
	}

	private void drawDiamond(DrawContext context, int x, int y, int size, int color) {
		int half = size / 2;
		for (int row = 0; row < size; row++) {
			int distance = Math.abs(row - half);
			int span = half - distance;
			context.fill(x + half - span, y + row, x + half + span + 1, y + row + 1, color);
		}
	}

	private int findMarkerHit(double mouseX, double mouseY) {
		Layout layout = createLayout();
		List<Marker> markers = buildMarkers(layout.mapBody, computeMapView(layout.mapBody));
		return markers.stream()
			.filter(marker -> isPointNear(mouseX, mouseY, marker.centerX, marker.centerY, marker.radius + 4))
			.min(Comparator.comparingDouble(marker -> distanceSquared(mouseX, mouseY, marker.centerX, marker.centerY)))
			.map(marker -> marker.defenderIndex)
			.orElse(-1);
	}

	private boolean isPointNear(double mouseX, double mouseY, int centerX, int centerY, int radius) {
		return distanceSquared(mouseX, mouseY, centerX, centerY) <= radius * radius;
	}

	private double distanceSquared(double mouseX, double mouseY, int centerX, int centerY) {
		double dx = mouseX - centerX;
		double dy = mouseY - centerY;
		return dx * dx + dy * dy;
	}

	private void syncWidgetLayout() {
		if (nameField == null) {
			return;
		}
		Layout layout = createLayout();
		Rect actions = createDetailLayout(layout).actionsCard;

		int fieldX = actions.x + 12;
		int fieldY = actions.y + 28;
		int fieldWidth = actions.width - 24;
		nameField.setX(fieldX);
		nameField.setY(fieldY);
		nameField.setWidth(fieldWidth);

		int buttonY = fieldY + 30;
		int gap = 8;
		renameButton.setX(fieldX);
		renameButton.setY(buttonY);
		renameButton.setWidth(fieldWidth);
		cycleRoleButton.setX(fieldX);
		cycleRoleButton.setY(buttonY + 24);
		cycleRoleButton.setWidth(fieldWidth);
		locateButton.setX(fieldX);
		locateButton.setY(buttonY + 48);
		locateButton.setWidth(fieldWidth);
	}

	private DetailLayout createDetailLayout(Layout layout) {
		Rect body = layout.detailBody;
		int gap = layout.density == LayoutDensity.COMPACT ? 8 : 10;
		if (layout.mode == LayoutMode.STACKED) {
			int portraitWidth = MathHelper.clamp(body.width / 4, 150, 230);
			int rightWidth = Math.max(240, body.width - portraitWidth - gap);
			int titleHeight = 52;
			int actionsHeight = computeActionsHeight(layout, rightWidth);
			int statsHeight = Math.max(66, body.height - titleHeight - actionsHeight - (gap * 2));
			Rect portraitCard = new Rect(body.x, body.y, portraitWidth, body.height);
			Rect titleCard = new Rect(body.x + portraitWidth + gap, body.y, rightWidth, titleHeight);
			Rect statsCard = new Rect(titleCard.x, titleCard.bottom() + gap, rightWidth, statsHeight);
			Rect actionsCard = new Rect(titleCard.x, statsCard.bottom() + gap, rightWidth, Math.max(68, body.bottom() - statsCard.bottom() - gap));
			return new DetailLayout(titleCard, portraitCard, statsCard, actionsCard);
		}
		int titleHeight = layout.density == LayoutDensity.WIDE ? 66 : 58;
		int actionsHeight = computeActionsHeight(layout, body.width);
		int portraitHeight = MathHelper.clamp((body.height - titleHeight - actionsHeight - (gap * 3)) / 2, 104, layout.density == LayoutDensity.WIDE ? 176 : 150);
		Rect titleCard = new Rect(body.x, body.y, body.width, titleHeight);
		Rect portraitCard = new Rect(body.x, titleCard.bottom() + gap, body.width, portraitHeight);
		int statsHeight = Math.max(110, body.bottom() - portraitCard.bottom() - gap - actionsHeight - gap);
		Rect statsCard = new Rect(body.x, portraitCard.bottom() + gap, body.width, statsHeight);
		Rect actionsCard = new Rect(body.x, statsCard.bottom() + gap, body.width, Math.max(84, body.bottom() - (statsCard.bottom() + gap)));
		return new DetailLayout(titleCard, portraitCard, statsCard, actionsCard);
	}

	private Layout createLayout() {
		LayoutDensity density = layoutDensity();
		int marginX = Math.max(SCREEN_MARGIN_X, this.width / 32);
		int marginY = Math.max(SCREEN_MARGIN_Y, this.height / 28);
		int availableWidth = this.width - (marginX * 2);
		int availableHeight = this.height - (marginY * 2);
		int frameWidth = MathHelper.clamp(availableWidth, Math.min(MIN_FRAME_WIDTH, availableWidth), Math.min(MAX_FRAME_WIDTH, availableWidth));
		int frameHeight = MathHelper.clamp(availableHeight, Math.min(MIN_FRAME_HEIGHT, availableHeight), Math.min(MAX_FRAME_HEIGHT, availableHeight));
		int frameX = (this.width - frameWidth) / 2;
		int frameY = (this.height - frameHeight) / 2;
		Rect frame = new Rect(frameX, frameY, frameWidth, frameHeight);
		int outerInset = density == LayoutDensity.COMPACT ? 10 : 12;
		int topBarHeight = density == LayoutDensity.WIDE ? 120 : (density == LayoutDensity.COMPACT ? 104 : 112);
		Rect topBar = new Rect(frame.x + outerInset, frame.y + outerInset, frame.width - (outerInset * 2), topBarHeight);
		int mainY = topBar.bottom() + PANEL_GAP;
		int mainHeight = frame.bottom() - outerInset - mainY;
		LayoutMode mode = chooseLayoutMode(frame, density, mainHeight);
		Rect mapPanel;
		Rect detailPanel;
		if (mode == LayoutMode.STACKED) {
			int splitGap = density == LayoutDensity.COMPACT ? 10 : 12;
			int detailMinHeight = density == LayoutDensity.COMPACT ? 170 : 220;
			int mapHeight = Math.max(220, Math.min((int) Math.round(mainHeight * 0.58F), mainHeight - detailMinHeight - splitGap));
			int detailHeight = Math.max(detailMinHeight, mainHeight - mapHeight - splitGap);
			int contentWidth = Math.min(frame.width - (outerInset * 2), STACKED_CONTENT_MAX_WIDTH);
			int contentX = frame.x + ((frame.width - contentWidth) / 2);
			mapPanel = new Rect(contentX, mainY, contentWidth, mapHeight);
			detailPanel = new Rect(contentX, mapPanel.bottom() + splitGap, contentWidth, detailHeight);
		} else {
			int detailWidth = density == LayoutDensity.WIDE
				? MathHelper.clamp(frame.width / 3, 400, 480)
				: MathHelper.clamp(frame.width / 4, DETAIL_PANEL_WIDTH_MIN, DETAIL_PANEL_WIDTH_MAX);
			int mapWidth = frame.width - detailWidth - PANEL_GAP - (outerInset * 2);
			mapPanel = new Rect(frame.x + outerInset, mainY, mapWidth, mainHeight);
			detailPanel = new Rect(mapPanel.right() + PANEL_GAP, mainY, detailWidth, mainHeight);
		}
		int mapLegendHeight = density == LayoutDensity.COMPACT ? 28 : 32;
		Rect mapLegend = new Rect(mapPanel.x + 14, mapPanel.bottom() - mapLegendHeight - 10, mapPanel.width - 28, mapLegendHeight);
		Rect mapBody = new Rect(mapPanel.x + 14, mapPanel.y + PANEL_HEADER_HEIGHT + 10, mapPanel.width - 28, mapPanel.height - PANEL_HEADER_HEIGHT - mapLegendHeight - 24);
		Rect detailBody = new Rect(detailPanel.x + 12, detailPanel.y + PANEL_HEADER_HEIGHT + 10, detailPanel.width - 24, detailPanel.height - PANEL_HEADER_HEIGHT - 22);
		return new Layout(frame, topBar, mapPanel, detailPanel, mapLegend, mapBody, detailBody, density, mode);
	}

	private void refreshControls() {
		ArmyLedgerSnapshot.DefenderEntry defender = getSelectedDefender();
		boolean hasSelection = defender != null;
		nameField.setVisible(true);
		nameField.setEditable(hasSelection);
		renameButton.visible = true;
		renameButton.active = hasSelection;
		cycleRoleButton.visible = true;
		cycleRoleButton.active = hasSelection;
		locateButton.visible = true;
		locateButton.active = hasSelection;
	}

	private void refreshNameField() {
		if (nameField == null) {
			return;
		}
		ArmyLedgerSnapshot.DefenderEntry defender = getSelectedDefender();
		nameField.setText(defender == null ? "" : defender.name());
	}

	private void selectInitialGuard() {
		if (snapshot.defenders().isEmpty()) {
			selectedIndex = -1;
			return;
		}
		if (lastSelectedDefenderId != null) {
			for (int i = 0; i < snapshot.defenders().size(); i++) {
				if (snapshot.defenders().get(i).entityUuid().equals(lastSelectedDefenderId)) {
					selectedIndex = i;
					return;
				}
			}
		}
		selectedIndex = 0;
		lastSelectedDefenderId = snapshot.defenders().get(0).entityUuid();
	}

	private void selectDefender(int index) {
		selectedIndex = MathHelper.clamp(index, 0, snapshot.defenders().size() - 1);
		lastSelectedDefenderId = snapshot.defenders().get(selectedIndex).entityUuid();
		refreshNameField();
		refreshControls();
	}

	private ArmyLedgerSnapshot.DefenderEntry getSelectedDefender() {
		if (snapshot.defenders().isEmpty() || selectedIndex < 0) {
			return null;
		}
		selectedIndex = MathHelper.clamp(selectedIndex, 0, snapshot.defenders().size() - 1);
		return snapshot.defenders().get(selectedIndex);
	}

	private int countOnlineDefenders() {
		int count = 0;
		for (ArmyLedgerSnapshot.DefenderEntry defender : snapshot.defenders()) {
			if (defender.online()) {
				count++;
			}
		}
		return count;
	}

	private List<Marker> buildMarkers(Rect body, MapView bounds) {
		List<Marker> markers = new ArrayList<>();
		for (int i = 0; i < snapshot.defenders().size(); i++) {
			ArmyLedgerSnapshot.DefenderEntry defender = snapshot.defenders().get(i);
			BlockPos pos = defender.online() ? defender.currentPos() : defender.homePost();
			Point point = worldToMap(pos, body, bounds);
			markers.add(new Marker(i, point.x, point.y, 6));
		}
		return markers;
	}

	private Point worldToMap(BlockPos pos, Rect body, MapView bounds) {
		float normalizedX = (pos.getX() - bounds.minX) / (float) Math.max(1, bounds.maxX - bounds.minX);
		float normalizedZ = (pos.getZ() - bounds.minZ) / (float) Math.max(1, bounds.maxZ - bounds.minZ);
		int x = body.x + Math.round(normalizedX * body.width);
		int y = body.y + Math.round(normalizedZ * body.height);
		return new Point(MathHelper.clamp(x, body.x + 6, body.right() - 6), MathHelper.clamp(y, body.y + 6, body.bottom() - 6));
	}

	private BaseBounds computeBaseBounds() {
		int minX = snapshot.bannerPos().getX();
		int maxX = snapshot.bannerPos().getX();
		int minZ = snapshot.bannerPos().getZ();
		int maxZ = snapshot.bannerPos().getZ();

		for (ArmyLedgerSnapshot.DefenderEntry defender : snapshot.defenders()) {
			minX = Math.min(minX, Math.min(defender.homePost().getX(), defender.currentPos().getX()));
			maxX = Math.max(maxX, Math.max(defender.homePost().getX(), defender.currentPos().getX()));
			minZ = Math.min(minZ, Math.min(defender.homePost().getZ(), defender.currentPos().getZ()));
			maxZ = Math.max(maxZ, Math.max(defender.homePost().getZ(), defender.currentPos().getZ()));
		}

		int padding = 5;
		minX -= padding;
		maxX += padding;
		minZ -= padding;
		maxZ += padding;

		int minSpan = 18;
		if (maxX - minX < minSpan) {
			int extra = (minSpan - (maxX - minX)) / 2 + 1;
			minX -= extra;
			maxX += extra;
		}
		if (maxZ - minZ < minSpan) {
			int extra = (minSpan - (maxZ - minZ)) / 2 + 1;
			minZ -= extra;
			maxZ += extra;
		}
		return new BaseBounds(minX, maxX, minZ, maxZ);
	}

	private MapView computeMapView(Rect body) {
		BaseBounds baseBounds = computeBaseBounds();
		float aspect = body.width / (float) Math.max(1, body.height);
		float paddedWidth = Math.max(18.0F, (baseBounds.maxX - baseBounds.minX) + 10.0F);
		float paddedHeight = Math.max(18.0F, (baseBounds.maxZ - baseBounds.minZ) + 10.0F);
		float baseWidth = Math.max(paddedWidth, paddedHeight * aspect);
		float baseHeight = Math.max(paddedHeight, baseWidth / Math.max(0.01F, aspect));
		float centerX = ((baseBounds.minX + baseBounds.maxX) * 0.5F) + mapPanX;
		float centerZ = ((baseBounds.minZ + baseBounds.maxZ) * 0.5F) + mapPanZ;
		float viewWidth = Math.max(12.0F, baseWidth / mapZoom);
		float viewHeight = Math.max(12.0F, baseHeight / mapZoom);
		return new MapView(centerX - (viewWidth * 0.5F), centerX + (viewWidth * 0.5F), centerZ - (viewHeight * 0.5F), centerZ + (viewHeight * 0.5F));
	}

	private void ensureMapCache(Rect body, MapView bounds) {
		ClientWorld world = this.client == null ? null : this.client.world;
		int samplesX = MathHelper.clamp(Math.round((body.width / 10.0F) * MathHelper.clamp(mapZoom, 1.0F, 3.0F)), 28, 140);
		int samplesZ = MathHelper.clamp(Math.round((body.height / 10.0F) * MathHelper.clamp(mapZoom, 1.0F, 3.0F)), 20, 110);
		long tick = world == null ? -1L : world.getTime() / 10L;
		if (cachedMapBounds != null && cachedMapBounds.equals(bounds) && cachedSamplesX == samplesX && cachedSamplesZ == samplesZ && cachedMapTick == tick) {
			return;
		}

		cachedMapBounds = bounds;
		cachedSamplesX = samplesX;
		cachedSamplesZ = samplesZ;
		cachedMapTick = tick;
		cachedMapColors = new int[samplesX * samplesZ];

		for (int sampleZ = 0; sampleZ < samplesZ; sampleZ++) {
			float zProgress = (sampleZ + 0.5F) / samplesZ;
			int worldZ = MathHelper.floor(MathHelper.lerp(zProgress, bounds.minZ, bounds.maxZ));
			for (int sampleX = 0; sampleX < samplesX; sampleX++) {
				float xProgress = (sampleX + 0.5F) / samplesX;
				int worldX = MathHelper.floor(MathHelper.lerp(xProgress, bounds.minX, bounds.maxX));
				cachedMapColors[sampleZ * samplesX + sampleX] = sampleMapColor(world, worldX, worldZ, snapshot.bannerPos().getY(), bounds);
			}
		}
	}

	private int sampleMapColor(ClientWorld world, int x, int z, int fallbackY, MapView bounds) {
		if (world == null) {
			return 0xFF2B3C2F;
		}
		int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
		int sampleY = Math.max(world.getBottomY(), topY > world.getBottomY() ? topY : fallbackY);
		BlockPos samplePos = new BlockPos(x, sampleY, z);
		BlockState state = world.getBlockState(samplePos);
		MapColor mapColor = state.getMapColor(world, samplePos);
		int baseColor = mapColor == MapColor.CLEAR ? fallbackBlockColor(state) : mapColor.color | 0xFF000000;

		int northY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z - 1) - 1;
		int southY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z + 1) - 1;
		int eastY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x + 1, z) - 1;
		int westY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x - 1, z) - 1;
		int slope = (southY - northY) + (eastY - westY);
		float shade = 1.0F + MathHelper.clamp(slope * 0.05F, -0.16F, 0.16F);
		float distance = (Math.abs(x - snapshot.bannerPos().getX()) + Math.abs(z - snapshot.bannerPos().getZ()))
			/ (float) Math.max(1, (bounds.maxX - bounds.minX) + (bounds.maxZ - bounds.minZ));
		float focus = 1.0F - (distance * 0.06F);
		return shadeColor(baseColor, shade * focus);
	}

	private int fallbackBlockColor(BlockState state) {
		String id = String.valueOf(Registries.BLOCK.getId(state.getBlock()));
		if (id.contains("water")) {
			return 0xFF6E93B7;
		}
		if (id.contains("lava")) {
			return 0xFFB35A2E;
		}
		if (id.contains("sand") || id.contains("gravel") || id.contains("path")) {
			return 0xFFD4BE8C;
		}
		if (id.contains("log") || id.contains("planks") || id.contains("wood")) {
			return 0xFF8A6A46;
		}
		if (id.contains("brick") || id.contains("stone") || id.contains("cobblestone") || id.contains("deepslate")) {
			return 0xFF8E8A84;
		}
		if (id.contains("leaves") || id.contains("moss")) {
			return 0xFF5E7F51;
		}
		if (id.contains("dirt") || id.contains("mud") || id.contains("farmland")) {
			return 0xFF8D6A48;
		}
		if (id.contains("grass")) {
			return 0xFF6E8B4A;
		}
		return 0xFFB8AA8E;
	}

	private int shadeColor(int color, float factor) {
		int alpha = (color >>> 24) & 0xFF;
		int red = (color >>> 16) & 0xFF;
		int green = (color >>> 8) & 0xFF;
		int blue = color & 0xFF;
		red = MathHelper.clamp(Math.round(red * factor), 0, 255);
		green = MathHelper.clamp(Math.round(green * factor), 0, 255);
		blue = MathHelper.clamp(Math.round(blue * factor), 0, 255);
		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}

	private void invalidateMapCache() {
		cachedMapBounds = null;
		cachedSamplesX = -1;
		cachedSamplesZ = -1;
		cachedMapTick = Long.MIN_VALUE;
	}

	private boolean contains(Rect rect, double mouseX, double mouseY) {
		return mouseX >= rect.x && mouseX <= rect.right() && mouseY >= rect.y && mouseY <= rect.bottom();
	}

	private void sendRename() {
		ArmyLedgerSnapshot.DefenderEntry defender = getSelectedDefender();
		if (defender == null) {
			return;
		}
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeUuid(defender.entityUuid());
		buf.writeString(nameField.getText(), 64);
		ClientPlayNetworking.send(ArmyLedgerService.RENAME_PACKET, buf);
	}

	private void sendRoleCycle() {
		ArmyLedgerSnapshot.DefenderEntry defender = getSelectedDefender();
		if (defender == null) {
			return;
		}
		DefenderRole nextRole = defender.role() == DefenderRole.ARCHER ? DefenderRole.SOLDIER : DefenderRole.ARCHER;
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeUuid(defender.entityUuid());
		buf.writeString(nextRole.id(), 32);
		ClientPlayNetworking.send(ArmyLedgerService.ROLE_PACKET, buf);
	}

	private void sendLocate() {
		ArmyLedgerSnapshot.DefenderEntry defender = getSelectedDefender();
		if (defender == null) {
			return;
		}
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeUuid(defender.entityUuid());
		ClientPlayNetworking.send(ArmyLedgerService.LOCATE_PACKET, buf);
	}

	private void drawTrimmed(DrawContext context, String text, int x, int y, int maxWidth, int color) {
		String safe = safeText(text, "");
		float scale = bodyScale(layoutDensity());
		int trimWidth = Math.max(1, (int) (maxWidth / scale));
		OrderedText trimmed = Language.getInstance().reorder(this.textRenderer.trimToWidth(Text.literal(safe), trimWidth));
		drawScaledOrderedText(context, trimmed, x, y, color, scale);
	}

	private void drawScaledText(DrawContext context, String text, int x, int y, int color, float scale) {
		context.getMatrices().push();
		context.getMatrices().scale(scale, scale, 1.0F);
		context.drawText(this.textRenderer, Text.literal(text), Math.round(x / scale), Math.round(y / scale), color, false);
		context.getMatrices().pop();
	}

	private void drawScaledOrderedText(DrawContext context, OrderedText text, int x, int y, int color, float scale) {
		context.getMatrices().push();
		context.getMatrices().scale(scale, scale, 1.0F);
		context.drawText(this.textRenderer, text, Math.round(x / scale), Math.round(y / scale), color, false);
		context.getMatrices().pop();
	}

	private String formatPhase(String rawPhase) {
		if (rawPhase == null || rawPhase.isBlank()) {
			return "At Peace";
		}
		String[] words = rawPhase.replace('_', ' ').split(" ");
		StringBuilder builder = new StringBuilder();
		for (String word : words) {
			if (word.isBlank()) {
				continue;
			}
			if (!builder.isEmpty()) {
				builder.append(' ');
			}
			builder.append(Character.toUpperCase(word.charAt(0)));
			if (word.length() > 1) {
				builder.append(word.substring(1));
			}
		}
		return builder.toString();
	}

	private String safeText(String value, String fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}
		return value;
	}

	private LayoutDensity layoutDensity() {
		int guiScale = getGuiScaleSetting();
		if (this.width >= 1500 || guiScale == 1 || guiScale == 2) {
			return LayoutDensity.WIDE;
		}
		if (this.width <= 980 || this.height <= 560 || guiScale >= 4) {
			return LayoutDensity.COMPACT;
		}
		return LayoutDensity.NORMAL;
	}

	private LayoutMode chooseLayoutMode(Rect frame, LayoutDensity density, int mainHeight) {
		int guiScale = getGuiScaleSetting();
		if (guiScale >= 3) {
			return LayoutMode.STACKED;
		}
		int minMapWidth = density == LayoutDensity.WIDE ? 660 : 580;
		int minDetailWidth = density == LayoutDensity.WIDE ? 400 : 360;
		int minimumSideWidth = minMapWidth + minDetailWidth + PANEL_GAP + 24;
		if (frame.width < minimumSideWidth) {
			return LayoutMode.STACKED;
		}
		if (density == LayoutDensity.COMPACT || mainHeight <= 420) {
			return LayoutMode.STACKED;
		}
		return LayoutMode.SIDE_PANEL;
	}

	private int computeEntityScale(Layout layout, Rect portraitCard) {
		int maxFromWidth = Math.max(28, portraitCard.width / 3);
		int maxFromHeight = Math.max(28, portraitCard.height / 2);
		int preferred = layout.mode == LayoutMode.STACKED ? 44 : (layout.density == LayoutDensity.WIDE ? 58 : 50);
		return MathHelper.clamp(preferred, 28, Math.min(maxFromWidth, maxFromHeight));
	}

	private int computeActionsHeight(Layout layout, int availableWidth) {
		return 22 + 20 + 8 + (3 * 24);
	}

	private int getGuiScaleSetting() {
		if (this.client == null) {
			return 0;
		}
		return this.client.options.getGuiScale().getValue();
	}

	private float titleScale(LayoutDensity density) {
		return switch (density) {
			case COMPACT -> 1.08F;
			case NORMAL -> 1.18F;
			case WIDE -> 1.28F;
		};
	}

	private float bodyScale(LayoutDensity density) {
		return switch (density) {
			case COMPACT -> 1.00F;
			case NORMAL -> 1.08F;
			case WIDE -> 1.16F;
		};
	}

	private float metaScale(LayoutDensity density) {
		return switch (density) {
			case COMPACT -> 0.92F;
			case NORMAL -> 1.00F;
			case WIDE -> 1.06F;
		};
	}

	private int statRowHeight(LayoutDensity density) {
		return switch (density) {
			case COMPACT -> 18;
			case NORMAL -> 20;
			case WIDE -> 22;
		};
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private record Rect(int x, int y, int width, int height) {
		int right() {
			return x + width;
		}

		int bottom() {
			return y + height;
		}

		int centerX() {
			return x + (width / 2);
		}

		int centerY() {
			return y + (height / 2);
		}
	}

	private record Layout(Rect frame, Rect topBar, Rect mapPanel, Rect detailPanel, Rect mapLegend, Rect mapBody, Rect detailBody, LayoutDensity density, LayoutMode mode) {
	}

	private record DetailLayout(Rect titleCard, Rect portraitCard, Rect statsCard, Rect actionsCard) {
	}

	private record BaseBounds(int minX, int maxX, int minZ, int maxZ) {
	}

	private record MapView(float minX, float maxX, float minZ, float maxZ) {
	}

	private record Point(int x, int y) {
	}

	private record Marker(int defenderIndex, int centerX, int centerY, int radius) {
	}
}
