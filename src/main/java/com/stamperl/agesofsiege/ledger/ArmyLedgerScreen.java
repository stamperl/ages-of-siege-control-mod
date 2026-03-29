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
	private static final float CAMPAIGN_ROUTE_ZOOM_MIN = 0.85F;
	private static final float CAMPAIGN_ROUTE_ZOOM_MAX = 2.2F;
	private static final float CAMPAIGN_ROUTE_ZOOM_STEP = 0.14F;

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
	private static final int SAMPLE_NATURAL = 0;
	private static final int SAMPLE_PATH = 1;
	private static final int SAMPLE_WOOD = 2;
	private static final int SAMPLE_STONE = 3;
	private static final int SAMPLE_WATER = 4;
	private static final int SAMPLE_SAND = 5;
	private static final int SAMPLE_FOLIAGE = 6;
	private static final int SAMPLE_CROP = 7;

	private static UUID lastSelectedDefenderId;
	private static LedgerMode lastLedgerMode = LedgerMode.DEFENDERS;

	private final ArmyLedgerSnapshot snapshot;

	private int selectedIndex = -1;
	private int selectedSiegeIndex = -1;
	private TextFieldWidget nameField;
	private ButtonWidget renameButton;
	private ButtonWidget cycleRoleButton;
	private ButtonWidget locateButton;
	private ButtonWidget defendersTabButton;
	private ButtonWidget siegesTabButton;
	private ButtonWidget previousSiegeButton;
	private ButtonWidget nextSiegeButton;
	private ButtonWidget lockSiegeButton;
	private ButtonWidget startSiegeButton;

	private MapView cachedMapBounds;
	private int[] cachedMapColors = new int[0];
	private byte[] cachedMapKinds = new byte[0];
	private int cachedSamplesX = -1;
	private int cachedSamplesZ = -1;
	private long cachedMapTick = Long.MIN_VALUE;
	private float mapZoom = 1.0F;
	private float mapPanX;
	private float mapPanZ;
	private boolean mapDragging;
	private double mapDragLastX;
	private double mapDragLastY;
	private float campaignRouteZoom = 1.0F;
	private float campaignRoutePanX;
	private float campaignRoutePanY;
	private boolean campaignRouteDragging;
	private double campaignRouteDragLastX;
	private double campaignRouteDragLastY;
	private boolean detailDrawerOpen = true;
	private int detailScroll;
	private int siegeListScroll;
	private int siegeDetailScroll;
	private LedgerMode ledgerMode = LedgerMode.DEFENDERS;

	private enum LayoutDensity {
		COMPACT,
		NORMAL,
		WIDE
	}

	private enum LayoutMode {
		SIDE_PANEL,
		STACKED,
		DRAWER
	}

	private enum LedgerMode {
		DEFENDERS,
		SIEGES
	}

	public ArmyLedgerScreen(ArmyLedgerSnapshot snapshot) {
		super(Text.literal("Army Command Table"));
		this.snapshot = snapshot;
	}

	@Override
	protected void init() {
		super.init();
		selectInitialGuard();
		selectInitialSiege();

		this.nameField = new TextFieldWidget(this.textRenderer, 0, 0, 120, 20, Text.literal("Guard Name"));
		this.nameField.setMaxLength(32);
		this.addDrawableChild(this.nameField);

		this.renameButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Apply Name"), button -> sendRename())
			.dimensions(0, 0, 90, 20).build());
		this.cycleRoleButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Cycle Role"), button -> sendRoleCycle())
			.dimensions(0, 0, 90, 20).build());
		this.locateButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Locate"), button -> sendLocate())
			.dimensions(0, 0, 90, 20).build());
		this.defendersTabButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Defenders"), button -> setLedgerMode(LedgerMode.DEFENDERS))
			.dimensions(0, 0, 88, 20).build());
		this.siegesTabButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Sieges"), button -> setLedgerMode(LedgerMode.SIEGES))
			.dimensions(0, 0, 88, 20).build());
		this.previousSiegeButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Previous"), button -> cycleSiegeSelection(-1))
			.dimensions(0, 0, 90, 20).build());
		this.nextSiegeButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Next"), button -> cycleSiegeSelection(1))
			.dimensions(0, 0, 90, 20).build());
		this.lockSiegeButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Lock Siege"), button -> sendLockOrCancelSiege())
			.dimensions(0, 0, 120, 20).build());
		this.startSiegeButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Start Siege"), button -> sendStartSiege())
			.dimensions(0, 0, 120, 20).build());

		detailDrawerOpen = createLayout().mode != LayoutMode.DRAWER;
		ledgerMode = defaultLedgerMode();
		lastLedgerMode = ledgerMode;
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
		if (layout.detailVisible) {
			drawDetailPanel(context, layout, mouseX, mouseY);
		}

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (ledgerMode == LedgerMode.SIEGES) {
			Layout layout = createLayout();
			if (button == 0 && contains(campaignRouteViewport(layout), mouseX, mouseY)) {
				int siegeIndex = findSiegeHit(mouseX, mouseY);
				if (siegeIndex >= 0) {
					selectSiege(siegeIndex);
					return true;
				}
				campaignRouteDragging = true;
				campaignRouteDragLastX = mouseX;
				campaignRouteDragLastY = mouseY;
				return true;
			}
			if (!contains(layout.mapBody, mouseX, mouseY)) {
				return super.mouseClicked(mouseX, mouseY, button);
			}
			return super.mouseClicked(mouseX, mouseY, button);
		}
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
		if (button == 0 && campaignRouteDragging) {
			campaignRouteDragging = false;
			return true;
		}
		if (button == 0 && mapDragging) {
			mapDragging = false;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (ledgerMode == LedgerMode.SIEGES) {
			if (campaignRouteDragging && button == 0) {
				Layout layout = createLayout();
				Rect viewport = campaignRouteViewport(layout);
				campaignRoutePanX += (float) ((mouseX - campaignRouteDragLastX) / Math.max(0.01F, campaignRouteZoom));
				campaignRoutePanY += (float) ((mouseY - campaignRouteDragLastY) / Math.max(0.01F, campaignRouteZoom));
				clampCampaignRoutePan(viewport);
				campaignRouteDragLastX = mouseX;
				campaignRouteDragLastY = mouseY;
				return true;
			}
			return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
		}
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
		if (ledgerMode == LedgerMode.SIEGES) {
			if (contains(campaignRouteViewport(layout), mouseX, mouseY) && verticalAmount != 0.0D) {
				Rect viewport = campaignRouteViewport(layout);
				float localX = (float) ((mouseX - viewport.x) / Math.max(0.01F, campaignRouteZoom) - campaignRoutePanX);
				float localY = (float) ((mouseY - viewport.y) / Math.max(0.01F, campaignRouteZoom) - campaignRoutePanY);
				float previousZoom = campaignRouteZoom;
				campaignRouteZoom = MathHelper.clamp(
					campaignRouteZoom + (verticalAmount > 0.0D ? CAMPAIGN_ROUTE_ZOOM_STEP : -CAMPAIGN_ROUTE_ZOOM_STEP),
					CAMPAIGN_ROUTE_ZOOM_MIN,
					CAMPAIGN_ROUTE_ZOOM_MAX
				);
				if (campaignRouteZoom != previousZoom) {
					campaignRoutePanX = (float) ((mouseX - viewport.x) / campaignRouteZoom) - localX;
					campaignRoutePanY = (float) ((mouseY - viewport.y) / campaignRouteZoom) - localY;
					clampCampaignRoutePan(viewport);
				}
				return true;
			}
			if (contains(layout.mapBody, mouseX, mouseY)) {
				int maxScroll = getSiegeListScrollMax(layout);
				if (maxScroll > 0) {
					siegeListScroll = MathHelper.clamp(siegeListScroll - (int) Math.round(verticalAmount * 20.0D), 0, maxScroll);
					refreshControls();
					return true;
				}
			}
			if (layout.detailVisible && contains(layout.detailBody, mouseX, mouseY)) {
				int maxScroll = getSiegeDetailScrollMax(layout);
				if (maxScroll > 0) {
					siegeDetailScroll = MathHelper.clamp(siegeDetailScroll - (int) Math.round(verticalAmount * 20.0D), 0, maxScroll);
					syncWidgetLayout();
					return true;
				}
			}
			return super.mouseScrolled(mouseX, mouseY, verticalAmount);
		}
		if (layout.detailVisible && layout.mode == LayoutMode.DRAWER && contains(layout.detailBody, mouseX, mouseY)) {
			int maxScroll = getDetailScrollMax(layout);
			if (maxScroll > 0) {
				detailScroll = MathHelper.clamp(detailScroll - (int) Math.round(verticalAmount * 20.0D), 0, maxScroll);
				syncWidgetLayout();
				return true;
			}
		}
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

		if (layout.density == LayoutDensity.COMPACT) {
			drawCompactTopBar(context, layout, bar);
			return;
		}

		int inset = layout.density == LayoutDensity.COMPACT ? 10 : 12;
		int gap = layout.density == LayoutDensity.COMPACT ? 8 : 10;
		Rect navCard = topBarNavRect(layout);
		int innerX = bar.x + inset;
		int innerWidth = Math.max(320, navCard.x - gap - innerX);
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
		drawTopBarNavCard(context, navCard, layout);
	}

	private void drawCompactTopBar(DrawContext context, Layout layout, Rect bar) {
		int inset = 10;
		int gap = 8;
		int innerX = bar.x + inset;
		int innerY = bar.y + inset;
		Rect navCard = topBarNavRect(layout);
		int innerWidth = Math.max(300, navCard.x - gap - innerX);
		int cardHeight = bar.height - (inset * 2);

		int settlementWidth = Math.max(180, Math.round(innerWidth * 0.24F));
		int commandWidth = Math.max(260, Math.round(innerWidth * 0.30F));
		int metricWidth = (innerWidth - settlementWidth - commandWidth - (gap * 4)) / 3;

		Rect settlementCard = new Rect(innerX, innerY, settlementWidth, cardHeight);
		Rect commandCard = new Rect(settlementCard.right() + gap, innerY, commandWidth, cardHeight);
		Rect phaseCard = new Rect(commandCard.right() + gap, innerY, metricWidth, cardHeight);
		Rect guardsCard = new Rect(phaseCard.right() + gap, innerY, metricWidth, cardHeight);
		Rect onPostCard = new Rect(guardsCard.right() + gap, innerY, innerWidth - settlementWidth - commandWidth - metricWidth - metricWidth - (gap * 4), cardHeight);

		drawInfoCard(context, settlementCard, "Settlement", safeText(snapshot.ownerName(), "Unclaimed"));
		drawCommandCard(context, commandCard, layout);
		drawInfoCard(context, phaseCard, "Phase", formatPhase(snapshot.siegePhase()));
		drawInfoCard(context, guardsCard, "Guards", Integer.toString(snapshot.defenders().size()));
		drawInfoCard(context, onPostCard, "On Post", Integer.toString(countOnlineDefenders()));
		drawTopBarNavCard(context, navCard, layout);
	}

	private void drawCommandCard(DrawContext context, Rect rect, Layout layout) {
		context.fill(rect.x, rect.y, rect.right(), rect.bottom(), CARD_COLOR);
		context.drawBorder(rect.x, rect.y, rect.width, rect.height, CARD_BORDER);
		drawScaledText(context, "Army Command Table", rect.x + 10, rect.y + 8, TEXT_PRIMARY, titleScale(layout.density));
		drawTrimmed(context, "Banner " + snapshot.bannerPos().toShortString(), rect.x + 10, rect.y + 30, rect.width - 20, TEXT_SECONDARY);
		if (rect.height >= 52) {
			String progress = snapshot.nextAgeRequirement() < 0
				? snapshot.currentAgeName() + " age maxed"
				: snapshot.completedSieges() + "/" + snapshot.nextAgeRequirement() + " victories to next age";
			drawTrimmed(context, progress, rect.x + 10, rect.y + 46, rect.width - 20, TEXT_MUTED);
		}
	}

	private void drawInfoCard(DrawContext context, Rect rect, String label, String value) {
		context.fill(rect.x, rect.y, rect.right(), rect.bottom(), CARD_COLOR);
		context.drawBorder(rect.x, rect.y, rect.width, rect.height, CARD_BORDER);
		drawScaledText(context, label.toUpperCase(), rect.x + 10, rect.y + 8, TEXT_SECONDARY, metaScale(layoutDensity()));
		drawTrimmed(context, value, rect.x + 10, rect.y + 26, rect.width - 20, TEXT_PRIMARY);
	}

	private void drawTopBarNavCard(DrawContext context, Rect rect, Layout layout) {
		context.fill(rect.x, rect.y, rect.right(), rect.bottom(), CARD_COLOR);
		context.drawBorder(rect.x, rect.y, rect.width, rect.height, CARD_BORDER);
		drawScaledText(context, "VIEW", rect.x + 10, rect.y + 8, TEXT_SECONDARY, metaScale(layout.density));
	}

	private Rect topBarNavRect(Layout layout) {
		Rect bar = layout.topBar;
		int inset = layout.density == LayoutDensity.COMPACT ? 10 : 12;
		int width = layout.density == LayoutDensity.COMPACT ? 196 : 208;
		int height = Math.max(44, bar.height - (inset * 2));
		int x = bar.right() - inset - width;
		int y = bar.y + ((bar.height - height) / 2);
		return new Rect(x, y, width, height);
	}

	private void drawMapPanel(DrawContext context, Layout layout, int mouseX, int mouseY) {
		if (ledgerMode == LedgerMode.SIEGES) {
			drawSiegeListPanel(context, layout, mouseX, mouseY);
			return;
		}
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
				int index = sampleZ * cachedSamplesX + sampleX;
				context.fill(x0, y0, x1, y1, cachedMapColors[index]);
				if (x1 - x0 > 2 && y1 - y0 > 2) {
					drawFeatureEdge(context, x0, y0, x1, y1, sampleX, sampleZ, index);
				}
			}
		}
	}

	private void drawFeatureEdge(DrawContext context, int x0, int y0, int x1, int y1, int sampleX, int sampleZ, int index) {
		byte kind = cachedMapKinds[index];
		if (kind == SAMPLE_NATURAL || kind == SAMPLE_FOLIAGE) {
			return;
		}
		int edgeColor = switch (kind) {
			case SAMPLE_PATH -> 0x505A4426;
			case SAMPLE_WOOD -> 0x6050321D;
			case SAMPLE_STONE -> 0x60656A70;
			case SAMPLE_WATER -> 0x50536F9A;
			case SAMPLE_SAND -> 0x50A58F59;
			case SAMPLE_CROP -> 0x50578E2E;
			default -> 0x4036404B;
		};
		if (sampleX == 0 || cachedMapKinds[index - 1] != kind) {
			context.fill(x0, y0, x0 + 1, y1, edgeColor);
		}
		if (sampleZ == 0 || cachedMapKinds[index - cachedSamplesX] != kind) {
			context.fill(x0, y0, x1, y0 + 1, edgeColor);
		}
		if (sampleX == cachedSamplesX - 1 || cachedMapKinds[index + 1] != kind) {
			context.fill(x1 - 1, y0, x1, y1, edgeColor);
		}
		if (sampleZ == cachedSamplesZ - 1 || cachedMapKinds[index + cachedSamplesX] != kind) {
			context.fill(x0, y1 - 1, x1, y1, edgeColor);
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
			int boxLeft = marker.centerX - marker.radius - 1;
			int boxTop = marker.centerY - marker.radius - 1;
			int boxSize = marker.radius * 2 + 2;
			context.fill(boxLeft, boxTop, boxLeft + boxSize, boxTop + boxSize, 0xD0141A20);
			context.drawBorder(boxLeft, boxTop, boxSize, boxSize, markerColor);
			if (defender.role() == DefenderRole.ARCHER) {
				drawBowMarker(context, marker.centerX, marker.centerY, markerColor);
			} else {
				drawSwordMarker(context, marker.centerX, marker.centerY, markerColor);
			}

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
		if (ledgerMode == LedgerMode.SIEGES) {
			drawSiegeDetailPanel(context, layout);
			return;
		}
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

		int scrollOffset = layout.mode == LayoutMode.DRAWER ? getClampedDetailScroll(layout) : 0;
		DetailLayout detailLayout = createDetailLayout(layout);
		Rect titleCard = offsetRect(detailLayout.titleCard, -scrollOffset);
		Rect portraitCard = offsetRect(detailLayout.portraitCard, -scrollOffset);
		Rect statsCard = offsetRect(detailLayout.statsCard, -scrollOffset);
		Rect actionsCard = offsetRect(detailLayout.actionsCard, -scrollOffset);

		if (layout.mode == LayoutMode.DRAWER) {
			context.enableScissor(body.x, body.y, body.right(), body.bottom());
		}

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

		if (layout.mode == LayoutMode.DRAWER) {
			context.disableScissor();
			drawDetailScrollbar(context, layout, detailLayout.actionsCard.bottom());
		}
	}

	private void drawSiegeListPanel(DrawContext context, Layout layout, int mouseX, int mouseY) {
		Rect panel = layout.mapPanel;
		Rect body = layout.mapBody;
		context.fill(panel.x, panel.y, panel.right(), panel.bottom(), PANEL_COLOR);
		context.drawBorder(panel.x, panel.y, panel.width, panel.height, PANEL_BORDER);
		context.fill(panel.x, panel.y, panel.right(), panel.y + PANEL_HEADER_HEIGHT, PANEL_HEADER_COLOR);
		context.fill(panel.x + 12, panel.y + PANEL_HEADER_HEIGHT, panel.right() - 12, panel.y + PANEL_HEADER_HEIGHT + 1, CARD_BORDER);
		drawTrimmed(context, "Siege Campaign", panel.x + 14, panel.y + 13, 220, TEXT_PRIMARY);
		drawScaledText(context, "Select an operation and review the threat before locking it in.", panel.right() - 260, panel.y + 15, TEXT_SECONDARY, metaScale(layout.density));
		if (ledgerMode == LedgerMode.SIEGES) {
			Rect routeViewport = campaignRouteViewport(layout);
			clampCampaignRoutePan(routeViewport);
			context.enableScissor(routeViewport.x, routeViewport.y, routeViewport.right(), routeViewport.bottom());
			context.getMatrices().push();
			context.getMatrices().translate(routeViewport.x, routeViewport.y, 0.0F);
			context.getMatrices().scale(campaignRouteZoom, campaignRouteZoom, 1.0F);
			context.getMatrices().translate(campaignRoutePanX, campaignRoutePanY, 0.0F);
			drawCampaignRouteBackdrop(context, 0, 0, campaignRouteCanvasWidth(), campaignRouteCanvasHeight());
			drawSiegeProgressTree(context, 18, 10, campaignRouteCanvasWidth() - 36, getSelectedSiege());
			context.getMatrices().pop();
			context.disableScissor();
			ArmyLedgerSnapshot.SiegeEntry siege = getSelectedSiege();
			if (siege != null) {
				int footerY = routeViewport.bottom() - 30;
				context.fill(routeViewport.x + 10, footerY, routeViewport.right() - 10, routeViewport.bottom() - 10, 0x28140F0A);
				context.fill(routeViewport.x + 10, footerY, routeViewport.right() - 10, footerY + 1, CARD_BORDER);
				drawTrimmed(context, siege.name(), routeViewport.x + 18, footerY + 6, routeViewport.width - 184, TEXT_PRIMARY);
				String routeLabel = siege.unlocked()
					? (siege.replay() ? "Completed route unlocked for reward runs" : "Ready to deploy")
					: snapshot.completedSieges() + "/" + siege.unlockVictories() + " victories to unlock";
				drawTrimmed(context, routeLabel, routeViewport.x + 18, footerY + 18, routeViewport.width - 184, siege.unlocked() ? TEXT_WARM : TEXT_MUTED);
				drawChip(context, routeViewport.right() - 144, footerY + 6, 56, "Age " + ageShortLabel(siege.ageLevel()), siegeAccentColor(siege), TEXT_PRIMARY);
				drawChip(context, routeViewport.right() - 82, footerY + 6, 62, "+" + siege.warSuppliesReward() + " sup", 0x30546B43, TEXT_PRIMARY);
			}
			return;
		}

		int scrollOffset = getClampedSiegeListScroll(layout);
		context.enableScissor(body.x, body.y, body.right(), body.bottom());
		int routeX = body.x + 28;
		if (!snapshot.sieges().isEmpty()) {
			int firstCenterY = siegeCardRect(body, 0, scrollOffset).y + 24;
			int lastCenterY = siegeCardRect(body, snapshot.sieges().size() - 1, scrollOffset).y + 24;
			context.fill(routeX, firstCenterY, routeX + 2, lastCenterY, 0x2236414B);
		}
		for (int i = 0; i < snapshot.sieges().size(); i++) {
			Rect card = siegeCardRect(body, i, scrollOffset);
			ArmyLedgerSnapshot.SiegeEntry siege = snapshot.sieges().get(i);
			boolean selected = i == selectedSiegeIndex;
			int accent = siegeAccentColor(siege);
			int border = selected ? MARKER_SELECTED : CARD_BORDER;
			int fill = selected ? 0x2636404B : (siege.unlocked() ? CARD_COLOR : 0x18222830);
			context.fill(card.x, card.y, card.right(), card.bottom(), fill);
			context.drawBorder(card.x, card.y, card.width, card.height, border);
			context.fill(card.x, card.y, card.x + 6, card.bottom(), accent);
			int nodeY = card.y + 22;
			drawCampaignNode(context, routeX - 6, nodeY - 6, 12, siege, selected, false);
			context.fill(routeX + 2, nodeY, card.x + 12, nodeY + 2, selected ? MARKER_SELECTED : 0x2636414B);

			drawTrimmed(context, siege.name(), card.x + 24, card.y + 10, card.width - 188, siege.unlocked() ? TEXT_PRIMARY : TEXT_MUTED);
			String status = siege.unlocked()
				? (siege.replay() ? "Completed  •  Replayable" : "Current Tier")
				: "Unlocks at " + siege.unlockVictories() + " victories";
			drawScaledText(context, status, card.x + 24, card.y + 28, siege.unlocked() ? TEXT_WARM : TEXT_MUTED, metaScale(layout.density));
			drawTrimmed(context, siege.enemySummary(), card.x + 24, card.y + 44, card.width - 208, TEXT_SECONDARY);
			drawChip(context, card.right() - 150, card.y + 10, 56, "Age " + ageShortLabel(siege.ageLevel()), accent, TEXT_PRIMARY);
			drawChip(context, card.right() - 88, card.y + 10, 62, "+" + siege.warSuppliesReward() + " sup", 0x30546B43, TEXT_PRIMARY);
			drawWavePips(context, card.x + 24, card.bottom() - 18, Math.min(12, siege.waveSize()), 12, accent);
			drawSiegeFormationStrip(context, card.x + 148, card.bottom() - 22, siege, accent);
			if (siege.replay()) {
				drawScaledText(context, "reward route", card.right() - 122, card.bottom() - 20, TEXT_SECONDARY, 0.8F);
			}
			if (selected || contains(card, mouseX, mouseY)) {
				context.drawBorder(card.x + 8, card.y + 8, card.width - 16, card.height - 16, 0x384E6472);
			}
		}
		context.disableScissor();
		drawPanelScrollbar(context, body, getClampedSiegeListScroll(layout), getSiegeListScrollMax(layout), getSiegeListContentHeight(layout));
	}

	private void drawSiegeDetailPanel(DrawContext context, Layout layout) {
		Rect panel = layout.detailPanel;
		Rect body = layout.detailBody;
		context.fill(panel.x, panel.y, panel.right(), panel.bottom(), PANEL_COLOR);
		context.drawBorder(panel.x, panel.y, panel.width, panel.height, PANEL_BORDER);
		context.fill(panel.x, panel.y, panel.right(), panel.y + PANEL_HEADER_HEIGHT, PANEL_HEADER_COLOR);
		context.fill(panel.x + 12, panel.y + PANEL_HEADER_HEIGHT, panel.right() - 12, panel.y + PANEL_HEADER_HEIGHT + 1, CARD_BORDER);
		drawTrimmed(context, "Siege Overview", panel.x + 14, panel.y + 13, 180, TEXT_PRIMARY);
		drawScaledText(context, "Plan, lock, and start from here.", panel.right() - 140, panel.y + 15, TEXT_SECONDARY, metaScale(layout.density));

		ArmyLedgerSnapshot.SiegeEntry siege = getSelectedSiege();
		if (siege == null) {
			context.fill(body.x, body.y, body.right(), body.bottom(), CARD_COLOR);
			context.drawBorder(body.x, body.y, body.width, body.height, CARD_BORDER);
			drawTrimmed(context, "No siege operation is available.", body.x + 16, body.y + 24, body.width - 32, TEXT_SECONDARY);
			return;
		}

		int accent = siegeAccentColor(siege);
		int scrollOffset = getClampedSiegeDetailScroll(layout);
		SiegeDetailLayout detailLayout = createSiegeDetailLayout(layout);
		Rect titleCard = offsetRect(detailLayout.titleCard(), -scrollOffset);
		Rect overviewCard = offsetRect(detailLayout.overviewCard(), -scrollOffset);
		Rect campaignCard = offsetRect(detailLayout.campaignCard(), -scrollOffset);
		Rect actionsCard = offsetRect(detailLayout.actionsCard(), -scrollOffset);

		context.enableScissor(body.x, body.y, body.right(), body.bottom());

		drawCard(context, titleCard);
		drawCard(context, overviewCard);
		drawCard(context, actionsCard);
		context.fill(titleCard.x, titleCard.y, titleCard.x + 6, titleCard.bottom(), accent);

		drawTrimmed(context, siege.name(), titleCard.x + 14, titleCard.y + 12, titleCard.width - 136, TEXT_PRIMARY);
		String titleStatus = siege.unlocked()
			? (siege.replay() ? "Completed route  •  replayable for rewards" : "Progress operation")
			: "Locked until " + siege.unlockVictories() + " victories";
		drawScaledText(context, titleStatus, titleCard.x + 14, titleCard.y + 36, siege.unlocked() ? TEXT_WARM : TEXT_MUTED, metaScale(layout.density));
		drawChip(context, titleCard.right() - 132, titleCard.y + 12, 60, "Age " + ageShortLabel(siege.ageLevel()), accent, TEXT_PRIMARY);
		drawChip(context, titleCard.right() - 66, titleCard.y + 12, 54, siege.waveSize() + " wave", 0x2E2E485A, TEXT_PRIMARY);

		int rowY = overviewCard.y + 12;
		drawScaledText(context, "Threat Board", overviewCard.x + 12, rowY, TEXT_SECONDARY, metaScale(layout.density));
		drawWrapped(context, siege.description(), overviewCard.x + 12, rowY + 16, overviewCard.width - 24, TEXT_PRIMARY, 2);
		drawWavePips(context, overviewCard.x + 12, rowY + 50, Math.min(12, siege.waveSize()), 12, accent);
		drawScaledText(context, "Wave pressure", overviewCard.x + 118, rowY + 47, TEXT_SECONDARY, metaScale(layout.density));
		drawSiegeFormationStrip(context, overviewCard.x + 12, rowY + 76, siege, accent);
		rowY += 104;
		rowY = drawStatRow(context, overviewCard.x + 12, rowY, "Enemies", siege.enemySummary(), overviewCard.width - 24);
		rowY = drawStatRow(context, overviewCard.x + 12, rowY, "Weapons", siege.weaponSummary(), overviewCard.width - 24);
		drawStatRow(context, overviewCard.x + 12, rowY, "Threat", siege.threatSummary(), overviewCard.width - 24);

		if (false && ledgerMode == LedgerMode.SIEGES) {
			int campaignY = campaignCard.y + 12;
			drawScaledText(context, "Campaign Status", campaignCard.x + 12, campaignY, TEXT_SECONDARY, metaScale(layout.density));
			drawTrimmed(context, snapshot.currentAgeName() + " age", campaignCard.x + 12, campaignY + 16, campaignCard.width - 24, TEXT_PRIMARY);
			drawTrimmed(context, snapshot.completedSieges() + " total victories", campaignCard.x + 12, campaignY + 34, campaignCard.width - 24, TEXT_SECONDARY);
			drawWrapped(context, selectedSiegeStatus(siege), campaignCard.x + 12, campaignY + 54, campaignCard.width - 24, TEXT_MUTED, 2);
			drawProgressBar(
				context,
				campaignCard.x + 12,
				campaignCard.bottom() - 22,
				campaignCard.width - 24,
				8,
				Math.min(snapshot.completedSieges(), Math.max(1, siege.unlockVictories())),
				Math.max(1, siege.unlockVictories()),
				accent,
				0x2236414B
			);
		} else {
		int campaignY = campaignCard.y + 12;
		drawScaledText(context, "Age Route", campaignCard.x + 12, campaignY, TEXT_SECONDARY, metaScale(layout.density));
		drawScaledText(context, "Drag to pan  •  Wheel to zoom", campaignCard.right() - 162, campaignY + 1, TEXT_MUTED, 0.72F);
		Rect routeViewport = campaignRouteViewport(layout);
		clampCampaignRoutePan(routeViewport);
		context.enableScissor(routeViewport.x, routeViewport.y, routeViewport.right(), routeViewport.bottom());
		context.getMatrices().push();
		context.getMatrices().translate(routeViewport.x, routeViewport.y, 0.0F);
		context.getMatrices().scale(campaignRouteZoom, campaignRouteZoom, 1.0F);
		context.getMatrices().translate(campaignRoutePanX, campaignRoutePanY, 0.0F);
		drawCampaignRouteBackdrop(context, 0, 0, campaignRouteCanvasWidth(), campaignRouteCanvasHeight());
		drawSiegeProgressTree(context, 18, 10, campaignRouteCanvasWidth() - 36, siege);
		context.getMatrices().pop();
		context.disableScissor();
		context.fill(campaignCard.x + 12, routeViewport.bottom() + 4, campaignCard.right() - 12, campaignCard.bottom() - 10, 0x28140F0A);
		context.fill(campaignCard.x + 12, routeViewport.bottom() + 4, campaignCard.right() - 12, routeViewport.bottom() + 5, CARD_BORDER);
		drawProgressBar(
			context,
			campaignCard.x + 12,
			routeViewport.bottom() + 10,
			campaignCard.width - 24,
			8,
			Math.min(snapshot.completedSieges(), Math.max(1, siege.unlockVictories())),
			Math.max(1, siege.unlockVictories()),
			accent,
			0x2236414B
		);
		String unlockLabel = siege.unlocked()
			? (siege.replay() ? "Completed route unlocked for reward runs" : "Ready to deploy")
			: snapshot.completedSieges() + "/" + siege.unlockVictories() + " victories to unlock";
		drawTrimmed(context, unlockLabel, campaignCard.x + 12, routeViewport.bottom() + 20, campaignCard.width - 24, siege.unlocked() ? TEXT_WARM : TEXT_MUTED);

		}

		int actionY = actionsCard.y + 12;
		drawScaledText(context, "Command Post", actionsCard.x + 12, actionY, TEXT_SECONDARY, metaScale(layout.density));
		drawTrimmed(context, snapshot.currentAgeName() + " age - " + snapshot.completedSieges() + " victories", actionsCard.x + 12, actionY + 16, actionsCard.width - 24, TEXT_PRIMARY);
		drawWrapped(context, selectedSiegeStatus(siege), actionsCard.x + 12, actionY + 36, actionsCard.width - 24, TEXT_SECONDARY, 3);
		String rallyText = snapshot.hasRally() ? snapshot.rallyPos().toShortString() : "Not placed";
		drawTrimmed(context, "Rally: " + rallyText, actionsCard.x + 12, actionY + 76, actionsCard.width - 24, snapshot.hasRally() ? TEXT_WARM : TEXT_MUTED);

		context.disableScissor();
		drawPanelScrollbar(context, body, getClampedSiegeDetailScroll(layout), getSiegeDetailScrollMax(layout), getSiegeDetailContentHeight(layout));
	}

	private Rect siegeCardRect(Rect body, int index, int scrollOffset) {
		int gap = 10;
		int cardHeight = siegeCardHeight();
		int y = body.y + (index * (cardHeight + gap)) - scrollOffset;
		return new Rect(body.x, y, body.width, cardHeight);
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

	private void drawDiamond(DrawContext context, int x, int y, int size, int color) {
		int half = size / 2;
		for (int row = 0; row < size; row++) {
			int distance = Math.abs(row - half);
			int span = half - distance;
			context.fill(x + half - span, y + row, x + half + span + 1, y + row + 1, color);
		}
	}

	private void drawSwordMarker(DrawContext context, int centerX, int centerY, int color) {
		context.fill(centerX, centerY - 5, centerX + 1, centerY + 3, color);
		context.fill(centerX - 2, centerY - 2, centerX + 3, centerY - 1, color);
		context.fill(centerX - 1, centerY + 3, centerX + 2, centerY + 5, color);
	}

	private void drawBowMarker(DrawContext context, int centerX, int centerY, int color) {
		context.fill(centerX - 2, centerY - 5, centerX - 1, centerY + 5, color);
		context.fill(centerX + 2, centerY - 5, centerX + 3, centerY + 5, color);
		context.fill(centerX - 1, centerY - 4, centerX + 2, centerY - 3, color);
		context.fill(centerX - 1, centerY + 3, centerX + 2, centerY + 4, color);
		context.fill(centerX, centerY - 5, centerX + 1, centerY + 5, 0xE0F0F3F6);
	}

	private void drawRamMarker(DrawContext context, int x, int y, int color) {
		context.fill(x, y + 2, x + 10, y + 8, color);
		context.fill(x + 2, y, x + 8, y + 3, color);
		context.fill(x + 1, y + 8, x + 3, y + 10, 0xE0F0F3F6);
		context.fill(x + 7, y + 8, x + 9, y + 10, 0xE0F0F3F6);
	}

	private void drawChip(DrawContext context, int x, int y, int width, String text, int fillColor, int textColor) {
		context.fill(x, y, x + width, y + 16, fillColor);
		context.drawBorder(x, y, width, 16, CARD_BORDER);
		drawScaledText(context, text, x + 6, y + 4, textColor, 0.85F);
	}

	private void drawWavePips(DrawContext context, int x, int y, int filledCount, int totalCount, int accent) {
		for (int i = 0; i < totalCount; i++) {
			int left = x + (i * 10);
			int fill = i < filledCount ? accent : 0x2236414B;
			context.fill(left, y, left + 7, y + 6, fill);
			context.drawBorder(left, y, 7, 6, CARD_BORDER);
		}
	}

	private void drawSiegeFormationStrip(DrawContext context, int x, int y, ArmyLedgerSnapshot.SiegeEntry siege, int accent) {
		int rangedCount = MathHelper.clamp(Math.max(2, siege.waveSize() / 3), 2, 4);
		int breacherCount = MathHelper.clamp(Math.max(1, siege.ageLevel() + (siege.hasRam() ? 0 : 1)), 1, 3);
		for (int i = 0; i < rangedCount; i++) {
			drawBowMarker(context, x + (i * 14), y + 6, MARKER_ARCHER);
		}
		int swordStart = x + (rangedCount * 14) + 12;
		for (int i = 0; i < breacherCount; i++) {
			drawSwordMarker(context, swordStart + (i * 14), y + 6, MARKER_SOLDIER);
		}
		if (siege.hasRam()) {
			int ramX = swordStart + (breacherCount * 14) + 12;
			drawRamMarker(context, ramX, y + 1, accent);
		}
	}

	private void drawProgressBar(DrawContext context, int x, int y, int width, int height, int value, int max, int fillColor, int emptyColor) {
		context.fill(x, y, x + width, y + height, emptyColor);
		context.drawBorder(x, y, width, height, CARD_BORDER);
		int filledWidth = MathHelper.clamp(Math.round((value / (float) Math.max(1, max)) * (width - 2)), 0, width - 2);
		context.fill(x + 1, y + 1, x + 1 + filledWidth, y + height - 1, fillColor);
	}

	private void drawAgeTrack(DrawContext context, int x, int y, int width, int currentAgeLevel, int siegeAgeLevel) {
		int stages = 4;
		int spacing = Math.max(54, width / Math.max(1, stages - 1));
		for (int i = 0; i < stages; i++) {
			int nodeX = x + Math.min(width - 16, i * spacing);
			int color = i == siegeAgeLevel ? MARKER_SELECTED : (i <= currentAgeLevel ? TEXT_WARM : CARD_BORDER);
			if (i < stages - 1) {
				context.fill(nodeX + 12, y + 5, Math.min(x + width - 8, nodeX + spacing), y + 7, 0x2836414B);
			}
			context.fill(nodeX, y, nodeX + 12, y + 12, color);
			context.drawBorder(nodeX, y, 12, 12, FRAME_BORDER);
			drawScaledText(context, ageShortLabel(i), nodeX - 2, y + 16, i == siegeAgeLevel ? TEXT_PRIMARY : TEXT_MUTED, 0.75F);
		}
	}

	private void drawSiegeProgressTree(DrawContext context, int x, int y, int width, ArmyLedgerSnapshot.SiegeEntry selectedSiege) {
		int stageCount = 4;
		int nodeWidth = Math.max(46, Math.min(64, (width - 72) / stageCount));
		int nodeHeight = 20;
		int step = stageCount <= 1 ? 0 : Math.max(56, (width - nodeWidth) / (stageCount - 1));
		int[] nodeYs = new int[] {y + 20, y + 6, y + 28, y + 12};
		int selectedAge = selectedSiege == null ? snapshot.currentAgeLevel() : selectedSiege.ageLevel();
		for (int i = 0; i < stageCount - 1; i++) {
			int startX = x + Math.min(width - nodeWidth, i * step) + nodeWidth;
			int endX = x + Math.min(width - nodeWidth, (i + 1) * step);
			int startMidY = nodeYs[i] + (nodeHeight / 2);
			int endMidY = nodeYs[i + 1] + (nodeHeight / 2);
			int threshold = ageUnlockThreshold(i + 1);
			int deltaWins = Math.max(1, threshold - ageUnlockThreshold(i));
			boolean unlocked = snapshot.currentAgeLevel() >= i + 1;
			drawTreeConnector(context, startX, startMidY, endX, endMidY, unlocked ? TEXT_WARM : CARD_BORDER);
			int labelX = startX + Math.max(4, (endX - startX - 44) / 2);
			int labelY = Math.min(startMidY, endMidY) - 10;
			drawScaledText(context, deltaWins + " wins", labelX, labelY, unlocked ? TEXT_WARM : TEXT_MUTED, 0.72F);
		}
		for (int i = 0; i < stageCount; i++) {
			int nodeX = x + Math.min(width - nodeWidth, i * step);
			boolean reached = snapshot.currentAgeLevel() >= i;
			boolean selected = i == selectedAge;
			int replayCount = replayableSiegeCountForAge(i);
			Rect nodeRect = routeNodeRect(i);
			drawAgeStageNode(context, nodeRect.x, nodeRect.y, nodeRect.width, nodeRect.height, i, reached, selected);
			if (replayCount > 0 && reached) {
				int replayY = nodeRect.y + nodeRect.height + 8;
				context.fill(nodeRect.x + 8, replayY, nodeRect.x + nodeRect.width - 8, replayY + 14, 0x20394A36);
				context.drawBorder(nodeRect.x + 8, replayY, nodeRect.width - 16, 14, CARD_BORDER);
				drawScaledText(context, replayCount + " replay", nodeRect.x + 12, replayY + 4, TEXT_SECONDARY, 0.72F);
			}
		}
	}

	private Rect routeNodeRect(int index) {
		int stageCount = Math.max(1, snapshot.sieges().size());
		int nodeWidth = Math.max(46, Math.min(64, (campaignRouteCanvasWidth() - 72) / stageCount));
		int nodeHeight = 20;
		int step = stageCount <= 1 ? 0 : Math.max(56, (campaignRouteCanvasWidth() - 36 - nodeWidth) / (stageCount - 1));
		int[] nodeYs = new int[] {30, 16, 38, 22};
		int nodeX = 18 + Math.min(campaignRouteCanvasWidth() - 36 - nodeWidth, index * step);
		int nodeY = nodeYs[Math.min(index, nodeYs.length - 1)];
		return new Rect(nodeX, nodeY, nodeWidth, nodeHeight);
	}

	private void drawAgeStageNode(DrawContext context, int x, int y, int width, int height, int ageLevel, boolean reached, boolean selected) {
		int fill = reached ? 0xFF7C6224 : 0x24303840;
		int innerFill = reached ? 0xFFD39F16 : 0x2A38414B;
		int border = selected ? MARKER_SELECTED : (reached ? TEXT_WARM : CARD_BORDER);
		context.fill(x, y, x + width, y + height, fill);
		context.drawBorder(x, y, width, height, border);
		context.fill(x + 2, y + 2, x + width - 2, y + height - 2, innerFill);
		drawCenteredScaledText(context, ageLabel(ageLevel), x, y + 6, width, reached ? 0xFF1A1404 : TEXT_MUTED, 0.72F);
		if (selected) {
			context.drawBorder(x - 2, y - 2, width + 4, height + 4, 0x805F7C90);
		}
	}

	private void drawTreeConnector(DrawContext context, int startX, int startY, int endX, int endY, int color) {
		int turnX = startX + Math.max(12, (endX - startX) / 2);
		context.fill(startX, startY, turnX, startY + 2, color);
		int top = Math.min(startY, endY);
		int bottom = Math.max(startY, endY) + 2;
		context.fill(turnX, top, turnX + 2, bottom, color);
		context.fill(turnX, endY, endX, endY + 2, color);
	}

	private void drawCampaignRouteBackdrop(DrawContext context, int x, int y, int width, int height) {
		context.fill(x, y, x + width, y + height, 0xFF473629);
		context.fill(x + 4, y + 4, x + width - 4, y + height - 4, 0xFF8A724A);
		for (int row = y + 6; row < y + height - 6; row += 18) {
			for (int col = x + 6; col < x + width - 6; col += 18) {
				int tile = ((row + col) / 18) % 2 == 0 ? 0x202B1D13 : 0x1423150D;
				context.fill(col, row, Math.min(x + width - 6, col + 14), Math.min(y + height - 6, row + 14), tile);
			}
		}
		for (int beam = x + 10; beam < x + width - 8; beam += 44) {
			context.fill(beam, y + 4, beam + 3, y + height - 4, 0x4029150D);
		}
		for (int stud = x + 18; stud < x + width - 12; stud += 36) {
			context.fill(stud, y + 10, stud + 4, y + 14, 0x80D7B56A);
			context.fill(stud, y + height - 14, stud + 4, y + height - 10, 0x80D7B56A);
		}
		context.drawBorder(x, y, width, height, 0xB06A5433);
	}

	private void drawCenteredScaledText(DrawContext context, String text, int x, int y, int width, int color, float scale) {
		String safe = safeText(text, "");
		int rawWidth = this.textRenderer.getWidth(safe);
		int centeredX = x + Math.max(0, Math.round((width - (rawWidth * scale)) / 2.0F));
		drawScaledText(context, safe, centeredX, y, color, scale);
	}

	private Rect campaignRouteViewport(Layout layout) {
		Rect body = layout.mapBody;
		return new Rect(body.x, body.y, body.width, body.height);
	}

	private int campaignRouteCanvasWidth() {
		return 420;
	}

	private int campaignRouteCanvasHeight() {
		return 150;
	}

	private void clampCampaignRoutePan(Rect viewport) {
		float visibleWidth = viewport.width / Math.max(0.01F, campaignRouteZoom);
		float visibleHeight = viewport.height / Math.max(0.01F, campaignRouteZoom);
		float minPanX = visibleWidth >= campaignRouteCanvasWidth()
			? (visibleWidth - campaignRouteCanvasWidth()) * 0.5F
			: visibleWidth - campaignRouteCanvasWidth();
		float maxPanX = visibleWidth >= campaignRouteCanvasWidth()
			? minPanX
			: 0.0F;
		float minPanY = visibleHeight >= campaignRouteCanvasHeight()
			? (visibleHeight - campaignRouteCanvasHeight()) * 0.5F
			: visibleHeight - campaignRouteCanvasHeight();
		float maxPanY = visibleHeight >= campaignRouteCanvasHeight()
			? minPanY
			: 0.0F;
		campaignRoutePanX = MathHelper.clamp(campaignRoutePanX, minPanX, maxPanX);
		campaignRoutePanY = MathHelper.clamp(campaignRoutePanY, minPanY, maxPanY);
	}

	private void drawCampaignNode(DrawContext context, int x, int y, int size, ArmyLedgerSnapshot.SiegeEntry siege, boolean selected, boolean detailMode) {
		int fill = !siege.unlocked()
			? 0x1A2A3138
			: siege.replay()
				? 0xFF2E4552
				: siegeAccentColor(siege);
		int border = selected ? MARKER_SELECTED : (siege.unlocked() ? TEXT_WARM : CARD_BORDER);
		context.fill(x, y, x + size, y + size, fill);
		context.drawBorder(x, y, size, size, border);
		if (siege.replay()) {
			context.fill(x + 4, y + 4, x + size - 4, y + size - 4, 0x90E4D2A2);
		} else if (siege.unlocked()) {
			context.fill(x + 4, y + 4, x + size - 4, y + size - 4, 0x90F0F3F6);
		}
		if (detailMode && selected) {
			context.drawBorder(x - 2, y - 2, size + 4, size + 4, 0x605F7C90);
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

	private int findSiegeHit(double mouseX, double mouseY) {
		Layout layout = createLayout();
		Rect viewport = campaignRouteViewport(layout);
		float localX = (float) ((mouseX - viewport.x) / Math.max(0.01F, campaignRouteZoom) - campaignRoutePanX);
		float localY = (float) ((mouseY - viewport.y) / Math.max(0.01F, campaignRouteZoom) - campaignRoutePanY);
		for (int i = 0; i < snapshot.sieges().size(); i++) {
			if (contains(routeNodeRect(i), localX, localY)) {
				return i;
			}
		}
		return -1;
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
		if (nameField == null || defendersTabButton == null) {
			return;
		}
		Layout layout = createLayout();
		Rect navRect = topBarNavRect(layout);
		int navGap = 10;
		int navButtonY = navRect.y + Math.max(18, (navRect.height - 20) / 2);
		int navButtonWidth = (navRect.width - 30) / 2;
		defendersTabButton.setX(navRect.x + navGap);
		defendersTabButton.setY(navButtonY);
		defendersTabButton.setWidth(navButtonWidth);
		siegesTabButton.setX(navRect.x + navGap + navButtonWidth + navGap);
		siegesTabButton.setY(navButtonY);
		siegesTabButton.setWidth(navButtonWidth);
		defendersTabButton.visible = true;
		siegesTabButton.visible = true;
		if (ledgerMode == LedgerMode.SIEGES) {
			nameField.setVisible(false);
			renameButton.visible = false;
			cycleRoleButton.visible = false;
			locateButton.visible = false;
			int scrollOffset = getClampedSiegeDetailScroll(layout);
			Rect visibleBody = layout.detailBody;
			SiegeDetailLayout siegeLayout = createSiegeDetailLayout(layout);
			Rect actions = offsetRect(siegeLayout.actionsCard(), -scrollOffset);
			int leftX = actions.x + 12;
			int rightX = actions.centerX() + 4;
			int topY = actions.bottom() - 72;
			int buttonWidth = (siegeLayout.actionsCard().width - 32) / 2;
			previousSiegeButton.setX(leftX);
			previousSiegeButton.setY(topY);
			previousSiegeButton.setWidth(buttonWidth);
			nextSiegeButton.setX(rightX);
			nextSiegeButton.setY(topY);
			nextSiegeButton.setWidth(buttonWidth);
			lockSiegeButton.setX(leftX);
			lockSiegeButton.setY(topY + 24);
			lockSiegeButton.setWidth(siegeLayout.actionsCard().width - 24);
			startSiegeButton.setX(leftX);
			startSiegeButton.setY(topY + 48);
			startSiegeButton.setWidth(siegeLayout.actionsCard().width - 24);
			previousSiegeButton.visible = topY >= visibleBody.y && topY + 20 <= visibleBody.bottom();
			nextSiegeButton.visible = previousSiegeButton.visible;
			lockSiegeButton.visible = topY + 24 >= visibleBody.y && topY + 44 <= visibleBody.bottom();
			startSiegeButton.visible = topY + 48 >= visibleBody.y && topY + 68 <= visibleBody.bottom();
			return;
		}
		previousSiegeButton.visible = false;
		nextSiegeButton.visible = false;
		lockSiegeButton.visible = false;
		startSiegeButton.visible = false;
		if (!layout.detailVisible) {
			nameField.setVisible(false);
			renameButton.visible = false;
			cycleRoleButton.visible = false;
			locateButton.visible = false;
			return;
		}
		int scrollOffset = layout.mode == LayoutMode.DRAWER ? getClampedDetailScroll(layout) : 0;
		Rect visibleBody = layout.detailBody;
		Rect actions = offsetRect(createDetailLayout(layout).actionsCard, -scrollOffset);

		int fieldX = actions.x + 12;
		int fieldY = actions.y + 28;
		int fieldWidth = actions.width - 24;
		nameField.setX(fieldX);
		nameField.setY(fieldY);
		nameField.setWidth(fieldWidth);
		nameField.setVisible(fieldY >= visibleBody.y && fieldY + 20 <= visibleBody.bottom());

		int buttonY = fieldY + 30;
		renameButton.setX(fieldX);
		renameButton.setY(buttonY);
		renameButton.setWidth(fieldWidth);
		renameButton.visible = buttonY >= visibleBody.y && buttonY + 20 <= visibleBody.bottom();
		cycleRoleButton.setX(fieldX);
		cycleRoleButton.setY(buttonY + 24);
		cycleRoleButton.setWidth(fieldWidth);
		cycleRoleButton.visible = buttonY + 24 >= visibleBody.y && buttonY + 44 <= visibleBody.bottom();
		locateButton.setX(fieldX);
		locateButton.setY(buttonY + 48);
		locateButton.setWidth(fieldWidth);
		locateButton.visible = buttonY + 48 >= visibleBody.y && buttonY + 68 <= visibleBody.bottom();
	}

	private DetailLayout createDetailLayout(Layout layout) {
		Rect body = layout.detailBody;
		int gap = layout.density == LayoutDensity.COMPACT ? 8 : 10;
		if (layout.mode == LayoutMode.DRAWER) {
			int titleHeight = 46;
			int portraitHeight = 120;
			int statsHeight = 88;
			Rect titleCard = new Rect(body.x, body.y, body.width, titleHeight);
			Rect portraitCard = new Rect(body.x, titleCard.bottom() + gap, body.width, portraitHeight);
			Rect statsCard = new Rect(body.x, portraitCard.bottom() + gap, body.width, statsHeight);
			Rect actionsCard = new Rect(body.x, statsCard.bottom() + gap, body.width, Math.max(112, body.bottom() - (statsCard.bottom() + gap)));
			return new DetailLayout(titleCard, portraitCard, statsCard, actionsCard);
		}
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
		int portraitHeight = MathHelper.clamp((body.height - titleHeight - actionsHeight - (gap * 3)) / 2, 96, layout.density == LayoutDensity.WIDE ? 176 : 140);
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
		int topBarHeight = density == LayoutDensity.WIDE ? 120 : (density == LayoutDensity.COMPACT ? 62 : 104);
		Rect topBar = new Rect(frame.x + outerInset, frame.y + outerInset, frame.width - (outerInset * 2), topBarHeight);
		int mainY = topBar.bottom() + PANEL_GAP;
		int mainHeight = frame.bottom() - outerInset - mainY;
		LayoutMode mode = chooseLayoutMode(frame, density, mainHeight);
		if (ledgerMode == LedgerMode.SIEGES) {
			mode = LayoutMode.SIDE_PANEL;
		}
		boolean detailVisible = mode != LayoutMode.DRAWER || detailDrawerOpen;
		Rect mapPanel;
		Rect detailPanel;
		if (mode == LayoutMode.STACKED) {
			int splitGap = density == LayoutDensity.COMPACT ? 10 : 12;
			int detailMinHeight = ledgerMode == LedgerMode.SIEGES
				? (density == LayoutDensity.COMPACT ? 150 : 190)
				: (density == LayoutDensity.COMPACT ? 170 : 220);
			int mapMinHeight = ledgerMode == LedgerMode.SIEGES
				? (density == LayoutDensity.COMPACT ? 150 : 190)
				: 220;
			int maxMapHeight = Math.max(mapMinHeight, mainHeight - detailMinHeight - splitGap);
			int mapHeight = MathHelper.clamp((int) Math.round(mainHeight * 0.58F), mapMinHeight, maxMapHeight);
			int detailHeight = Math.max(detailMinHeight, mainHeight - mapHeight - splitGap);
			if (mapHeight + detailHeight + splitGap > mainHeight) {
				mapHeight = Math.max(120, mainHeight - detailMinHeight - splitGap);
				detailHeight = Math.max(120, mainHeight - mapHeight - splitGap);
			}
			int contentWidth = Math.min(frame.width - (outerInset * 2), STACKED_CONTENT_MAX_WIDTH);
			int contentX = frame.x + ((frame.width - contentWidth) / 2);
			mapPanel = new Rect(contentX, mainY, contentWidth, mapHeight);
			detailPanel = new Rect(contentX, mapPanel.bottom() + splitGap, contentWidth, detailHeight);
		} else if (mode == LayoutMode.DRAWER) {
			int mapWidth = frame.width - (outerInset * 2);
			mapPanel = new Rect(frame.x + outerInset, mainY, mapWidth, mainHeight);
			int detailWidth = MathHelper.clamp(Math.round(mapPanel.width * 0.31F), 300, 360);
			int detailHeight = Math.max(300, mapPanel.height - 28);
			detailPanel = new Rect(mapPanel.right() - detailWidth - 14, mapPanel.y + 14, detailWidth, detailHeight);
		} else {
			int detailWidth = switch (density) {
				case WIDE -> MathHelper.clamp(frame.width / 3, 400, 480);
				case NORMAL -> MathHelper.clamp(frame.width / 4, 320, 400);
				case COMPACT -> ledgerMode == LedgerMode.SIEGES
					? MathHelper.clamp(frame.width / 4, 250, 300)
					: MathHelper.clamp(frame.width / 4, 280, 340);
			};
			int mapWidth = frame.width - detailWidth - PANEL_GAP - (outerInset * 2);
			mapPanel = new Rect(frame.x + outerInset, mainY, mapWidth, mainHeight);
			detailPanel = new Rect(mapPanel.right() + PANEL_GAP, mainY, detailWidth, mainHeight);
		}
		int visibleMapWidth = mode == LayoutMode.DRAWER && detailVisible
			? Math.max(300, detailPanel.x - mapPanel.x - 28)
			: mapPanel.width - 28;
		Rect mapBody = new Rect(mapPanel.x + 14, mapPanel.y + PANEL_HEADER_HEIGHT + 10, visibleMapWidth, mapPanel.height - PANEL_HEADER_HEIGHT - 18);
		Rect detailBody = new Rect(detailPanel.x + 12, detailPanel.y + PANEL_HEADER_HEIGHT + 10, detailPanel.width - 24, detailPanel.height - PANEL_HEADER_HEIGHT - 22);
		return new Layout(frame, topBar, mapPanel, detailPanel, mapBody, detailBody, density, mode, detailVisible);
	}

	private void refreshControls() {
		if (defendersTabButton != null) {
			defendersTabButton.active = ledgerMode != LedgerMode.DEFENDERS;
			siegesTabButton.active = ledgerMode != LedgerMode.SIEGES;
		}
		if (ledgerMode == LedgerMode.SIEGES) {
			nameField.setVisible(false);
			renameButton.visible = false;
			cycleRoleButton.visible = false;
			locateButton.visible = false;
			previousSiegeButton.visible = true;
			nextSiegeButton.visible = true;
			lockSiegeButton.visible = true;
			startSiegeButton.visible = true;
			ArmyLedgerSnapshot.SiegeEntry siege = getSelectedSiege();
			boolean hasSelection = siege != null;
			boolean locked = snapshot.siegeLocked();
			previousSiegeButton.active = hasSelection && !locked && snapshot.sieges().size() > 1;
			nextSiegeButton.active = hasSelection && !locked && snapshot.sieges().size() > 1;
			lockSiegeButton.active = hasSelection && (locked || canLockSelectedSiege(siege));
			lockSiegeButton.setMessage(Text.literal(locked ? "Stand Down" : "Lock Siege"));
			startSiegeButton.active = snapshot.canStartSiege();
			startSiegeButton.setMessage(Text.literal("Start Siege"));
			return;
		}
		previousSiegeButton.visible = false;
		nextSiegeButton.visible = false;
		lockSiegeButton.visible = false;
		startSiegeButton.visible = false;
		ArmyLedgerSnapshot.DefenderEntry defender = getSelectedDefender();
		boolean hasSelection = defender != null;
		boolean visible = createLayout().detailVisible;
		nameField.setVisible(visible);
		nameField.setEditable(hasSelection && visible);
		renameButton.visible = visible;
		renameButton.active = hasSelection && visible;
		cycleRoleButton.visible = visible;
		cycleRoleButton.active = hasSelection && visible;
		locateButton.visible = visible;
		locateButton.active = hasSelection && visible;
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

	private void selectInitialSiege() {
		if (snapshot.sieges().isEmpty()) {
			selectedSiegeIndex = -1;
			return;
		}
		String selectedId = snapshot.selectedSiegeId();
		if (selectedId != null && !selectedId.isBlank()) {
			for (int i = 0; i < snapshot.sieges().size(); i++) {
				if (snapshot.sieges().get(i).id().equals(selectedId)) {
					selectedSiegeIndex = i;
					return;
				}
			}
		}
		selectedSiegeIndex = 0;
	}

	private void selectDefender(int index) {
		selectedIndex = MathHelper.clamp(index, 0, snapshot.defenders().size() - 1);
		lastSelectedDefenderId = snapshot.defenders().get(selectedIndex).entityUuid();
		if (createLayout().mode == LayoutMode.DRAWER) {
			detailDrawerOpen = true;
			detailScroll = 0;
		}
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

	private ArmyLedgerSnapshot.SiegeEntry getSelectedSiege() {
		if (snapshot.sieges().isEmpty() || selectedSiegeIndex < 0) {
			return null;
		}
		selectedSiegeIndex = MathHelper.clamp(selectedSiegeIndex, 0, snapshot.sieges().size() - 1);
		return snapshot.sieges().get(selectedSiegeIndex);
	}

	private void selectSiege(int index) {
		selectedSiegeIndex = MathHelper.clamp(index, 0, snapshot.sieges().size() - 1);
		siegeDetailScroll = 0;
		resetCampaignRouteView();
		refreshControls();
	}

	private void cycleSiegeSelection(int direction) {
		if (snapshot.sieges().isEmpty()) {
			return;
		}
		int next = selectedSiegeIndex < 0 ? 0 : selectedSiegeIndex;
		next = MathHelper.clamp(next + direction, 0, snapshot.sieges().size() - 1);
		selectSiege(next);
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
		float detailScale = MathHelper.clamp(0.85F + (mapZoom * 0.9F), 1.0F, 3.6F);
		int samplesX = MathHelper.clamp(Math.round((body.width / 7.0F) * detailScale), 40, 240);
		int samplesZ = MathHelper.clamp(Math.round((body.height / 7.0F) * detailScale), 30, 180);
		long tick = world == null ? -1L : world.getTime() / 10L;
		if (cachedMapBounds != null && cachedMapBounds.equals(bounds) && cachedSamplesX == samplesX && cachedSamplesZ == samplesZ && cachedMapTick == tick) {
			return;
		}

		cachedMapBounds = bounds;
		cachedSamplesX = samplesX;
		cachedSamplesZ = samplesZ;
		cachedMapTick = tick;
		cachedMapColors = new int[samplesX * samplesZ];
		cachedMapKinds = new byte[samplesX * samplesZ];

		for (int sampleZ = 0; sampleZ < samplesZ; sampleZ++) {
			float zProgress = (sampleZ + 0.5F) / samplesZ;
			int worldZ = MathHelper.floor(MathHelper.lerp(zProgress, bounds.minZ, bounds.maxZ));
			for (int sampleX = 0; sampleX < samplesX; sampleX++) {
				float xProgress = (sampleX + 0.5F) / samplesX;
				int worldX = MathHelper.floor(MathHelper.lerp(xProgress, bounds.minX, bounds.maxX));
				MapSample sample = sampleMapColor(world, worldX, worldZ, snapshot.bannerPos().getY(), bounds);
				int index = sampleZ * samplesX + sampleX;
				cachedMapColors[index] = sample.color();
				cachedMapKinds[index] = sample.kind();
			}
		}
	}

	private MapSample sampleMapColor(ClientWorld world, int x, int z, int fallbackY, MapView bounds) {
		if (world == null) {
			return new MapSample(0xFF2B3C2F, (byte) SAMPLE_NATURAL);
		}
		int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
		int sampleY = Math.max(world.getBottomY(), topY > world.getBottomY() ? topY : fallbackY);
		BlockPos samplePos = new BlockPos(x, sampleY, z);
		BlockState state = world.getBlockState(samplePos);
		byte kind = classifyBlock(state);
		MapColor mapColor = state.getMapColor(world, samplePos);
		int baseColor = mapColor == MapColor.CLEAR ? fallbackBlockColor(state, kind) : tintBlockColor((mapColor.color | 0xFF000000), kind);

		int northY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z - 1) - 1;
		int southY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z + 1) - 1;
		int eastY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x + 1, z) - 1;
		int westY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x - 1, z) - 1;
		int slope = (southY - northY) + (eastY - westY);
		float shade = 1.0F + MathHelper.clamp(slope * 0.05F, -0.16F, 0.16F);
		float distance = (Math.abs(x - snapshot.bannerPos().getX()) + Math.abs(z - snapshot.bannerPos().getZ()))
			/ (float) Math.max(1, (bounds.maxX - bounds.minX) + (bounds.maxZ - bounds.minZ));
		float focus = 1.02F - (distance * 0.03F);
		float contrast = switch (kind) {
			case SAMPLE_PATH, SAMPLE_WOOD, SAMPLE_STONE, SAMPLE_CROP -> 1.08F;
			case SAMPLE_WATER, SAMPLE_SAND -> 1.04F;
			default -> 1.0F;
		};
		return new MapSample(shadeColor(baseColor, shade * focus * contrast), kind);
	}

	private byte classifyBlock(BlockState state) {
		String id = String.valueOf(Registries.BLOCK.getId(state.getBlock()));
		if (id.contains("water")) {
			return (byte) SAMPLE_WATER;
		}
		if (id.contains("path") || id.contains("road")) {
			return (byte) SAMPLE_PATH;
		}
		if (id.contains("farmland") || id.contains("crop") || id.contains("wheat") || id.contains("carrot") || id.contains("potato") || id.contains("beetroot")) {
			return (byte) SAMPLE_CROP;
		}
		if (id.contains("sand") || id.contains("gravel")) {
			return (byte) SAMPLE_SAND;
		}
		if (id.contains("brick") || id.contains("stone") || id.contains("cobblestone") || id.contains("deepslate")) {
			return (byte) SAMPLE_STONE;
		}
		if (id.contains("log") || id.contains("planks") || id.contains("wood") || id.contains("fence") || id.contains("slab") || id.contains("stairs")) {
			return (byte) SAMPLE_WOOD;
		}
		if (id.contains("leaves") || id.contains("moss") || id.contains("vine")) {
			return (byte) SAMPLE_FOLIAGE;
		}
		return (byte) SAMPLE_NATURAL;
	}

	private int fallbackBlockColor(BlockState state, byte kind) {
		String id = String.valueOf(Registries.BLOCK.getId(state.getBlock()));
		if (id.contains("lava")) {
			return 0xFFB35A2E;
		}
		return switch (kind) {
			case SAMPLE_WATER -> 0xFF5A84B7;
			case SAMPLE_PATH -> 0xFFC2A368;
			case SAMPLE_CROP -> 0xFF79A93A;
			case SAMPLE_SAND -> 0xFFD9C38F;
			case SAMPLE_STONE -> 0xFF8C8C8A;
			case SAMPLE_WOOD -> 0xFF8B6A3C;
			case SAMPLE_FOLIAGE -> 0xFF587D43;
			default -> {
				if (id.contains("dirt") || id.contains("mud") || id.contains("farmland")) {
					yield 0xFF8D6A48;
				}
				if (id.contains("grass")) {
					yield 0xFF7DA63C;
				}
				yield 0xFFB8AA8E;
			}
		};
	}

	private int tintBlockColor(int color, byte kind) {
		return switch (kind) {
			case SAMPLE_PATH -> blendColor(color, 0xFFC8A86A, 0.45F);
			case SAMPLE_WOOD -> blendColor(color, 0xFF8B6738, 0.35F);
			case SAMPLE_STONE -> blendColor(color, 0xFF888B90, 0.30F);
			case SAMPLE_WATER -> blendColor(color, 0xFF5A82B4, 0.35F);
			case SAMPLE_SAND -> blendColor(color, 0xFFD7C08A, 0.30F);
			case SAMPLE_FOLIAGE -> blendColor(color, 0xFF5E8940, 0.25F);
			case SAMPLE_CROP -> blendColor(color, 0xFF7DAE37, 0.35F);
			default -> blendColor(color, 0xFF84B13C, 0.08F);
		};
	}

	private int blendColor(int color, int overlay, float amount) {
		int alpha = (color >>> 24) & 0xFF;
		int red = (color >>> 16) & 0xFF;
		int green = (color >>> 8) & 0xFF;
		int blue = color & 0xFF;
		int overlayRed = (overlay >>> 16) & 0xFF;
		int overlayGreen = (overlay >>> 8) & 0xFF;
		int overlayBlue = overlay & 0xFF;
		red = MathHelper.clamp(Math.round(MathHelper.lerp(amount, red, overlayRed)), 0, 255);
		green = MathHelper.clamp(Math.round(MathHelper.lerp(amount, green, overlayGreen)), 0, 255);
		blue = MathHelper.clamp(Math.round(MathHelper.lerp(amount, blue, overlayBlue)), 0, 255);
		return (alpha << 24) | (red << 16) | (green << 8) | blue;
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

	private Rect offsetRect(Rect rect, int offsetY) {
		return new Rect(rect.x, rect.y + offsetY, rect.width, rect.height);
	}

	private int getClampedDetailScroll(Layout layout) {
		int maxScroll = getDetailScrollMax(layout);
		detailScroll = MathHelper.clamp(detailScroll, 0, maxScroll);
		return detailScroll;
	}

	private int getClampedSiegeListScroll(Layout layout) {
		int maxScroll = getSiegeListScrollMax(layout);
		siegeListScroll = MathHelper.clamp(siegeListScroll, 0, maxScroll);
		return siegeListScroll;
	}

	private int getClampedSiegeDetailScroll(Layout layout) {
		int maxScroll = getSiegeDetailScrollMax(layout);
		siegeDetailScroll = MathHelper.clamp(siegeDetailScroll, 0, maxScroll);
		return siegeDetailScroll;
	}

	private int getDetailScrollMax(Layout layout) {
		if (layout.mode != LayoutMode.DRAWER || !layout.detailVisible) {
			return 0;
		}
		int contentBottom = ledgerMode == LedgerMode.SIEGES
			? createSiegeDetailLayout(layout).actionsCard().bottom()
			: createDetailLayout(layout).actionsCard.bottom();
		int contentHeight = contentBottom - layout.detailBody.y;
		return Math.max(0, contentHeight - layout.detailBody.height);
	}

	private int getSiegeListScrollMax(Layout layout) {
		return Math.max(0, getSiegeListContentHeight(layout) - layout.mapBody.height);
	}

	private int getSiegeDetailScrollMax(Layout layout) {
		if (!layout.detailVisible) {
			return 0;
		}
		return Math.max(0, getSiegeDetailContentHeight(layout) - layout.detailBody.height);
	}

	private int getSiegeListContentHeight(Layout layout) {
		int gap = 10;
		int count = snapshot.sieges().size();
		if (count <= 0) {
			return layout.mapBody.height;
		}
		return (count * siegeCardHeight()) + ((count - 1) * gap);
	}

	private int getSiegeDetailContentHeight(Layout layout) {
		return createSiegeDetailLayout(layout).actionsCard().bottom() - layout.detailBody.y;
	}

	private int siegeCardHeight() {
		return 96;
	}

	private void drawDetailScrollbar(DrawContext context, Layout layout, int contentBottom) {
		int maxScroll = getDetailScrollMax(layout);
		if (maxScroll <= 0) {
			return;
		}
		Rect body = layout.detailBody;
		int trackX = body.right() - 4;
		context.fill(trackX, body.y, trackX + 2, body.bottom(), CARD_BORDER);
		int contentHeight = contentBottom - body.y;
		int thumbHeight = Math.max(24, Math.round(body.height * (body.height / (float) Math.max(body.height, contentHeight))));
		int travel = Math.max(1, body.height - thumbHeight);
		int thumbY = body.y + Math.round((getClampedDetailScroll(layout) / (float) maxScroll) * travel);
		context.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbHeight, TEXT_SECONDARY);
	}

	private void drawPanelScrollbar(DrawContext context, Rect body, int scroll, int maxScroll, int contentHeight) {
		if (maxScroll <= 0) {
			return;
		}
		int trackX = body.right() - 4;
		context.fill(trackX, body.y, trackX + 2, body.bottom(), CARD_BORDER);
		int thumbHeight = Math.max(24, Math.round(body.height * (body.height / (float) Math.max(body.height, contentHeight))));
		int travel = Math.max(1, body.height - thumbHeight);
		int thumbY = body.y + Math.round((scroll / (float) maxScroll) * travel);
		context.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbHeight, TEXT_SECONDARY);
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

	private void sendLockOrCancelSiege() {
		if (snapshot.siegeLocked()) {
			ClientPlayNetworking.send(ArmyLedgerService.CANCEL_SIEGE_PACKET, PacketByteBufs.create());
			return;
		}
		ArmyLedgerSnapshot.SiegeEntry siege = getSelectedSiege();
		if (siege == null) {
			return;
		}
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeString(siege.id(), 64);
		ClientPlayNetworking.send(ArmyLedgerService.LOCK_SIEGE_PACKET, buf);
	}

	private void sendStartSiege() {
		ClientPlayNetworking.send(ArmyLedgerService.START_SIEGE_PACKET, PacketByteBufs.create());
	}

	private void setLedgerMode(LedgerMode ledgerMode) {
		this.ledgerMode = ledgerMode;
		lastLedgerMode = ledgerMode;
		if (ledgerMode == LedgerMode.SIEGES) {
			siegeListScroll = 0;
			siegeDetailScroll = 0;
			resetCampaignRouteView();
		} else {
			detailScroll = 0;
		}
		syncWidgetLayout();
		refreshControls();
	}

	private void resetCampaignRouteView() {
		campaignRouteZoom = 1.0F;
		campaignRoutePanX = 0.0F;
		campaignRoutePanY = 0.0F;
		campaignRouteDragging = false;
	}

	private LedgerMode defaultLedgerMode() {
		if (snapshot.siegeLocked() || !isPeacePhase()) {
			return LedgerMode.SIEGES;
		}
		return lastLedgerMode;
	}

	private boolean canLockSelectedSiege(ArmyLedgerSnapshot.SiegeEntry siege) {
		return siege != null
			&& !snapshot.siegeLocked()
			&& isPeacePhase()
			&& snapshot.hasBase()
			&& snapshot.hasRally()
			&& siege.unlocked();
	}

	private String selectedSiegeStatus(ArmyLedgerSnapshot.SiegeEntry siege) {
		if (siege == null) {
			return snapshot.siegeStatus();
		}
		if (!snapshot.hasBase()) {
			return "Place a Settlement Standard to unlock campaign planning.";
		}
		if (!snapshot.hasRally()) {
			return "Place a Raid Rally Banner to define the attacker formation point.";
		}
		if (snapshot.siegeLocked()) {
			return "Formation locked. The wave is staged at the rally point and waiting for Start Siege.";
		}
		if (!isPeacePhase()) {
			return "A siege is already active. Finish it before planning another.";
		}
		if (!siege.unlocked()) {
			return "That siege unlocks after " + siege.unlockVictories() + " total victories.";
		}
		if (siege.replay()) {
			return "Completed route selected. Rewards still drop, but settlement age progress will not advance.";
		}
		return snapshot.siegeStatus();
	}

	private boolean isPeacePhase() {
		return "at peace".equalsIgnoreCase(snapshot.siegePhase());
	}

	private int siegeAccentColor(ArmyLedgerSnapshot.SiegeEntry siege) {
		return switch (siege.ageLevel()) {
			case 0 -> 0xFF6B8A54;
			case 1 -> 0xFF9A7848;
			case 2 -> 0xFFAD5548;
			default -> 0xFF8B6BAF;
		};
	}

	private String ageLabel(int ageLevel) {
		return switch (ageLevel) {
			case 0 -> "Homestead";
			case 1 -> "Fortified";
			case 2 -> "Ironkeep";
			default -> "Industry";
		};
	}

	private String ageShortLabel(int ageLevel) {
		return switch (ageLevel) {
			case 0 -> "H";
			case 1 -> "F";
			case 2 -> "I";
			default -> "EI";
		};
	}

	private int ageUnlockThreshold(int ageLevel) {
		if (ageLevel <= 0) {
			return 0;
		}
		int threshold = Integer.MAX_VALUE;
		for (ArmyLedgerSnapshot.SiegeEntry siege : snapshot.sieges()) {
			if (siege.ageLevel() == ageLevel) {
				threshold = Math.min(threshold, Math.max(0, siege.unlockVictories()));
			}
		}
		return threshold == Integer.MAX_VALUE ? 0 : threshold;
	}

	private int replayableSiegeCountForAge(int ageLevel) {
		int count = 0;
		for (ArmyLedgerSnapshot.SiegeEntry siege : snapshot.sieges()) {
			if (siege.ageLevel() == ageLevel && siege.replay() && siege.unlocked()) {
				count++;
			}
		}
		return count;
	}

	private SiegeDetailLayout createSiegeDetailLayout(Layout layout) {
		Rect body = layout.detailBody;
		int gap = layout.density == LayoutDensity.COMPACT ? 8 : 10;
		if (layout.mode == LayoutMode.DRAWER) {
			int titleHeight = 58;
			int overviewHeight = 152;
			int campaignHeight = 0;
			int actionsHeight = 176;
			Rect titleCard = new Rect(body.x, body.y, body.width, titleHeight);
			Rect overviewCard = new Rect(body.x, titleCard.bottom() + gap, body.width, overviewHeight);
			Rect campaignCard = new Rect(body.x, overviewCard.bottom() + gap, body.width, campaignHeight);
			Rect actionsCard = new Rect(body.x, campaignCard.bottom() + gap, body.width, actionsHeight);
			return new SiegeDetailLayout(titleCard, overviewCard, campaignCard, actionsCard);
		}
		int titleHeight = 58;
		int campaignHeight = 0;
		int actionsHeight = 176;
		int overviewHeight = Math.max(148, body.height - titleHeight - campaignHeight - actionsHeight - (gap * 3));
		Rect titleCard = new Rect(body.x, body.y, body.width, titleHeight);
		Rect overviewCard = new Rect(body.x, titleCard.bottom() + gap, body.width, overviewHeight);
		Rect campaignCard = new Rect(body.x, overviewCard.bottom() + gap, body.width, campaignHeight);
		Rect actionsCard = new Rect(body.x, campaignCard.bottom() + gap, body.width, Math.max(actionsHeight, body.bottom() - campaignCard.bottom() - gap));
		return new SiegeDetailLayout(titleCard, overviewCard, campaignCard, actionsCard);
	}

	private void drawTrimmed(DrawContext context, String text, int x, int y, int maxWidth, int color) {
		String safe = safeText(text, "");
		float scale = bodyScale(layoutDensity());
		int trimWidth = Math.max(1, (int) (maxWidth / scale));
		OrderedText trimmed = Language.getInstance().reorder(this.textRenderer.trimToWidth(Text.literal(safe), trimWidth));
		drawScaledOrderedText(context, trimmed, x, y, color, scale);
	}

	private void drawWrapped(DrawContext context, String text, int x, int y, int maxWidth, int color, int maxLines) {
		float scale = bodyScale(layoutDensity());
		int trimWidth = Math.max(1, (int) (maxWidth / scale));
		List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(safeText(text, "")), trimWidth);
		int lineY = y;
		for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
			drawScaledOrderedText(context, lines.get(i), x, lineY, color, scale);
			lineY += Math.round(10 * scale) + 2;
		}
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
		int shortSide = Math.min(this.width, this.height);
		if (this.width >= 1400 && shortSide >= 620) {
			return LayoutDensity.WIDE;
		}
		if (this.width <= 980 || shortSide <= 520) {
			return LayoutDensity.COMPACT;
		}
		return LayoutDensity.NORMAL;
	}

	private LayoutMode chooseLayoutMode(Rect frame, LayoutDensity density, int mainHeight) {
		if (density == LayoutDensity.COMPACT && frame.width >= 900) {
			return LayoutMode.DRAWER;
		}
		int minMapWidth = switch (density) {
			case WIDE -> 660;
			case NORMAL -> 560;
			case COMPACT -> 500;
		};
		int minDetailWidth = switch (density) {
			case WIDE -> 400;
			case NORMAL -> 320;
			case COMPACT -> 280;
		};
		int minimumSideWidth = minMapWidth + minDetailWidth + PANEL_GAP + 24;
		if (frame.width < minimumSideWidth) {
			return LayoutMode.STACKED;
		}
		if (mainHeight <= 360) {
			return LayoutMode.STACKED;
		}
		return LayoutMode.SIDE_PANEL;
	}

	private int computeEntityScale(Layout layout, Rect portraitCard) {
		int maxFromWidth = Math.max(28, portraitCard.width / 3);
		int maxFromHeight = Math.max(28, portraitCard.height / 2);
		int preferred = layout.mode == LayoutMode.STACKED
			? 44
			: switch (layout.density) {
				case WIDE -> 58;
				case NORMAL -> 46;
				case COMPACT -> layout.mode == LayoutMode.DRAWER ? 34 : 38;
			};
		return MathHelper.clamp(preferred, 28, Math.min(maxFromWidth, maxFromHeight));
	}

	private int computeActionsHeight(Layout layout, int availableWidth) {
		return 22 + 20 + 8 + (3 * 24);
	}

	private float titleScale(LayoutDensity density) {
		return switch (density) {
			case COMPACT -> 0.96F;
			case NORMAL -> 1.10F;
			case WIDE -> 1.28F;
		};
	}

	private float bodyScale(LayoutDensity density) {
		return switch (density) {
			case COMPACT -> 0.88F;
			case NORMAL -> 1.00F;
			case WIDE -> 1.16F;
		};
	}

	private float metaScale(LayoutDensity density) {
		return switch (density) {
			case COMPACT -> 0.80F;
			case NORMAL -> 0.94F;
			case WIDE -> 1.06F;
		};
	}

	private int statRowHeight(LayoutDensity density) {
		return switch (density) {
			case COMPACT -> 14;
			case NORMAL -> 18;
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

	private record Layout(Rect frame, Rect topBar, Rect mapPanel, Rect detailPanel, Rect mapBody, Rect detailBody, LayoutDensity density, LayoutMode mode, boolean detailVisible) {
	}

	private record DetailLayout(Rect titleCard, Rect portraitCard, Rect statsCard, Rect actionsCard) {
	}

	private record SiegeDetailLayout(Rect titleCard, Rect overviewCard, Rect campaignCard, Rect actionsCard) {
	}

	private record BaseBounds(int minX, int maxX, int minZ, int maxZ) {
	}

	private record MapView(float minX, float maxX, float minZ, float maxZ) {
	}

	private record Point(int x, int y) {
	}

	private record Marker(int defenderIndex, int centerX, int centerY, int radius) {
	}

	private record MapSample(int color, byte kind) {
	}
}
