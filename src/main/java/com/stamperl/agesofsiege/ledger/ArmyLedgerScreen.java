package com.stamperl.agesofsiege.ledger;

import com.stamperl.agesofsiege.AgesOfSiegeMod;
import com.stamperl.agesofsiege.AntiqueAtlasCompat;
import com.stamperl.agesofsiege.defense.DefenderRole;
import com.stamperl.agesofsiege.item.ModItems;
import com.stamperl.agesofsiege.siege.SiegeCatalog;
import com.stamperl.agesofsiege.siege.runtime.UnitRole;
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
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ArmyLedgerScreen extends Screen {
	private static final int SCREEN_MARGIN_X = 28;
	private static final int SCREEN_MARGIN_Y = 22;
	private static final int PANEL_GAP = 14;
	private static final int PANEL_HEADER_HEIGHT = 40;
	private static final int SIEGE_BUTTON_FOOTER_GAP = 10;
	private static final int SIEGE_BUTTON_FOOTER_HEIGHT = 132;
	private static final int DETAIL_PANEL_WIDTH_MIN = 360;
	private static final int DETAIL_PANEL_WIDTH_MAX = 440;
	private static final int MAX_FRAME_WIDTH = 1400;
	private static final int MAX_FRAME_HEIGHT = 900;
	private static final int MIN_FRAME_WIDTH = 760;
	private static final int MIN_FRAME_HEIGHT = 480;
	private static final int STACKED_CONTENT_MAX_WIDTH = 1160;
	private static final float LEDGER_TARGET_SCALE = 2.0F;
	private static final float MAP_ZOOM_MIN = 1.0F;
	private static final float MAP_ZOOM_MAX = 4.0F;
	private static final float MAP_ZOOM_STEP = 0.25F;
	private static final float CAMPAIGN_ROUTE_ZOOM_MIN = 0.85F;
	private static final float CAMPAIGN_ROUTE_ZOOM_MAX = 2.2F;
	private static final float CAMPAIGN_ROUTE_ZOOM_STEP = 0.14F;
	private static final int DEBUG_OVERLAY_BG = 0xD0101418;
	private static final int DEBUG_OVERLAY_BORDER = 0xA0C88452;
	private static final int DEBUG_FRAME = 0xC0E47F63;
	private static final int DEBUG_MAP = 0xC05CC8A6;
	private static final int DEBUG_DETAIL = 0xC06B9CDA;
	private static final int DEBUG_VIEWPORT = 0xC0D6C49E;

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
	private static final int MAP_GRID_MINOR = 0x103A4753;
	private static final int MAP_GRID_MAJOR = 0x244A5A67;
	private static final int MAP_CONTOUR_LIGHT = 0x2ED8E3EA;
	private static final int MAP_CONTOUR_DARK = 0x2A11181E;
	private static final int MARKER_ARCHER = 0xFF5F83A8;
	private static final int MARKER_SOLDIER = 0xFFC7B88F;
	private static final int MARKER_SELECTED = 0xFFAD5548;
	private static final int MARKER_OFFLINE = 0xFF6E7177;
	private static final int MARKER_POST = 0x80FFFFFF;
	private static final int MARKER_BANNER = 0xFFB89B54;
	private static final int MARKER_RALLY = 0xFFD05E56;
	private static final int MARKER_ATTACKER = 0xFFD8B86E;
	private static final int MARKER_ATTACKER_BG = 0xD0151A1F;
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
	private int[] cachedMapHeights = new int[0];
	private int cachedMapOriginX;
	private int cachedMapOriginZ;
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
	private boolean debugOverlayEnabled;
	private long lastDebugLogAt;
	private String lastDebugLayoutSignature = "";

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
		this.locateButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Open Tactical Atlas"), button -> openAtlas())
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
		int ledgerMouseX = toLedgerX(mouseX);
		int ledgerMouseY = toLedgerY(mouseY);
		context.fill(0, 0, this.width, this.height, SCRIM_COLOR);
		context.getMatrices().push();
		float ledgerScale = ledgerUiScaleFactor();
		context.getMatrices().scale(ledgerScale, ledgerScale, 1.0F);
		drawFrame(context, layout.frame);
		drawTopBar(context, layout);
		drawMapPanel(context, layout, ledgerMouseX, ledgerMouseY);
		if (layout.detailVisible) {
			drawDetailPanel(context, layout, ledgerMouseX, ledgerMouseY);
		}
		if (debugOverlayEnabled) {
			drawDebugOverlay(context, layout, ledgerMouseX, ledgerMouseY);
			logDebugLayout(layout);
		}
		context.getMatrices().pop();

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_F8) {
			debugOverlayEnabled = !debugOverlayEnabled;
			if (debugOverlayEnabled) {
				lastDebugLayoutSignature = "";
				lastDebugLogAt = 0L;
				AgesOfSiegeMod.LOGGER.info("Army ledger debug overlay enabled.");
			} else {
				AgesOfSiegeMod.LOGGER.info("Army ledger debug overlay disabled.");
			}
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		double ledgerMouseX = toLedgerCoord(mouseX);
		double ledgerMouseY = toLedgerCoord(mouseY);
		if (ledgerMode == LedgerMode.SIEGES) {
			Layout layout = createLayout();
			if (button == 0) {
				int chapterAge = findChapterHit(layout, ledgerMouseX, ledgerMouseY);
				if (chapterAge >= 0) {
					selectSiegeChapter(chapterAge);
					debugLogInteraction("chapter-click", layout, ledgerMouseX, ledgerMouseY);
					return true;
				}
			}
			if (button == 0 && contains(campaignRouteViewport(layout), ledgerMouseX, ledgerMouseY)) {
				int siegeIndex = findSiegeHit(ledgerMouseX, ledgerMouseY);
				if (siegeIndex >= 0) {
					selectSiege(siegeIndex);
					debugLogInteraction("route-click", layout, ledgerMouseX, ledgerMouseY);
					return true;
				}
			}
			if (!contains(layout.mapBody, ledgerMouseX, ledgerMouseY)) {
				return super.mouseClicked(mouseX, mouseY, button);
			}
			return super.mouseClicked(mouseX, mouseY, button);
		}
		int markerIndex = findMarkerHit(ledgerMouseX, ledgerMouseY);
		if (markerIndex >= 0) {
			selectDefender(markerIndex);
			return true;
		}
		if (button == 0) {
			Rect mapBody = createLayout().mapBody;
			if (contains(mapBody, ledgerMouseX, ledgerMouseY)) {
				mapDragging = true;
				mapDragLastX = ledgerMouseX;
				mapDragLastY = ledgerMouseY;
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
		if (ledgerMode == LedgerMode.SIEGES) {
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
		double ledgerMouseX = toLedgerCoord(mouseX);
		double ledgerMouseY = toLedgerCoord(mouseY);
		mapPanX -= (float) ((ledgerMouseX - mapDragLastX) * worldPerPixelX);
		mapPanZ -= (float) ((ledgerMouseY - mapDragLastY) * worldPerPixelZ);
		mapDragLastX = ledgerMouseX;
		mapDragLastY = ledgerMouseY;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		double ledgerMouseX = toLedgerCoord(mouseX);
		double ledgerMouseY = toLedgerCoord(mouseY);
		Layout layout = createLayout();
		if (ledgerMode == LedgerMode.SIEGES) {
			if (layout.detailVisible && contains(layout.detailBody, ledgerMouseX, ledgerMouseY)) {
				int maxScroll = getSiegeDetailScrollMax(layout);
				if (maxScroll > 0) {
					siegeDetailScroll = MathHelper.clamp(siegeDetailScroll - (int) Math.round(verticalAmount * 20.0D), 0, maxScroll);
					syncWidgetLayout();
					return true;
				}
			}
			return super.mouseScrolled(mouseX, mouseY, verticalAmount);
		}
		if (layout.detailVisible && layout.mode == LayoutMode.DRAWER && contains(layout.detailBody, ledgerMouseX, ledgerMouseY)) {
			int maxScroll = getDetailScrollMax(layout);
			if (maxScroll > 0) {
				detailScroll = MathHelper.clamp(detailScroll - (int) Math.round(verticalAmount * 20.0D), 0, maxScroll);
				syncWidgetLayout();
				return true;
			}
		}
		Rect body = layout.mapBody;
		if (!contains(body, ledgerMouseX, ledgerMouseY) || verticalAmount == 0.0D) {
			return super.mouseScrolled(mouseX, mouseY, verticalAmount);
		}

		MapView currentView = computeMapView(body);
		float normalizedX = MathHelper.clamp((float) ((ledgerMouseX - body.x) / Math.max(1.0, body.width)), 0.0F, 1.0F);
		float normalizedZ = MathHelper.clamp((float) ((ledgerMouseY - body.y) / Math.max(1.0, body.height)), 0.0F, 1.0F);
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
		float paddedWidth = Math.max(18.0F, (baseBounds.maxX - baseBounds.minX) + 10.0F);
		float paddedHeight = Math.max(18.0F, (baseBounds.maxZ - baseBounds.minZ) + 10.0F);
		float viewWidth = Math.max(12.0F, Math.max(paddedWidth, paddedHeight * aspect) / mapZoom);
		float viewHeight = Math.max(12.0F, Math.max(paddedHeight, paddedWidth / Math.max(0.01F, aspect)) / mapZoom);
		float centerX = worldX - ((normalizedX - 0.5F) * viewWidth);
		float centerZ = worldZ - ((normalizedZ - 0.5F) * viewHeight);
		mapPanX = centerX - baseCenterX;
		mapPanZ = centerZ - baseCenterZ;
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
				: snapshot.ageProgressWins() + "/" + snapshot.ageProgressTarget() + " regular wins to age siege";
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
		drawEmbeddedTacticalBoard(context, layout, mouseX, mouseY);
	}

	private void drawEmbeddedTacticalBoard(DrawContext context, Layout layout, int mouseX, int mouseY) {
		Rect panel = layout.mapPanel;
		Rect body = layout.mapBody;
		context.fill(panel.x, panel.y, panel.right(), panel.bottom(), PANEL_COLOR);
		context.drawBorder(panel.x, panel.y, panel.width, panel.height, PANEL_BORDER);
		context.fill(panel.x, panel.y, panel.right(), panel.y + PANEL_HEADER_HEIGHT, PANEL_HEADER_COLOR);
		context.fill(panel.x + 12, panel.y + PANEL_HEADER_HEIGHT, panel.right() - 12, panel.y + PANEL_HEADER_HEIGHT + 1, CARD_BORDER);
		drawTrimmed(context, "Tactical Board", panel.x + 14, panel.y + 13, 180, TEXT_PRIMARY);
		if (layout.density != LayoutDensity.COMPACT) {
			drawScaledText(context, String.format("Zoom %.2fx  •  Wheel to zoom  •  Drag to pan", mapZoom), panel.right() - 250, panel.y + 15, TEXT_SECONDARY, metaScale(layout.density));
		}

		context.fill(body.x, body.y, body.right(), body.bottom(), 0xFF253A29);
		context.drawBorder(body.x, body.y, body.width, body.height, PANEL_BORDER);
		drawMapGrid(context, body);
		drawTacticalFootprint(context, body);
		drawBannerAndPosts(context, body);
		drawAttackerMarkers(context, body);
		drawGuardMarkers(context, body, mouseX, mouseY);
	}

	private void drawTacticalFootprint(DrawContext context, Rect body) {
		MapView bounds = computeMapView(body);
		if (snapshot.defenders().isEmpty()) {
			Point banner = worldToMap(snapshot.bannerPos(), body, bounds);
			context.fill(banner.x - 18, banner.y - 18, banner.x + 18, banner.y + 18, 0x202A3321);
			return;
		}
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (ArmyLedgerSnapshot.DefenderEntry defender : snapshot.defenders()) {
			minX = Math.min(minX, defender.homePost().getX());
			maxX = Math.max(maxX, defender.homePost().getX());
			minZ = Math.min(minZ, defender.homePost().getZ());
			maxZ = Math.max(maxZ, defender.homePost().getZ());
		}
		int x0 = worldToMapX(minX - 0.5F, body, bounds);
		int x1 = worldToMapX(maxX + 1.5F, body, bounds);
		int y0 = worldToMapY(minZ - 0.5F, body, bounds);
		int y1 = worldToMapY(maxZ + 1.5F, body, bounds);
		context.fill(x0, y0, x1, y1, 0x1E9FBC4A);
		context.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0x1824351A);
		int wall = Math.max(2, Math.min(8, Math.round((x1 - x0) / Math.max(6.0F, maxX - minX + 2.0F))));
		context.fill(x0, y0, x1, y0 + wall, 0xFFAA8249);
		context.fill(x0, y1 - wall, x1, y1, 0xFFAA8249);
		context.fill(x0, y0, x0 + wall, y1, 0xFFAA8249);
		context.fill(x1 - wall, y0, x1, y1, 0xFFAA8249);
		context.drawBorder(x0, y0, x1 - x0, y1 - y0, 0x70463318);
	}
	private void drawAtlasLaunchPanel(DrawContext context, Layout layout) {
		Rect panel = layout.mapPanel;
		Rect body = layout.mapBody;
		context.fill(panel.x, panel.y, panel.right(), panel.bottom(), PANEL_COLOR);
		context.drawBorder(panel.x, panel.y, panel.width, panel.height, PANEL_BORDER);
		context.fill(panel.x, panel.y, panel.right(), panel.y + PANEL_HEADER_HEIGHT, PANEL_HEADER_COLOR);
		context.fill(panel.x + 12, panel.y + PANEL_HEADER_HEIGHT, panel.right() - 12, panel.y + PANEL_HEADER_HEIGHT + 1, CARD_BORDER);
		drawTrimmed(context, "Tactical Atlas", panel.x + 14, panel.y + 13, 180, TEXT_PRIMARY);
		drawTrimmed(context, "Open Antique Atlas centered on your banner or selected defender post.", panel.right() - 410, panel.y + 15, 390, TEXT_SECONDARY);

		context.fill(body.x, body.y, body.right(), body.bottom(), MAP_BG);
		context.drawBorder(body.x, body.y, body.width, body.height, PANEL_BORDER);

		int inset = 18;
		int gap = 12;
		int leftWidth = Math.max(240, Math.min(360, body.width / 2));
		int summaryHeight = 74;
		Rect settlementCard = new Rect(body.x + inset, body.y + inset, leftWidth, summaryHeight);
		Rect focusCard = new Rect(settlementCard.x, settlementCard.bottom() + gap, leftWidth, summaryHeight);
		Rect noteCard = new Rect(settlementCard.x, focusCard.bottom() + gap, leftWidth, 92);
		Rect launchCard = new Rect(settlementCard.x, noteCard.bottom() + gap, leftWidth, Math.max(116, body.bottom() - noteCard.bottom() - gap - inset));
		Rect previewCard = new Rect(launchCard.right() + gap, body.y + inset, Math.max(220, body.right() - launchCard.right() - gap - inset), body.height - (inset * 2));

		drawInfoCard(context, settlementCard, "Settlement", safeText(snapshot.ownerName(), "Unknown"));
		drawInfoCard(context, focusCard, "Atlas Focus", atlasFocusSummary());
		drawAtlasNoteCard(context, noteCard);
		drawAtlasLaunchCard(context, launchCard);
		drawAtlasPreviewCard(context, previewCard);
	}

	private void drawAtlasNoteCard(DrawContext context, Rect rect) {
		context.fill(rect.x, rect.y, rect.right(), rect.bottom(), CARD_COLOR);
		context.drawBorder(rect.x, rect.y, rect.width, rect.height, CARD_BORDER);
		drawScaledText(context, "HOW IT WORKS", rect.x + 10, rect.y + 8, TEXT_SECONDARY, 0.72F);
		drawWrapped(context, "Atlas handles the heavy map rendering and cache. The ledger stays focused on commands and guard controls.", rect.x + 10, rect.y + 24, rect.width - 20, TEXT_PRIMARY, 0);
	}

	private void drawAtlasLaunchCard(DrawContext context, Rect rect) {
		context.fill(rect.x, rect.y, rect.right(), rect.bottom(), CARD_COLOR);
		context.drawBorder(rect.x, rect.y, rect.width, rect.height, CARD_BORDER);
		drawScaledText(context, "TACTICAL ACTION", rect.x + 10, rect.y + 8, TEXT_SECONDARY, 0.72F);
		String availability = AntiqueAtlasCompat.isAvailable()
			? "Atlas is installed in this instance."
			: "Atlas is not available in this instance.";
		drawWrapped(context, availability, rect.x + 10, rect.y + 24, rect.width - 20, AntiqueAtlasCompat.isAvailable() ? TEXT_WARM : MARKER_SELECTED, 0);
		drawWrapped(context, "Opening Atlas will center on the selected defender post. If no guard is selected, it will center on the settlement banner.", rect.x + 10, rect.y + 44, rect.width - 20, TEXT_PRIMARY, 0);
	}

	private void drawAtlasPreviewCard(DrawContext context, Rect rect) {
		context.fill(rect.x, rect.y, rect.right(), rect.bottom(), 0xFF11171C);
		context.drawBorder(rect.x, rect.y, rect.width, rect.height, CARD_BORDER);
		drawScaledText(context, "ATLAS PREVIEW", rect.x + 12, rect.y + 10, TEXT_SECONDARY, 0.72F);
		int centerX = rect.centerX();
		int centerY = rect.centerY() - 18;
		int tile = Math.max(18, Math.min(28, rect.width / 10));
		int size = tile * 6;
		int startX = centerX - (size / 2);
		int startY = centerY - (size / 2);
		for (int row = 0; row < 6; row++) {
			for (int col = 0; col < 6; col++) {
				int x = startX + (col * tile);
				int y = startY + (row * tile);
				int color = (row >= 1 && row <= 4 && col >= 1 && col <= 4) ? 0xFF89B63A : 0xFF7EA92F;
				context.fill(x, y, x + tile, y + tile, color);
				context.drawBorder(x, y, tile, tile, 0x18202A14);
			}
		}
		int wallInset = tile;
		context.fill(startX + wallInset, startY + wallInset, startX + size - wallInset, startY + wallInset + tile, 0xFFAC8447);
		context.fill(startX + wallInset, startY + size - wallInset - tile, startX + size - wallInset, startY + size - wallInset, 0xFFAC8447);
		context.fill(startX + wallInset, startY + wallInset, startX + wallInset + tile, startY + size - wallInset, 0xFFAC8447);
		context.fill(startX + size - wallInset - tile, startY + wallInset, startX + size - wallInset, startY + size - wallInset, 0xFFAC8447);
		int postX = startX + (size / 2) - 1;
		int postY = startY + (size / 2) - 6;
		context.fill(postX, postY, postX + 2, postY + 12, MARKER_BANNER);
		context.fill(postX + 2, postY, postX + 6, postY + 3, MARKER_BANNER);
		drawScaledText(context, "Atlas provides the full terrain map and navigation tools.", rect.x + 12, rect.bottom() - 40, TEXT_PRIMARY, 0.82F);
		drawScaledText(context, "Use the ledger for commands. Use Atlas for terrain.", rect.x + 12, rect.bottom() - 24, TEXT_SECONDARY, 0.72F);
	}

	private String atlasFocusSummary() {
		ArmyLedgerSnapshot.DefenderEntry defender = getSelectedDefender();
		if (defender != null) {
			return defender.name() + "  " + defender.homePost().toShortString();
		}
		return "Banner " + snapshot.bannerPos().toShortString();
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
			int worldZ = cachedMapOriginZ + sampleZ;
			int y0 = worldToMapY(worldZ, body, bounds);
			int y1 = worldToMapY(worldZ + 1.0F, body, bounds);
			for (int sampleX = 0; sampleX < cachedSamplesX; sampleX++) {
				int worldX = cachedMapOriginX + sampleX;
				int x0 = worldToMapX(worldX, body, bounds);
				int x1 = worldToMapX(worldX + 1.0F, body, bounds);
				int index = sampleZ * cachedSamplesX + sampleX;
				context.fill(x0, y0, x1, y1, cachedMapColors[index]);
				if (x1 - x0 > 2 && y1 - y0 > 2) {
					drawFeatureEdge(context, x0, y0, x1, y1, sampleX, sampleZ, index);
					drawContourEdge(context, x0, y0, x1, y1, sampleX, sampleZ, index);
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
			case SAMPLE_PATH -> 0x80584024;
			case SAMPLE_WOOD -> 0x904C3119;
			case SAMPLE_STONE -> 0x90656A70;
			case SAMPLE_WATER -> 0x70536F9A;
			case SAMPLE_SAND -> 0x70A58F59;
			case SAMPLE_CROP -> 0x70578E2E;
			default -> 0x5036404B;
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

	private void drawContourEdge(DrawContext context, int x0, int y0, int x1, int y1, int sampleX, int sampleZ, int index) {
		byte kind = cachedMapKinds[index];
		if (kind != SAMPLE_STONE && kind != SAMPLE_WOOD && kind != SAMPLE_PATH) {
			return;
		}
		int height = cachedMapHeights[index];
		if (sampleX < cachedSamplesX - 1) {
			int eastDelta = cachedMapHeights[index + 1] - height;
			if (eastDelta >= 3) {
				context.fill(x1 - 1, y0, x1, y1, MAP_CONTOUR_DARK);
			} else if (eastDelta <= -3) {
				context.fill(x1 - 1, y0, x1, y1, MAP_CONTOUR_LIGHT);
			}
		}
		if (sampleZ < cachedSamplesZ - 1) {
			int southDelta = cachedMapHeights[index + cachedSamplesX] - height;
			if (southDelta >= 3) {
				context.fill(x0, y1 - 1, x1, y1, MAP_CONTOUR_DARK);
			} else if (southDelta <= -3) {
				context.fill(x0, y1 - 1, x1, y1, MAP_CONTOUR_LIGHT);
			}
		}
	}

	private void drawBannerMarker(DrawContext context, Point point, int color, boolean rally) {
		int boxLeft = point.x - 7;
		int boxTop = point.y - 7;
		context.fill(boxLeft + 1, boxTop + 1, boxLeft + 15, boxTop + 15, 0x70090C10);
		context.fill(boxLeft, boxTop, boxLeft + 14, boxTop + 14, 0xD0141A20);
		context.drawBorder(boxLeft, boxTop, 14, 14, color);
		context.fill(point.x - 1, point.y - 5, point.x + 1, point.y + 5, color);
		context.fill(point.x + 1, point.y - 5, point.x + 6, point.y - 1, color);
		if (rally) {
			context.fill(point.x - 4, point.y + 4, point.x + 5, point.y + 5, color);
		}
	}

	private void drawAttackerSymbol(DrawContext context, int centerX, int centerY, ArmyLedgerSnapshot.AttackerEntry attacker) {
		if (attacker.engine() || attacker.role() == UnitRole.RAM) {
			drawRamMarker(context, centerX, centerY, MARKER_ATTACKER);
			return;
		}
		if (attacker.role() == UnitRole.RANGED) {
			drawBowMarker(context, centerX, centerY, MARKER_ATTACKER);
			return;
		}
		drawSwordMarker(context, centerX, centerY, MARKER_ATTACKER);
	}

	private void drawRamMarker(DrawContext context, int centerX, int centerY, int color) {
		context.fill(centerX - 4, centerY - 2, centerX + 4, centerY + 3, color);
		context.fill(centerX - 5, centerY - 1, centerX - 4, centerY + 2, color);
		context.fill(centerX + 4, centerY - 1, centerX + 5, centerY + 2, color);
		context.fill(centerX - 2, centerY + 3, centerX - 1, centerY + 5, color);
		context.fill(centerX + 1, centerY + 3, centerX + 2, centerY + 5, color);
	}

	private void drawMapGrid(DrawContext context, Rect body) {
		MapView bounds = computeMapView(body);
		drawMapGridLines(context, body, bounds, 8, MAP_GRID_MINOR);
		drawMapGridLines(context, body, bounds, 16, MAP_GRID_MAJOR);
	}

	private void drawMapGridLines(DrawContext context, Rect body, MapView bounds, int spacing, int color) {
		if (spacing <= 0) {
			return;
		}
		int minGridX = MathHelper.floor(bounds.minX / spacing) * spacing;
		int maxGridX = MathHelper.ceil(bounds.maxX / spacing) * spacing;
		for (int worldX = minGridX; worldX <= maxGridX; worldX += spacing) {
			float normalizedX = (worldX - bounds.minX) / Math.max(1.0F, bounds.maxX - bounds.minX);
			int screenX = body.x + Math.round(normalizedX * body.width);
			if (screenX > body.x && screenX < body.right()) {
				context.fill(screenX, body.y, screenX + 1, body.bottom(), color);
			}
		}

		int minGridZ = MathHelper.floor(bounds.minZ / spacing) * spacing;
		int maxGridZ = MathHelper.ceil(bounds.maxZ / spacing) * spacing;
		for (int worldZ = minGridZ; worldZ <= maxGridZ; worldZ += spacing) {
			float normalizedZ = (worldZ - bounds.minZ) / Math.max(1.0F, bounds.maxZ - bounds.minZ);
			int screenY = body.y + Math.round(normalizedZ * body.height);
			if (screenY > body.y && screenY < body.bottom()) {
				context.fill(body.x, screenY, body.right(), screenY + 1, color);
			}
		}
	}

	private void drawBannerAndPosts(DrawContext context, Rect body) {
		MapView bounds = computeMapView(body);
		for (ArmyLedgerSnapshot.DefenderEntry defender : snapshot.defenders()) {
			Point post = worldToMap(defender.homePost(), body, bounds);
			drawDiamond(context, post.x - 7, post.y - 7, 14, 0x80202830);
			drawDiamond(context, post.x - 6, post.y - 6, 12, MARKER_POST);
		}

		drawBannerMarker(context, worldToMap(snapshot.bannerPos(), body, bounds), MARKER_BANNER, false);
		if (snapshot.hasRally()) {
			drawBannerMarker(context, worldToMap(snapshot.rallyPos(), body, bounds), MARKER_RALLY, true);
		}
	}

	private void drawAttackerMarkers(DrawContext context, Rect body) {
		if (snapshot.attackers().isEmpty()) {
			return;
		}
		MapView bounds = computeMapView(body);
		for (ArmyLedgerSnapshot.AttackerEntry attacker : snapshot.attackers()) {
			if (!attacker.online()) {
				continue;
			}
			Point point = worldToMap(attacker.currentPos(), body, bounds);
			int boxLeft = point.x - 7;
			int boxTop = point.y - 7;
			context.fill(boxLeft + 1, boxTop + 1, boxLeft + 15, boxTop + 15, 0x70090C10);
			context.fill(boxLeft, boxTop, boxLeft + 14, boxTop + 14, MARKER_ATTACKER_BG);
			context.drawBorder(boxLeft, boxTop, 14, 14, MARKER_ATTACKER);
			drawAttackerSymbol(context, point.x, point.y, attacker);
		}
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
			context.fill(boxLeft + 1, boxTop + 1, boxLeft + boxSize + 1, boxTop + boxSize + 1, 0x600B0F13);
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
			enableLedgerScissor(context, body);
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
		drawTrimmed(context, "Siege Quest Book", panel.x + 14, panel.y + 13, 220, TEXT_PRIMARY);
		int questHintX = panel.x + Math.min(420, Math.max(260, panel.width / 3));
		drawTrimmed(context, "Choose a chapter, then select a raid node to preview and stage.", questHintX, panel.y + 15, panel.right() - questHintX - 18, TEXT_SECONDARY);
		if (ledgerMode == LedgerMode.SIEGES) {
			Rect sidebar = siegeChapterSidebarRect(body);
			Rect graph = siegeQuestGraphRect(body);
			drawCard(context, sidebar);
			drawCard(context, graph);
			drawSiegeChapterSidebar(context, sidebar);

			enableLedgerScissor(context, graph);
			context.getMatrices().push();
			context.getMatrices().translate(graph.x, graph.y, 0.0F);
			drawCampaignRouteBackdrop(context, 0, 0, graph.width, graph.height);
			drawSiegeProgressTree(context, 0, 0, graph.width, graph.height, getSelectedSiege());
			context.getMatrices().pop();
			context.disableScissor();
			return;
		}

		int scrollOffset = getClampedSiegeListScroll(layout);
		enableLedgerScissor(context, body);
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
			drawBattleGroupStrip(context, card.x + 148, card.bottom() - 21, card.right() - card.x - 160, siege, accent);
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
		Rect footer = siegeButtonFooterRect(layout);
		context.fill(panel.x, panel.y, panel.right(), panel.bottom(), PANEL_COLOR);
		context.drawBorder(panel.x, panel.y, panel.width, panel.height, PANEL_BORDER);
		context.fill(panel.x, panel.y, panel.right(), panel.y + PANEL_HEADER_HEIGHT, PANEL_HEADER_COLOR);
		context.fill(panel.x + 12, panel.y + PANEL_HEADER_HEIGHT, panel.right() - 12, panel.y + PANEL_HEADER_HEIGHT + 1, CARD_BORDER);
		drawTrimmed(context, "Siege Overview", panel.x + 14, panel.y + 13, 180, TEXT_PRIMARY);
		drawTrimmed(context, "Preview and launch from here.", panel.x + 122, panel.y + 15, panel.width - 136, TEXT_SECONDARY);

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
		Rect actionsCard = offsetRect(detailLayout.actionsCard(), -scrollOffset);

		enableLedgerScissor(context, body);

		drawCard(context, titleCard);
		drawCard(context, overviewCard);
		drawCard(context, actionsCard);
		context.fill(titleCard.x, titleCard.y, titleCard.x + 6, titleCard.bottom(), accent);

		boolean compactSiegeDetail = layout.density == LayoutDensity.COMPACT;
		drawMiniSiegeIconPlate(context, titleCard.x + 14, titleCard.y + 10, siege);
		int titleTextX = titleCard.x + 44;
		int nameWidth = compactSiegeDetail ? titleCard.width - 56 : titleCard.width - 166;
		drawTrimmed(context, siege.name(), titleTextX, titleCard.y + 12, nameWidth, TEXT_PRIMARY);
		String titleStatus = siege.unlocked()
			? (siege.replay() ? "Completed route - replayable for rewards" : (siege.ageDefining() ? "Age-defining boss siege" : (isMinorRaid(siege) ? "Variable raid contract" : "Progress operation")))
			: siege.ageDefining()
				? "Locked until " + snapshot.ageProgressTarget() + " regular wins in this age"
				: "Locked until the current age route opens";
		if (compactSiegeDetail) {
			drawScaledText(context, titleStatus, titleTextX, titleCard.y + 34, siege.unlocked() ? TEXT_WARM : TEXT_MUTED, 0.72F);
			drawChip(context, titleTextX, titleCard.y + 54, 50, "Age " + ageShortLabel(siege.ageLevel()), accent, TEXT_PRIMARY);
			drawChip(context, titleTextX + 56, titleCard.y + 54, 48, siege.waveSize() + "w", 0x2E2E485A, TEXT_PRIMARY);
		} else {
			drawScaledText(context, titleStatus, titleTextX, titleCard.y + 36, siege.unlocked() ? TEXT_WARM : TEXT_MUTED, metaScale(layout.density));
			int chipY = titleCard.y + 54;
			drawChip(context, titleCard.right() - 132, chipY, 60, "Age " + ageShortLabel(siege.ageLevel()), accent, TEXT_PRIMARY);
			drawChip(context, titleCard.right() - 66, chipY, 54, siege.waveSize() + " wave", 0x2E2E485A, TEXT_PRIMARY);
		}

		int rowY = overviewCard.y + 12;
		drawScaledText(context, "Threat Board", overviewCard.x + 12, rowY, TEXT_SECONDARY, metaScale(layout.density));
		drawWrapped(context, siege.description(), overviewCard.x + 12, rowY + 16, overviewCard.width - 24, TEXT_PRIMARY, 0);
		int descriptionHeight = wrappedTextHeight(siege.description(), overviewCard.width - 24, 0, layout.density);
		int waveY = rowY + 16 + descriptionHeight + 10;
		drawWavePips(context, overviewCard.x + 12, waveY, Math.min(12, siege.waveSize()), 12, accent);
		drawScaledText(context, "Wave pressure", overviewCard.x + 118, waveY - 3, TEXT_SECONDARY, metaScale(layout.density));
		int formationY = waveY + 26;
		drawBattleGroupStrip(context, overviewCard.x + 12, formationY, overviewCard.width - 24, siege, accent);
		rowY = formationY + 28;
		rowY = drawStatRow(context, overviewCard.x + 12, rowY, "Enemies", siege.enemySummary(), overviewCard.width - 24);
		rowY = drawStatRow(context, overviewCard.x + 12, rowY, "Weapons", siege.weaponSummary(), overviewCard.width - 24);
		drawStatRow(context, overviewCard.x + 12, rowY, "Threat", siege.threatSummary(), overviewCard.width - 24);

		int actionY = actionsCard.y + 12;
		drawScaledText(context, "Command Post", actionsCard.x + 12, actionY, TEXT_SECONDARY, metaScale(layout.density));
		int commandSummaryY = actionY + 16;
		String commandSummary = snapshot.currentAgeName() + " age - " + snapshot.completedSieges() + " victories";
		drawWrapped(context, commandSummary, actionsCard.x + 12, commandSummaryY, actionsCard.width - 24, TEXT_PRIMARY, 0);
		int commandSummaryHeight = wrappedTextHeight(commandSummary, actionsCard.width - 24, 0, layout.density);
		int statusY = commandSummaryY + commandSummaryHeight + 8;
		String siegeStatus = selectedSiegeStatus(siege);
		drawWrapped(context, siegeStatus, actionsCard.x + 12, statusY, actionsCard.width - 24, TEXT_SECONDARY, 0);
		int statusHeight = wrappedTextHeight(siegeStatus, actionsCard.width - 24, 0, layout.density);
		int rallyY = statusY + statusHeight + 8;
		String rallyText = "Rally: " + (snapshot.hasRally() ? snapshot.rallyPos().toShortString() : "Not placed");
		drawWrapped(context, rallyText, actionsCard.x + 12, rallyY, actionsCard.width - 24, snapshot.hasRally() ? TEXT_WARM : TEXT_MUTED, 0);

		context.disableScissor();
		drawPanelScrollbar(context, body, getClampedSiegeDetailScroll(layout), getSiegeDetailScrollMax(layout), getSiegeDetailContentHeight(layout));
		context.fill(footer.x, footer.y, footer.right(), footer.bottom(), PANEL_COLOR);
		context.drawBorder(footer.x, footer.y, footer.width, footer.height, CARD_BORDER);
		context.fill(footer.x, footer.y, footer.right(), footer.y + 22, PANEL_HEADER_COLOR);
		context.fill(footer.x + 10, footer.y + 22, footer.right() - 10, footer.y + 23, CARD_BORDER);
		drawScaledText(context, "Siege Commands", footer.x + 12, footer.y + 7, TEXT_SECONDARY, metaScale(layout.density));
	}

	private void drawDebugOverlay(DrawContext context, Layout layout, int mouseX, int mouseY) {
		drawDebugRect(context, layout.frame, DEBUG_FRAME);
		drawDebugRect(context, layout.topBar, DEBUG_FRAME);
		drawDebugRect(context, layout.mapPanel, DEBUG_MAP);
		drawDebugRect(context, layout.mapBody, DEBUG_MAP);
		if (layout.detailVisible) {
			drawDebugRect(context, layout.detailPanel, DEBUG_DETAIL);
			drawDebugRect(context, layout.detailBody, DEBUG_DETAIL);
		}
		if (ledgerMode == LedgerMode.SIEGES) {
			Rect viewport = campaignRouteViewport(layout);
			drawDebugRect(context, viewport, DEBUG_VIEWPORT);
			ArmyLedgerSnapshot.SiegeEntry siege = getSelectedSiege();
			if (siege != null) {
				Rect node = routeNodeScreenRect(layout, siege);
				drawDebugRect(context, node, MARKER_SELECTED);
			}
		}

		int panelX = layout.frame.x + 14;
		int panelY = layout.frame.y + 14;
		int panelWidth = Math.min(360, layout.frame.width - 28);
		int panelHeight = ledgerMode == LedgerMode.SIEGES ? 126 : 92;
		context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, DEBUG_OVERLAY_BG);
		context.drawBorder(panelX, panelY, panelWidth, panelHeight, DEBUG_OVERLAY_BORDER);

		int textY = panelY + 8;
		drawScaledText(context, "Siege UI Debug  [F8]", panelX + 8, textY, TEXT_PRIMARY, 0.88F);
		textY += 14;
		drawScaledText(context, "Screen " + this.width + "x" + this.height + "  ledger=" + virtualScreenWidth() + "x" + virtualScreenHeight() + "  density=" + layout.density + "  mode=" + layout.mode, panelX + 8, textY, TEXT_WARM, 0.78F);
		textY += 12;
		drawScaledText(context, "Frame " + rectDebug(layout.frame) + "  mouse=" + mouseX + "," + mouseY, panelX + 8, textY, TEXT_SECONDARY, 0.74F);
		textY += 12;
		drawScaledText(context, "MapPanel " + rectDebug(layout.mapPanel) + "  MapBody " + rectDebug(layout.mapBody), panelX + 8, textY, TEXT_SECONDARY, 0.74F);
		textY += 12;
		if (layout.detailVisible) {
			drawScaledText(context, "DetailPanel " + rectDebug(layout.detailPanel) + "  DetailBody " + rectDebug(layout.detailBody), panelX + 8, textY, TEXT_SECONDARY, 0.74F);
			textY += 12;
		}
		if (ledgerMode == LedgerMode.SIEGES) {
			Rect viewport = campaignRouteViewport(layout);
			drawScaledText(
				context,
				String.format(Locale.ROOT, "Route viewport %s  zoom=%.2f  pan=(%.1f, %.1f)", rectDebug(viewport), campaignRouteZoom, campaignRoutePanX, campaignRoutePanY),
				panelX + 8,
				textY,
				TEXT_SECONDARY,
				0.74F
			);
			textY += 12;
			ArmyLedgerSnapshot.SiegeEntry siege = getSelectedSiege();
			if (siege != null) {
				drawScaledText(context, "Selected " + siege.name() + "  node=" + rectDebug(routeNodeScreenRect(layout, siege)), panelX + 8, textY, TEXT_SECONDARY, 0.74F);
			}
		}
	}

	private void drawDebugRect(DrawContext context, Rect rect, int color) {
		context.drawBorder(rect.x, rect.y, rect.width, rect.height, color);
	}

	private void enableLedgerScissor(DrawContext context, Rect rect) {
		Rect actual = toActualRect(rect);
		context.enableScissor(actual.x, actual.y, actual.right(), actual.bottom());
	}

	private Rect routeNodeScreenRect(Layout layout, ArmyLedgerSnapshot.SiegeEntry siege) {
		Rect viewport = campaignRouteViewport(layout);
		Rect node = routeNodeRect(viewport.width, viewport.height, siege);
		return new Rect(viewport.x + node.x, viewport.y + node.y, node.width, node.height);
	}

	private String rectDebug(Rect rect) {
		return rect.x + "," + rect.y + " " + rect.width + "x" + rect.height;
	}

	private void logDebugLayout(Layout layout) {
		long now = System.currentTimeMillis();
		String selected = getSelectedSiege() == null ? "-" : getSelectedSiege().id();
		String signature = layout.mode + "|" + layout.density + "|" + rectDebug(layout.mapBody) + "|" + rectDebug(layout.detailBody) + "|"
			+ String.format(Locale.ROOT, "%.2f|%.1f|%.1f|%s", campaignRouteZoom, campaignRoutePanX, campaignRoutePanY, selected);
		if (signature.equals(lastDebugLayoutSignature) && now - lastDebugLogAt < 1000L) {
			return;
		}
		lastDebugLayoutSignature = signature;
		lastDebugLogAt = now;
		Rect viewport = campaignRouteViewport(layout);
		AgesOfSiegeMod.LOGGER.info(
			"Ledger debug mode={} density={} frame={} mapPanel={} mapBody={} detailPanel={} detailBody={} routeViewport={} zoom={} pan=({}, {}) selected={}",
			layout.mode,
			layout.density,
			rectDebug(layout.frame),
			rectDebug(layout.mapPanel),
			rectDebug(layout.mapBody),
			rectDebug(layout.detailPanel),
			rectDebug(layout.detailBody),
			rectDebug(viewport),
			String.format(Locale.ROOT, "%.2f", campaignRouteZoom),
			String.format(Locale.ROOT, "%.1f", campaignRoutePanX),
			String.format(Locale.ROOT, "%.1f", campaignRoutePanY),
			selected
		);
	}

	private void debugLogInteraction(String action, Layout layout, double mouseX, double mouseY) {
		if (!debugOverlayEnabled) {
			return;
		}
		ArmyLedgerSnapshot.SiegeEntry siege = getSelectedSiege();
		String selected = siege == null ? "-" : siege.id();
		AgesOfSiegeMod.LOGGER.info(
			"Ledger debug action={} mouse=({}, {}) routeViewport={} zoom={} pan=({}, {}) selected={}",
			action,
			Math.round(mouseX),
			Math.round(mouseY),
			rectDebug(campaignRouteViewport(layout)),
			String.format(Locale.ROOT, "%.2f", campaignRouteZoom),
			String.format(Locale.ROOT, "%.1f", campaignRoutePanX),
			String.format(Locale.ROOT, "%.1f", campaignRoutePanY),
			selected
		);
		lastDebugLogAt = 0L;
	}

	private Rect siegeCardRect(Rect body, int index, int scrollOffset) {
		int gap = 10;
		int cardHeight = siegeCardHeight();
		int y = body.y + (index * (cardHeight + gap)) - scrollOffset;
		return new Rect(body.x, y, body.width, cardHeight);
	}

	private void drawCard(DrawContext context, Rect rect) {
		context.fill(rect.x, rect.y, rect.right(), rect.bottom(), CARD_COLOR);
		context.fill(rect.x + 1, rect.y + 1, rect.right() - 1, rect.bottom() - 1, 0x10161C21);
		context.drawBorder(rect.x, rect.y, rect.width, rect.height, CARD_BORDER);
	}

	private int drawStatRow(DrawContext context, int x, int y, String label, String value, int width) {
		LayoutDensity density = layoutDensity();
		String labelText = label.toUpperCase(Locale.ROOT);
		float labelScale = metaScale(density);
		int labelWidth = Math.round(this.textRenderer.getWidth(labelText) * labelScale);
		int labelHeight = Math.round(10 * labelScale) + 2;
		int gap = density == LayoutDensity.COMPACT ? 8 : 10;
		int reservedLabelWidth = Math.min(Math.max(labelWidth, density == LayoutDensity.COMPACT ? 72 : 84), Math.max(72, width / 3));
		int valueWidth = width - reservedLabelWidth - gap;

		drawScaledText(context, labelText, x, y + 1, TEXT_SECONDARY, labelScale);
		if (valueWidth >= 96) {
			int valueX = x + reservedLabelWidth + gap;
			drawWrapped(context, value, valueX, y, valueWidth, TEXT_PRIMARY, 0);
			int valueHeight = wrappedTextHeight(value, valueWidth, 0, density);
			return y + Math.max(labelHeight, valueHeight) + 6;
		}

		int stackedValueY = y + labelHeight + 4;
		drawWrapped(context, value, x, stackedValueY, width, TEXT_PRIMARY, 0);
		int valueHeight = wrappedTextHeight(value, width, 0, density);
		return stackedValueY + valueHeight + 6;
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

	private void drawChip(DrawContext context, int x, int y, int width, String text, int fillColor, int textColor) {
		context.fill(x, y, x + width, y + 16, fillColor);
		context.fill(x + 1, y + 1, x + width - 1, y + 15, 0x20000000);
		context.drawBorder(x, y, width, 16, 0x505A6470);
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

	private void drawBattleGroupStrip(DrawContext context, int x, int y, int width, ArmyLedgerSnapshot.SiegeEntry siege, int accent) {
		List<ArmyLedgerSnapshot.BattleGroupEntry> groups = siege.battleGroups();
		if (groups.isEmpty()) {
			return;
		}
		int cursorX = x;
		int limitX = x + Math.max(0, width);
		int visibleCount = 0;
		for (int i = 0; i < groups.size(); i++) {
			ArmyLedgerSnapshot.BattleGroupEntry group = groups.get(i);
			int tokenWidth = battleGroupTokenWidth(group);
			if (cursorX + tokenWidth > limitX) {
				break;
			}
			drawBattleGroupToken(context, cursorX, y, tokenWidth, group, accent);
			cursorX += tokenWidth + 6;
			visibleCount++;
		}
		if (visibleCount < groups.size()) {
			drawScaledText(context, "+" + (groups.size() - visibleCount) + " more", Math.min(cursorX, limitX - 36), y + 3, TEXT_MUTED, 0.72F);
		}
	}

	private void drawBattleGroupToken(DrawContext context, int x, int y, int width, ArmyLedgerSnapshot.BattleGroupEntry group, int accent) {
		ItemStack icon = battleGroupIcon(group);
		int border = group.engine() ? accent : CARD_BORDER;
		int fill = group.engine() ? 0x241E241C : 0x1E273039;
		context.fill(x, y, x + width, y + 18, fill);
		context.drawBorder(x, y, width, 18, border);
		context.getMatrices().push();
		context.getMatrices().translate(0.0F, 0.0F, 120.0F);
		context.drawItem(icon, x + 1, y + 1);
		context.getMatrices().pop();
		drawScaledText(context, "x" + Math.max(0, group.count()), x + 18, y + 4, group.engine() ? TEXT_WARM : TEXT_PRIMARY, 0.72F);
	}

	private int battleGroupTokenWidth(ArmyLedgerSnapshot.BattleGroupEntry group) {
		int countWidth = this.textRenderer.getWidth("x" + Math.max(0, group.count()));
		return Math.max(36, 20 + countWidth + 10);
	}

	private ItemStack battleGroupIcon(ArmyLedgerSnapshot.BattleGroupEntry group) {
		String kind = safeText(group.kindId(), "").toLowerCase(Locale.ROOT);
		String label = safeText(group.displayName(), "").toLowerCase(Locale.ROOT);
		String combined = kind + " " + label;
		if (group.engine() || combined.contains("ram")) {
			return new ItemStack(Items.OAK_LOG);
		}
		if (combined.contains("catapult") || combined.contains("trebuchet")) {
			return new ItemStack(Items.DISPENSER);
		}
		if (combined.contains("shield")) {
			return new ItemStack(Items.SHIELD);
		}
		if (combined.contains("bomber") || combined.contains("bomb")) {
			return new ItemStack(Items.TNT);
		}
		if (combined.contains("arch") || combined.contains("ranged") || combined.contains("crossbow")) {
			return new ItemStack(Items.CROSSBOW);
		}
		if (combined.contains("breach") || combined.contains("axe")) {
			return new ItemStack(Items.IRON_AXE);
		}
		if (combined.contains("soldier") || combined.contains("melee") || combined.contains("guard")) {
			return new ItemStack(Items.IRON_SWORD);
		}
		if (combined.contains("tech") || combined.contains("engineer")) {
			return new ItemStack(Items.REDSTONE_TORCH);
		}
		return new ItemStack(Items.PAPER);
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

	private Rect siegeChapterSidebarRect(Rect body) {
		return new Rect(body.x, body.y, Math.min(170, Math.max(142, body.width / 6)), body.height);
	}

	private Rect siegeQuestGraphRect(Rect body) {
		Rect sidebar = siegeChapterSidebarRect(body);
		int gap = 12;
		return new Rect(sidebar.right() + gap, body.y, body.width - sidebar.width - gap, body.height);
	}

	private List<ArmyLedgerSnapshot.SiegeEntry> siegeChapterEntries(int ageLevel) {
		return snapshot.sieges().stream()
			.filter(siege -> siege.ageLevel() == ageLevel)
			.sorted(Comparator
				.comparing(ArmyLedgerSnapshot.SiegeEntry::ageDefining)
				.thenComparingInt(ArmyLedgerSnapshot.SiegeEntry::unlockVictories)
				.thenComparingInt(ArmyLedgerSnapshot.SiegeEntry::routeColumn)
				.thenComparing(ArmyLedgerSnapshot.SiegeEntry::id))
			.toList();
	}

	private int selectedSiegeChapter() {
		ArmyLedgerSnapshot.SiegeEntry siege = getSelectedSiege();
		return siege == null ? snapshot.currentAgeLevel() : siege.ageLevel();
	}

	private void selectSiegeChapter(int ageLevel) {
		List<ArmyLedgerSnapshot.SiegeEntry> chapter = siegeChapterEntries(ageLevel);
		if (chapter.isEmpty()) {
			return;
		}
		ArmyLedgerSnapshot.SiegeEntry current = getSelectedSiege();
		if (current != null && current.ageLevel() == ageLevel) {
			return;
		}
		for (int i = 0; i < snapshot.sieges().size(); i++) {
			if (snapshot.sieges().get(i).id().equals(chapter.get(0).id())) {
				selectSiege(i);
				return;
			}
		}
	}

	private void drawSiegeChapterSidebar(DrawContext context, Rect sidebar) {
		drawScaledText(context, "Chapters", sidebar.x + 12, sidebar.y + 12, TEXT_SECONDARY, 0.84F);
		int selectedChapter = selectedSiegeChapter();
		for (int age = 0; age <= 3; age++) {
			Rect row = siegeChapterRowRect(sidebar, age);
			boolean selected = age == selectedChapter;
			int accent = ageAccentColor(age);
			int tint = selected ? 0x24303C47 : 0x1014191D;
			int progressValue = age < snapshot.currentAgeLevel()
				? snapshot.ageProgressTarget()
				: age == snapshot.currentAgeLevel() ? snapshot.ageProgressWins() : 0;
			int progressMax = Math.max(1, snapshot.ageProgressTarget());
			context.fill(row.x, row.y, row.right(), row.bottom(), tint);
			context.fill(row.x, row.y, row.x + 4, row.bottom(), accent);
			context.drawBorder(row.x, row.y, row.width, row.height, selected ? MARKER_SELECTED : CARD_BORDER);
			if (selected) {
				context.drawBorder(row.x - 1, row.y - 1, row.width + 2, row.height + 2, 0x7040D8EE);
			}
			context.fill(row.x + 10, row.y + 8, row.x + 30, row.y + 28, 0x16192220);
			context.drawBorder(row.x + 10, row.y + 8, 20, 20, 0x505A6470);
			drawAgeGlyph(context, row.x + 14, row.y + 12, age, false, age <= snapshot.currentAgeLevel() ? TEXT_WARM : TEXT_MUTED);
			drawTrimmed(context, ageLabel(age), row.x + 38, row.y + 7, row.width - 48, age <= snapshot.currentAgeLevel() ? TEXT_PRIMARY : TEXT_MUTED);
			String status = age < snapshot.currentAgeLevel()
				? "Completed routes"
				: age == snapshot.currentAgeLevel()
					? snapshot.ageProgressWins() + "/" + snapshot.ageProgressTarget() + " wins"
					: "Locked chapter";
			drawScaledText(context, status, row.x + 38, row.y + 21, age <= snapshot.currentAgeLevel() ? TEXT_WARM : TEXT_MUTED, 0.72F);
			drawProgressBar(context, row.x + 38, row.y + 33, row.width - 50, 6, progressValue, progressMax, accent, 0x10232B33);
		}
	}

	private Rect siegeChapterRowRect(Rect sidebar, int age) {
		int rowHeight = 46;
		int gap = 8;
		return new Rect(sidebar.x + 10, sidebar.y + 36 + (age * (rowHeight + gap)), sidebar.width - 20, rowHeight);
	}

	private void drawSiegeProgressTree(DrawContext context, int x, int y, int width, int height, ArmyLedgerSnapshot.SiegeEntry selectedSiege) {
		int chapterAge = selectedSiegeChapter();
		List<ArmyLedgerSnapshot.SiegeEntry> chapterSieges = siegeChapterEntries(chapterAge);
		drawAgeColumnHeader(context, 18, 16, chapterAge);
		String routeStatus = chapterAge < snapshot.currentAgeLevel()
			? "Completed route open for reward runs"
			: chapterAge == snapshot.currentAgeLevel()
				? snapshot.ageProgressWins() + "/" + snapshot.ageProgressTarget() + " war wins toward the milestone siege"
				: "Locked chapter until the previous age siege falls";
		drawTrimmed(context, routeStatus, 24, 46, Math.max(180, width - 48), chapterAge <= snapshot.currentAgeLevel() ? TEXT_WARM : TEXT_MUTED);
		int footerY = Math.max(268, height - 54);

		Rect previousRect = null;
		ArmyLedgerSnapshot.SiegeEntry previousSiege = null;
		for (ArmyLedgerSnapshot.SiegeEntry siege : chapterSieges) {
			Rect nodeRect = routeNodeRect(width, height, siege);
			if (previousRect != null) {
				int thickness = (isMinorRaid(previousSiege) || isMinorRaid(siege)) ? 2 : 3;
				drawTreeConnector(context, previousRect.right(), previousRect.centerY(), nodeRect.x, nodeRect.centerY(), 0x22343E47, thickness + 1);
				drawTreeConnector(context, previousRect.right(), previousRect.centerY(), nodeRect.x, nodeRect.centerY(), connectorColorFor(siege), thickness);
				if (previousSiege != null && isSiegeQuestCompleted(previousSiege)) {
					drawTreeConnector(context, previousRect.right(), previousRect.centerY(), nodeRect.x, nodeRect.centerY(), animatedConnectorColor(connectorColorFor(siege)), 1);
				}
			}
			previousRect = nodeRect;
			previousSiege = siege;
		}

		ArmyLedgerSnapshot.SiegeEntry bossSiege = chapterSieges.stream().filter(ArmyLedgerSnapshot.SiegeEntry::ageDefining).findFirst().orElse(null);
		if (bossSiege != null) {
			Rect bossRect = routeNodeRect(width, height, bossSiege);
			drawScaledText(context, snapshot.ageProgressTarget() + " wins to age siege", bossRect.x - 84, bossRect.y - 18, 0xCFE2C38E, 0.7F);
		}

		for (ArmyLedgerSnapshot.SiegeEntry siege : chapterSieges) {
			Rect nodeRect = routeNodeRect(width, height, siege);
			boolean selected = selectedSiege != null && selectedSiege.id().equals(siege.id());
			drawAgeStageNode(context, nodeRect.x, nodeRect.y, nodeRect.width, nodeRect.height, siege, selected);
		}
		if (selectedSiege != null) {
			drawQuestSelectionBar(context, 18, footerY, Math.max(280, width - 36), selectedSiege);
		}
	}

	private Rect routeNodeRect(int canvasWidth, int canvasHeight, ArmyLedgerSnapshot.SiegeEntry siege) {
		List<ArmyLedgerSnapshot.SiegeEntry> chapterSieges = siegeChapterEntries(siege.ageLevel());
		int maxColumn = Math.max(1, chapterSieges.stream().mapToInt(ArmyLedgerSnapshot.SiegeEntry::routeColumn).max().orElse(1));
		int maxRow = Math.max(0, chapterSieges.stream().mapToInt(ArmyLedgerSnapshot.SiegeEntry::routeRow).max().orElse(0));
		int leftPad = 74;
		int rightPad = 74;
		int usableWidth = Math.max(300, canvasWidth - leftPad - rightPad);
		float columnFraction = maxColumn <= 0 ? 0.0F : siege.routeColumn() / (float) maxColumn;
		int centerX = leftPad + Math.round(columnFraction * usableWidth);
		int nodeWidth = siege.ageDefining() ? 52 : (isMinorRaid(siege) ? 24 : 40);
		int nodeHeight = nodeWidth;
		int topY = 96;
		int upperMidY = Math.max(138, topY + 52);
		int midY = Math.max(188, (canvasHeight / 2) - 10);
		int bottomY = Math.max(midY + 42, canvasHeight - 152);
		int[] laneY = maxRow <= 0
			? new int[]{midY}
			: new int[]{topY, upperMidY, midY, bottomY};
		int x = centerX - (nodeWidth / 2);
		int y = laneY[MathHelper.clamp(siege.routeRow(), 0, laneY.length - 1)];
		if (isMinorRaid(siege)) {
			y += 10;
		}
		return new Rect(x, y, nodeWidth, nodeHeight);
	}

	private void drawAgeStageNode(DrawContext context, int x, int y, int width, int height, ArmyLedgerSnapshot.SiegeEntry siege, boolean selected) {
		int accent = siegeAccentColor(siege);
		boolean minorRaid = isMinorRaid(siege);
		boolean completed = isSiegeQuestCompleted(siege);
		int outer = !siege.unlocked() && !siege.replay()
			? 0xFF555A61
			: siege.ageDefining()
				? 0xFF7E2B24
				: minorRaid
					? 0xFF7A6330
				: siege.replay()
					? 0xFF7B2525
					: 0xFF7A6330;
		int inner = !siege.unlocked() && !siege.replay()
			? 0xFF2A2E34
			: minorRaid
				? 0xFF222A31
				: 0xFF30353B;
		context.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0x50000000);
		if (completed && !selected) {
			context.drawBorder(x - 2, y - 2, width + 4, height + 4, animatedConnectorColor(accent));
		}
		if (selected) {
			context.fill(x - 3, y - 3, x + width + 3, y + height + 3, 0x302FCFE4);
			context.drawBorder(x - 3, y - 3, width + 6, height + 6, 0xB02FCFE4);
		}
		context.fill(x, y, x + width, y + height, outer);
		context.drawBorder(x, y, width, height, selected ? MARKER_SELECTED : 0xFF2A2420);
		context.fill(x + 3, y + 3, x + width - 3, y + height - 3, inner);
		context.drawBorder(x + 3, y + 3, width - 6, height - 6, 0x805C646C);
		context.fill(x + 6, y + 6, x + width - 6, y + height - 6, 0xA012171C);
		drawSiegeNodeIcon(context, x + ((width - 16) / 2), y + ((height - 16) / 2), siege);
		if (!siege.unlocked() && !siege.replay()) {
			context.fill(x + 6, y + 6, x + width - 6, y + height - 6, 0x80101820);
		}
		drawNodeBadge(context, x, y, width, siege, accent);
		if (selected) {
			context.drawBorder(x - 1, y - 1, width + 2, height + 2, 0x7033D7EF);
		} else if (minorRaid) {
			context.drawBorder(x - 1, y - 1, width + 2, height + 2, 0x30465663);
		}
	}

	private void drawAgeColumnHeader(DrawContext context, int x, int y, int age) {
		int width = 196;
		int color = ageAccentColor(age);
		context.fill(x, y, x + width, y + 26, 0x20202830);
		context.fill(x + 1, y + 1, x + width - 1, y + 25, 0x10161B21);
		context.drawBorder(x, y, width, 26, CARD_BORDER);
		context.fill(x + 8, y + 7, x + width - 8, y + 19, color);
		drawTrimmed(context, ageLabel(age) + " Age", x + 14, y + 8, width - 28, age <= snapshot.currentAgeLevel() ? TEXT_PRIMARY : TEXT_MUTED);
	}

	private int connectorColorFor(ArmyLedgerSnapshot.SiegeEntry siege) {
		if (siege.replay()) {
			return 0xFF8AA06B;
		}
		return isSiegeQuestCompleted(siege) ? TEXT_WARM : 0xFF3E4A55;
	}

	private int animatedConnectorColor(int color) {
		double phase = (System.currentTimeMillis() % 1200L) / 1200.0D;
		double pulse = 0.55D + (Math.sin(phase * Math.PI * 2.0D) * 0.35D);
		int alpha = MathHelper.clamp((int) Math.round(80 + (pulse * 90.0D)), 32, 180);
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	private boolean isMinorRaid(ArmyLedgerSnapshot.SiegeEntry siege) {
		if (siege == null) {
			return false;
		}
		SiegeCatalog.SiegeDefinition definition = SiegeCatalog.byId(siege.id());
		return definition != null && definition.minorRaid();
	}

	private boolean isSiegeQuestCompleted(ArmyLedgerSnapshot.SiegeEntry siege) {
		if (siege == null) {
			return false;
		}
		if (siege.ageLevel() < snapshot.currentAgeLevel()) {
			return true;
		}
		if (siege.ageLevel() > snapshot.currentAgeLevel()) {
			return false;
		}
		if (siege.ageDefining()) {
			return false;
		}
		return snapshot.ageProgressWins() > siege.unlockVictories();
	}

	private void drawTreeConnector(DrawContext context, int startX, int startY, int endX, int endY, int color, int thickness) {
		int turnX = startX + Math.max(16, (endX - startX) / 2);
		int half = Math.max(1, thickness / 2);
		context.fill(startX, startY - half, turnX, startY + half + 1, color);
		int top = Math.min(startY, endY) - half;
		int bottom = Math.max(startY, endY) + half + 1;
		context.fill(turnX - half, top, turnX + half + 1, bottom, color);
		context.fill(turnX, endY - half, endX, endY + half + 1, color);
	}

	private void drawRegularWinQuestNodes(DrawContext context, Rect startRect, Rect endRect, int age, int questStartIndex, int questCount, int totalWins, int filledWins, boolean bossRoute) {
		if (questCount <= 0) {
			return;
		}
		int startCenterX = startRect.centerX();
		int endCenterX = endRect.centerX();
		int startCenterY = startRect.centerY();
		int endCenterY = endRect.centerY();
		for (int i = 0; i < questCount; i++) {
			float t = (i + 1) / (float) (questCount + 1);
			int iconX = Math.round(MathHelper.lerp(t, startCenterX, endCenterX)) - 11;
			int iconY = Math.round(MathHelper.lerp(t, startCenterY, endCenterY)) - 11;
			boolean filled = questStartIndex + i < filledWins;
			drawWinQuestNode(context, iconX, iconY, age, questStartIndex + i, filled, bossRoute);
		}
		if (bossRoute) {
			int labelX = Math.round(MathHelper.lerp(0.55F, startCenterX, endCenterX)) - 36;
			int labelY = Math.min(startCenterY, endCenterY) - 20;
			drawScaledText(context, totalWins + " wins to age siege", labelX, labelY, 0xCFE2C38E, 0.7F);
		}
	}

	private void drawWinQuestNode(DrawContext context, int x, int y, int age, int questIndex, boolean filled, boolean bossRoute) {
		int outer = filled ? 0xFF836126 : 0xFF2A3138;
		int inner = filled ? (bossRoute ? 0xFFD58C36 : 0xFFCCAA61) : 0xFF151B21;
		int border = filled ? 0xFFE4CE93 : CARD_BORDER;
		context.fill(x, y, x + 22, y + 22, outer);
		context.drawBorder(x, y, 22, 22, border);
		context.fill(x + 2, y + 2, x + 20, y + 20, inner);
		if (!filled) {
			context.fill(x + 2, y + 2, x + 20, y + 20, 0x70232B33);
		}
		context.getMatrices().push();
		context.getMatrices().translate(0.0F, 0.0F, 120.0F);
		context.drawItem(regularWinQuestIcon(age, questIndex, bossRoute), x + 3, y + 3);
		context.getMatrices().pop();
	}

	private void drawQuestSelectionBar(DrawContext context, int x, int y, int width, ArmyLedgerSnapshot.SiegeEntry siege) {
		int accent = siegeAccentColor(siege);
		context.fill(x, y, x + width, y + 34, 0xC0141A1F);
		context.drawBorder(x, y, width, 34, CARD_BORDER);
		drawMiniSiegeIconPlate(context, x + 8, y + 6, siege);
		drawTrimmed(context, siege.name(), x + 38, y + 8, Math.max(120, width - 170), TEXT_PRIMARY);
		String subtitle = siege.ageDefining()
			? "Milestone siege"
			: isMinorRaid(siege)
				? "Variable raid"
			: siege.replay()
				? "Reward route"
				: "Operation";
		drawScaledText(context, subtitle, x + 38, y + 20, TEXT_SECONDARY, 0.72F);
		drawChip(context, x + width - 116, y + 9, 50, "Age " + ageShortLabel(siege.ageLevel()), accent, TEXT_PRIMARY);
		drawChip(context, x + width - 60, y + 9, 48, "+" + siege.warSuppliesReward(), 0x2E2E485A, TEXT_PRIMARY);
	}

	private void drawMiniSiegeIconPlate(DrawContext context, int x, int y, ArmyLedgerSnapshot.SiegeEntry siege) {
		int accent = siegeAccentColor(siege);
		context.fill(x, y, x + 22, y + 22, siege.ageDefining() ? 0xFF7E2B24 : 0xFF30353B);
		context.drawBorder(x, y, 22, 22, accent);
		context.fill(x + 3, y + 3, x + 19, y + 19, 0xFF12171C);
		drawSiegeNodeIcon(context, x + 3, y + 3, siege);
	}

	private void drawAgeGlyph(DrawContext context, int x, int y, int ageLevel, boolean boss, int color) {
		switch (ageLevel) {
			case 0 -> {
				context.fill(x + 5, y + 1, x + 7, y + 10, color);
				context.fill(x + 3, y + 4, x + 5, y + 9, color);
				context.fill(x + 7, y + 4, x + 9, y + 9, color);
				context.fill(x + 2, y + 8, x + 10, y + 10, color);
			}
			case 1 -> {
				context.fill(x + 2, y + 2, x + 10, y + 4, color);
				context.fill(x + 2, y + 8, x + 10, y + 10, color);
				context.fill(x + 2, y + 4, x + 4, y + 8, color);
				context.fill(x + 8, y + 4, x + 10, y + 8, color);
				context.fill(x + 5, y + 5, x + 7, y + 8, color);
			}
			case 2 -> {
				context.fill(x + 2, y + 2, x + 10, y + 4, color);
				context.fill(x + 3, y + 4, x + 9, y + 7, color);
				context.fill(x + 4, y + 7, x + 8, y + 10, color);
			}
			default -> {
				context.fill(x + 1, y + 7, x + 11, y + 9, color);
				context.fill(x + 4, y + 1, x + 6, y + 7, color);
				context.fill(x + 7, y + 3, x + 9, y + 9, color);
				context.fill(x + 8, y, x + 10, y + 3, color);
			}
		}
		if (boss) {
			context.fill(x + 10, y, x + 12, y + 2, MARKER_SELECTED);
			context.fill(x + 8, y + 2, x + 10, y + 4, MARKER_SELECTED);
			context.fill(x + 10, y + 4, x + 12, y + 6, MARKER_SELECTED);
		}
	}

	private void drawCampaignRouteBackdrop(DrawContext context, int x, int y, int width, int height) {
		context.fill(x, y, x + width, y + height, 0xFF141A1F);
		context.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF11171C);
		for (int row = y + 22; row < y + height; row += 28) {
			context.fill(x, row, x + width, row + 1, 0x102A333A);
		}
		for (int col = x + 26; col < x + width; col += 36) {
			context.fill(col, y, col + 1, y + height, 0x0C2A333A);
		}
		context.drawBorder(x, y, width, height, CARD_BORDER);
	}

	private void drawSiegeNodeIcon(DrawContext context, int x, int y, ArmyLedgerSnapshot.SiegeEntry siege) {
		ItemStack stack = siegeQuestIcon(siege);
		context.getMatrices().push();
		context.getMatrices().translate(0.0F, 0.0F, 120.0F);
		context.drawItem(stack, x, y);
		context.getMatrices().pop();
	}

	private void drawNodeBadge(DrawContext context, int x, int y, int width, ArmyLedgerSnapshot.SiegeEntry siege, int accent) {
		if (siege.ageDefining()) {
			context.fill(x + width - 10, y + 3, x + width - 3, y + 10, 0xFFD9B45C);
			context.drawBorder(x + width - 10, y + 3, 7, 7, 0xFF3A220A);
		} else if (siege.replay()) {
			context.fill(x + width - 9, y + heightMarkerOffset(siege), x + width - 3, y + heightMarkerOffset(siege) + 6, 0xFF7DA25F);
			context.drawBorder(x + width - 9, y + heightMarkerOffset(siege), 6, 6, 0xFF203019);
		} else if (!siege.unlocked()) {
			context.fill(x + width - 9, y + heightMarkerOffset(siege), x + width - 3, y + heightMarkerOffset(siege) + 6, 0xFF6C7177);
			context.drawBorder(x + width - 9, y + heightMarkerOffset(siege), 6, 6, 0xFF22282E);
		} else {
			context.fill(x + width - 9, y + heightMarkerOffset(siege), x + width - 3, y + heightMarkerOffset(siege) + 6, accent);
			context.drawBorder(x + width - 9, y + heightMarkerOffset(siege), 6, 6, 0xFF2A2115);
		}
	}

	private int heightMarkerOffset(ArmyLedgerSnapshot.SiegeEntry siege) {
		return siege.ageDefining() ? 12 : 4;
	}

	private ItemStack siegeQuestIcon(ArmyLedgerSnapshot.SiegeEntry siege) {
		if (siege.id().startsWith("homestead_patrol_")) {
			return new ItemStack(siege.id().endsWith("_v") ? Items.RED_BANNER : Items.WOODEN_SWORD);
		}
		if (siege.id().startsWith("fortified_patrol_")) {
			return new ItemStack(siege.id().endsWith("_v") ? Items.WHITE_BANNER : Items.SHIELD);
		}
		if (siege.id().startsWith("ironkeep_sortie_")) {
			return new ItemStack(siege.id().endsWith("_v") ? Items.BLACK_BANNER : Items.IRON_SWORD);
		}
		if (siege.id().startsWith("industry_counterraid_")) {
			return new ItemStack(siege.id().endsWith("_v") ? Items.ORANGE_BANNER : Items.REDSTONE);
		}
		return switch (siege.id()) {
			case "homestead_watch" -> new ItemStack(Items.SPYGLASS);
			case "fieldside_raid" -> new ItemStack(Items.WHEAT);
			case "homestead_age_siege" -> new ItemStack(Items.RED_BANNER);
			case "fort_wall_probe" -> new ItemStack(Items.CROSSBOW);
			case "gatehouse_siege" -> new ItemStack(Items.IRON_AXE);
			case "fortified_age_siege" -> new ItemStack(Items.SHIELD);
			case "ironkeep_skirmish" -> new ItemStack(Items.IRON_SWORD);
			case "ram_line_push" -> new ItemStack(Items.OAK_LOG);
			case "ironkeep_age_siege" -> new ItemStack(Items.ANVIL);
			case "smokehouse_pressure" -> new ItemStack(Items.CAMPFIRE);
			case "foundry_break" -> new ItemStack(Items.REDSTONE);
			case "industry_age_siege" -> new ItemStack(Items.BLAST_FURNACE);
			default -> siege.ageDefining() ? new ItemStack(ModItems.SETTLEMENT_STANDARD) : new ItemStack(ModItems.WAR_SUPPLIES);
		};
	}

	private ItemStack regularWinQuestIcon(int ageLevel, int questIndex, boolean bossRoute) {
		int slot = Math.floorMod(questIndex, 5);
		return switch (ageLevel) {
			case 0 -> switch (slot) {
				case 0 -> new ItemStack(Items.WOODEN_SWORD);
				case 1 -> new ItemStack(Items.BREAD);
				case 2 -> new ItemStack(Items.ARROW);
				case 3 -> new ItemStack(Items.OAK_FENCE);
				default -> new ItemStack(bossRoute ? Items.RED_BANNER : Items.LEATHER_HELMET);
			};
			case 1 -> switch (slot) {
				case 0 -> new ItemStack(Items.SHIELD);
				case 1 -> new ItemStack(Items.STONE_BRICKS);
				case 2 -> new ItemStack(Items.IRON_INGOT);
				case 3 -> new ItemStack(Items.CROSSBOW);
				default -> new ItemStack(bossRoute ? Items.WHITE_BANNER : Items.ARROW);
			};
			case 2 -> switch (slot) {
				case 0 -> new ItemStack(Items.IRON_SWORD);
				case 1 -> new ItemStack(Items.OAK_LOG);
				case 2 -> new ItemStack(Items.LADDER);
				case 3 -> new ItemStack(Items.ANVIL);
				default -> new ItemStack(bossRoute ? Items.BLACK_BANNER : Items.CHAINMAIL_CHESTPLATE);
			};
			default -> switch (slot) {
				case 0 -> new ItemStack(Items.REDSTONE);
				case 1 -> new ItemStack(Items.RAIL);
				case 2 -> new ItemStack(Items.HOPPER);
				case 3 -> new ItemStack(Items.BLAST_FURNACE);
				default -> new ItemStack(bossRoute ? Items.ORANGE_BANNER : Items.IRON_NUGGET);
			};
		};
	}

	private void drawCenteredScaledText(DrawContext context, String text, int x, int y, int width, int color, float scale) {
		String safe = safeText(text, "");
		int rawWidth = this.textRenderer.getWidth(safe);
		int centeredX = x + Math.max(0, Math.round((width - (rawWidth * scale)) / 2.0F));
		drawScaledText(context, safe, centeredX, y, color, scale);
	}

	private Rect campaignRouteViewport(Layout layout) {
		return siegeQuestGraphRect(layout.mapBody);
	}

	private int campaignRouteCanvasWidth() {
		return 640;
	}

	private int campaignRouteCanvasHeight() {
		return 340;
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
		float localX = (float) (mouseX - viewport.x);
		float localY = (float) (mouseY - viewport.y);
		List<ArmyLedgerSnapshot.SiegeEntry> chapterSieges = siegeChapterEntries(selectedSiegeChapter());
		for (ArmyLedgerSnapshot.SiegeEntry siege : chapterSieges) {
			if (contains(routeNodeRect(viewport.width, viewport.height, siege), localX, localY)) {
				for (int i = 0; i < snapshot.sieges().size(); i++) {
					if (snapshot.sieges().get(i).id().equals(siege.id())) {
						return i;
					}
				}
			}
		}
		return -1;
	}

	private int findChapterHit(Layout layout, double mouseX, double mouseY) {
		Rect sidebar = siegeChapterSidebarRect(layout.mapBody);
		if (!contains(sidebar, mouseX, mouseY)) {
			return -1;
		}
		for (int age = 0; age <= 3; age++) {
			if (contains(siegeChapterRowRect(sidebar, age), mouseX, mouseY)) {
				return age;
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
		Rect navRect = toActualRect(topBarNavRect(layout));
		int navGap = toActualLength(10);
		int navButtonY = navRect.y + Math.max(toActualLength(12), (navRect.height - 20) / 2);
		int navButtonWidth = (navRect.width - (navGap * 3)) / 2;
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
			Rect footer = toActualRect(siegeButtonFooterRect(layout));
			int inset = toActualLength(12);
			int gap = toActualLength(8);
			int buttonHeight = previousSiegeButton.getHeight();
			int buttonGap = toActualLength(6);
			int rowGap = buttonHeight + buttonGap;
			int leftX = footer.x + inset;
			int buttonWidth = (footer.width - (inset * 2) - gap) / 2;
			int rightX = leftX + buttonWidth + gap;
			int footerHeaderHeight = toActualLength(22);
			int contentTop = footer.y + footerHeaderHeight + toActualLength(8);
			int contentHeight = (buttonHeight * 3) + (buttonGap * 2);
			int availableHeight = footer.bottom() - contentTop - inset;
			int topY = contentTop + Math.max(0, (availableHeight - contentHeight) / 2);
			previousSiegeButton.setX(leftX);
			previousSiegeButton.setY(topY);
			previousSiegeButton.setWidth(buttonWidth);
			nextSiegeButton.setX(rightX);
			nextSiegeButton.setY(topY);
			nextSiegeButton.setWidth(buttonWidth);
			lockSiegeButton.setX(leftX);
			lockSiegeButton.setY(topY + rowGap);
			lockSiegeButton.setWidth(footer.width - (inset * 2));
			startSiegeButton.setX(leftX);
			startSiegeButton.setY(topY + (rowGap * 2));
			startSiegeButton.setWidth(footer.width - (inset * 2));
			previousSiegeButton.visible = layout.detailVisible;
			nextSiegeButton.visible = layout.detailVisible;
			lockSiegeButton.visible = layout.detailVisible;
			startSiegeButton.visible = layout.detailVisible;
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
		Rect visibleBody = toActualRect(layout.detailBody);
		Rect actions = toActualRect(offsetRect(createDetailLayout(layout).actionsCard, -scrollOffset));

		int inset = toActualLength(12);
		int fieldYPad = toActualLength(28);
		int buttonHeight = renameButton.getHeight();
		int fieldButtonGap = buttonHeight + toActualLength(6);
		int fieldX = actions.x + inset;
		int fieldY = actions.y + fieldYPad;
		int fieldWidth = actions.width - (inset * 2);
		int buttonY = fieldY + nameField.getHeight() + toActualLength(12);
		int lastButtonY = buttonY + (fieldButtonGap * 2);
		int overflow = (lastButtonY + buttonHeight) - visibleBody.bottom();
		if (overflow > 0) {
			int shift = Math.min(overflow, Math.max(0, buttonY - (actions.y + inset + buttonHeight)));
			fieldY -= shift;
			buttonY -= shift;
		}
		nameField.setX(fieldX);
		nameField.setY(fieldY);
		nameField.setWidth(fieldWidth);
		nameField.setVisible(fieldY >= visibleBody.y && fieldY + 20 <= visibleBody.bottom());

		renameButton.setX(fieldX);
		renameButton.setY(buttonY);
		renameButton.setWidth(fieldWidth);
		renameButton.visible = buttonY >= visibleBody.y && buttonY + buttonHeight <= visibleBody.bottom();
		cycleRoleButton.setX(fieldX);
		cycleRoleButton.setY(buttonY + fieldButtonGap);
		cycleRoleButton.setWidth(fieldWidth);
		cycleRoleButton.visible = buttonY + fieldButtonGap >= visibleBody.y && buttonY + fieldButtonGap + buttonHeight <= visibleBody.bottom();
		locateButton.setX(fieldX);
		locateButton.setY(buttonY + (fieldButtonGap * 2));
		locateButton.setWidth(fieldWidth);
		locateButton.visible = buttonY + (fieldButtonGap * 2) >= visibleBody.y && buttonY + (fieldButtonGap * 2) + buttonHeight <= visibleBody.bottom();
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
		int screenWidth = virtualScreenWidth();
		int screenHeight = virtualScreenHeight();
		int marginX = Math.max(SCREEN_MARGIN_X, screenWidth / 32);
		int marginY = Math.max(SCREEN_MARGIN_Y, screenHeight / 28);
		int availableWidth = screenWidth - (marginX * 2);
		int availableHeight = screenHeight - (marginY * 2);
		int frameWidth = MathHelper.clamp(availableWidth, Math.min(MIN_FRAME_WIDTH, availableWidth), Math.min(MAX_FRAME_WIDTH, availableWidth));
		int frameHeight = MathHelper.clamp(availableHeight, Math.min(MIN_FRAME_HEIGHT, availableHeight), Math.min(MAX_FRAME_HEIGHT, availableHeight));
		int frameX = (screenWidth - frameWidth) / 2;
		int frameY = (screenHeight - frameHeight) / 2;
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
				case WIDE -> ledgerMode == LedgerMode.SIEGES ? MathHelper.clamp(frame.width / 4, 300, 360) : MathHelper.clamp(frame.width / 3, 400, 480);
				case NORMAL -> ledgerMode == LedgerMode.SIEGES ? MathHelper.clamp(frame.width / 5, 264, 312) : MathHelper.clamp(frame.width / 4, 320, 400);
				case COMPACT -> ledgerMode == LedgerMode.SIEGES
					? MathHelper.clamp(frame.width / 5, 220, 248)
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
		int detailBodyHeight = detailPanel.height - PANEL_HEADER_HEIGHT - 22;
		if (ledgerMode == LedgerMode.SIEGES && detailVisible) {
			detailBodyHeight -= siegeButtonFooterHeight() + SIEGE_BUTTON_FOOTER_GAP;
		}
		Rect detailBody = new Rect(detailPanel.x + 12, detailPanel.y + PANEL_HEADER_HEIGHT + 10, detailPanel.width - 24, detailBodyHeight);
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
			int chapterSize = siege == null ? 0 : siegeChapterEntries(siege.ageLevel()).size();
			previousSiegeButton.active = hasSelection && !locked && chapterSize > 1;
			nextSiegeButton.active = hasSelection && !locked && chapterSize > 1;
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
		locateButton.active = AntiqueAtlasCompat.isAvailable();
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
		refreshControls();
		if (debugOverlayEnabled) {
			debugLogInteraction("select-siege", createLayout(), 0, 0);
		}
	}

	private void cycleSiegeSelection(int direction) {
		ArmyLedgerSnapshot.SiegeEntry current = getSelectedSiege();
		if (current == null) {
			return;
		}
		List<ArmyLedgerSnapshot.SiegeEntry> chapterSieges = siegeChapterEntries(current.ageLevel());
		if (chapterSieges.size() <= 1) {
			return;
		}
		int chapterIndex = 0;
		for (int i = 0; i < chapterSieges.size(); i++) {
			if (chapterSieges.get(i).id().equals(current.id())) {
				chapterIndex = i;
				break;
			}
		}
		int nextChapterIndex = MathHelper.clamp(chapterIndex + direction, 0, chapterSieges.size() - 1);
		String nextId = chapterSieges.get(nextChapterIndex).id();
		for (int i = 0; i < snapshot.sieges().size(); i++) {
			if (snapshot.sieges().get(i).id().equals(nextId)) {
				selectSiege(i);
				return;
			}
		}
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
			BlockPos pos = defender.homePost();
			Point point = worldToMap(pos, body, bounds);
			markers.add(new Marker(i, point.x, point.y, 6));
		}
		return markers;
	}

	private Point worldToMap(BlockPos pos, Rect body, MapView bounds) {
		int x = worldToMapX(pos.getX() + 0.5F, body, bounds);
		int y = worldToMapY(pos.getZ() + 0.5F, body, bounds);
		return new Point(MathHelper.clamp(x, body.x + 6, body.right() - 6), MathHelper.clamp(y, body.y + 6, body.bottom() - 6));
	}

	private int worldToMapX(float worldX, Rect body, MapView bounds) {
		float normalizedX = (worldX - bounds.minX) / Math.max(1.0F, bounds.maxX - bounds.minX);
		return body.x + Math.round(normalizedX * body.width);
	}

	private int worldToMapY(float worldZ, Rect body, MapView bounds) {
		float normalizedZ = (worldZ - bounds.minZ) / Math.max(1.0F, bounds.maxZ - bounds.minZ);
		return body.y + Math.round(normalizedZ * body.height);
	}

	private BaseBounds computeBaseBounds() {
		int minX = snapshot.bannerPos().getX();
		int maxX = snapshot.bannerPos().getX();
		int minZ = snapshot.bannerPos().getZ();
		int maxZ = snapshot.bannerPos().getZ();

		if (snapshot.hasRally()) {
			minX = Math.min(minX, snapshot.rallyPos().getX());
			maxX = Math.max(maxX, snapshot.rallyPos().getX());
			minZ = Math.min(minZ, snapshot.rallyPos().getZ());
			maxZ = Math.max(maxZ, snapshot.rallyPos().getZ());
		}

		for (ArmyLedgerSnapshot.DefenderEntry defender : snapshot.defenders()) {
			minX = Math.min(minX, Math.min(defender.homePost().getX(), defender.currentPos().getX()));
			maxX = Math.max(maxX, Math.max(defender.homePost().getX(), defender.currentPos().getX()));
			minZ = Math.min(minZ, Math.min(defender.homePost().getZ(), defender.currentPos().getZ()));
			maxZ = Math.max(maxZ, Math.max(defender.homePost().getZ(), defender.currentPos().getZ()));
		}
		for (ArmyLedgerSnapshot.AttackerEntry attacker : snapshot.attackers()) {
			minX = Math.min(minX, attacker.currentPos().getX());
			maxX = Math.max(maxX, attacker.currentPos().getX());
			minZ = Math.min(minZ, attacker.currentPos().getZ());
			maxZ = Math.max(maxZ, attacker.currentPos().getZ());
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
		int minBlockX = MathHelper.floor(bounds.minX);
		int maxBlockX = MathHelper.ceil(bounds.maxX);
		int minBlockZ = MathHelper.floor(bounds.minZ);
		int maxBlockZ = MathHelper.ceil(bounds.maxZ);
		int samplesX = MathHelper.clamp(maxBlockX - minBlockX, 1, 320);
		int samplesZ = MathHelper.clamp(maxBlockZ - minBlockZ, 1, 220);
		long tick = world == null ? -1L : world.getTime() / 10L;
		if (cachedSamplesX == samplesX
			&& cachedSamplesZ == samplesZ
			&& cachedMapOriginX == minBlockX
			&& cachedMapOriginZ == minBlockZ
			&& cachedMapTick == tick) {
			return;
		}

		cachedMapBounds = bounds;
		cachedMapOriginX = minBlockX;
		cachedMapOriginZ = minBlockZ;
		cachedSamplesX = samplesX;
		cachedSamplesZ = samplesZ;
		cachedMapTick = tick;
		cachedMapColors = new int[samplesX * samplesZ];
		cachedMapKinds = new byte[samplesX * samplesZ];
		cachedMapHeights = new int[samplesX * samplesZ];

		for (int sampleZ = 0; sampleZ < samplesZ; sampleZ++) {
			int worldZ = minBlockZ + sampleZ;
			for (int sampleX = 0; sampleX < samplesX; sampleX++) {
				int worldX = minBlockX + sampleX;
				MapSample sample = sampleMapColor(world, worldX, worldZ, snapshot.bannerPos().getY(), bounds);
				int index = sampleZ * samplesX + sampleX;
				cachedMapColors[index] = sample.color();
				cachedMapKinds[index] = sample.kind();
				cachedMapHeights[index] = sample.height();
			}
		}
	}

	private MapSample sampleMapColor(ClientWorld world, int x, int z, int fallbackY, MapView bounds) {
		if (world == null) {
			return new MapSample(0xFF2B3C2F, (byte) SAMPLE_NATURAL, fallbackY);
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
		float shade = 1.0F + MathHelper.clamp(slope * 0.04F, -0.10F, 0.10F);
		float distance = (Math.abs(x - snapshot.bannerPos().getX()) + Math.abs(z - snapshot.bannerPos().getZ()))
			/ (float) Math.max(1, (bounds.maxX - bounds.minX) + (bounds.maxZ - bounds.minZ));
		float focus = 1.01F - (distance * 0.015F);
		float contrast = switch (kind) {
			case SAMPLE_PATH, SAMPLE_WOOD, SAMPLE_STONE -> 1.10F;
			case SAMPLE_CROP -> 1.04F;
			case SAMPLE_WATER, SAMPLE_SAND -> 1.02F;
			default -> 0.98F;
		};
		return new MapSample(shadeColor(baseColor, shade * focus * contrast), kind, sampleY);
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
			case SAMPLE_PATH -> blendColor(color, 0xFFC4A062, 0.52F);
			case SAMPLE_WOOD -> blendColor(color, 0xFF8B6738, 0.44F);
			case SAMPLE_STONE -> blendColor(color, 0xFF8A8E93, 0.40F);
			case SAMPLE_WATER -> blendColor(color, 0xFF5A82B4, 0.34F);
			case SAMPLE_SAND -> blendColor(color, 0xFFD7C08A, 0.28F);
			case SAMPLE_FOLIAGE -> blendColor(color, 0xFF6E9A45, 0.12F);
			case SAMPLE_CROP -> blendColor(color, 0xFF89B63D, 0.26F);
			default -> blendColor(color, 0xFF88B542, 0.02F);
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
		cachedMapHeights = new int[0];
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

	private void openAtlas() {
		BlockPos focus = snapshot.bannerPos();
		ArmyLedgerSnapshot.DefenderEntry defender = getSelectedDefender();
		if (defender != null) {
			focus = defender.homePost();
		}
		AntiqueAtlasCompat.open(focus);
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
			return "Place a Settlement Standard to unlock the campaign.";
		}
		if (!snapshot.hasRally()) {
			return "Place a Raid Rally Banner to set the attacker rally point.";
		}
		if (snapshot.siegeLocked()) {
			return "Formation locked. The raid is staged and waiting for Start Siege.";
		}
		if (!isPeacePhase()) {
			return "A siege is already active. Finish it before planning another.";
		}
		if (!siege.unlocked()) {
			if (siege.ageDefining()) {
				return "Win " + snapshot.ageProgressTarget() + " regular sieges in this age to unlock the age siege.";
			}
			if (siege.ageLevel() > snapshot.currentAgeLevel()) {
				return "Clear the current age siege to unlock the next age route.";
			}
			return "That siege is not unlocked yet.";
		}
		if (siege.replay()) {
			return "Replay run selected. Rewards still drop, but age progress will not advance.";
		}
		if (isMinorRaid(siege)) {
			return "Variable raid selected. Its formation rotates after each completed siege.";
		}
		return snapshot.siegeStatus();
	}

	private boolean isPeacePhase() {
		return "at peace".equalsIgnoreCase(snapshot.siegePhase());
	}

	private int siegeAccentColor(ArmyLedgerSnapshot.SiegeEntry siege) {
		return ageAccentColor(siege.ageLevel());
	}

	private int ageAccentColor(int ageLevel) {
		return switch (ageLevel) {
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
		ArmyLedgerSnapshot.SiegeEntry siege = getSelectedSiege();
		int actionsHeight = Math.max(layout.density == LayoutDensity.COMPACT ? 172 : 154, siegeActionsContentHeight(layout, siege));
		int overviewHeight = Math.max(layout.density == LayoutDensity.COMPACT ? 164 : 180, siegeOverviewContentHeight(layout, siege));
		if (layout.mode == LayoutMode.DRAWER) {
			int titleHeight = layout.density == LayoutDensity.COMPACT ? 92 : 80;
			int campaignHeight = 0;
			Rect titleCard = new Rect(body.x, body.y, body.width, titleHeight);
			Rect overviewCard = new Rect(body.x, titleCard.bottom() + gap, body.width, overviewHeight);
			Rect campaignCard = new Rect(body.x, overviewCard.bottom() + gap, body.width, campaignHeight);
			Rect actionsCard = new Rect(body.x, campaignCard.bottom() + gap, body.width, actionsHeight);
			return new SiegeDetailLayout(titleCard, overviewCard, campaignCard, actionsCard);
		}
		int titleHeight = layout.density == LayoutDensity.COMPACT ? 92 : 80;
		int campaignHeight = 0;
		Rect titleCard = new Rect(body.x, body.y, body.width, titleHeight);
		Rect overviewCard = new Rect(body.x, titleCard.bottom() + gap, body.width, overviewHeight);
		Rect campaignCard = new Rect(body.x, overviewCard.bottom() + gap, body.width, campaignHeight);
		Rect actionsCard = new Rect(body.x, campaignCard.bottom() + gap, body.width, actionsHeight);
		return new SiegeDetailLayout(titleCard, overviewCard, campaignCard, actionsCard);
	}

	private int siegeOverviewContentHeight(Layout layout, ArmyLedgerSnapshot.SiegeEntry siege) {
		if (siege == null) {
			return layout.density == LayoutDensity.COMPACT ? 164 : 180;
		}
		int width = Math.max(120, layout.detailBody.width - 24);
		int rowY = 12;
		rowY += 16;
		rowY += wrappedTextHeight(siege.description(), width, 0, layout.density);
		rowY += 10;
		rowY += 12;
		rowY += 26;
		rowY += 28;
		rowY += statRowContentHeight("Enemies", siege.enemySummary(), width, layout.density);
		rowY += statRowContentHeight("Weapons", siege.weaponSummary(), width, layout.density);
		rowY += statRowContentHeight("Threat", siege.threatSummary(), width, layout.density);
		return rowY + 12;
	}

	private int statRowContentHeight(String label, String value, int width, LayoutDensity density) {
		String labelText = label.toUpperCase(Locale.ROOT);
		float labelScale = metaScale(density);
		int labelWidth = Math.round(this.textRenderer.getWidth(labelText) * labelScale);
		int labelHeight = Math.round(10 * labelScale) + 2;
		int gap = density == LayoutDensity.COMPACT ? 8 : 10;
		int reservedLabelWidth = Math.min(Math.max(labelWidth, density == LayoutDensity.COMPACT ? 72 : 84), Math.max(72, width / 3));
		int valueWidth = width - reservedLabelWidth - gap;
		if (valueWidth >= 96) {
			int valueHeight = wrappedTextHeight(value, valueWidth, 0, density);
			return Math.max(labelHeight, valueHeight) + 6;
		}
		int valueHeight = wrappedTextHeight(value, width, 0, density);
		return labelHeight + 4 + valueHeight + 6;
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
		int visibleLines = maxLines <= 0 ? lines.size() : Math.min(maxLines, lines.size());
		for (int i = 0; i < visibleLines; i++) {
			drawScaledOrderedText(context, lines.get(i), x, lineY, color, scale);
			lineY += Math.round(10 * scale) + 2;
		}
	}

	private int wrappedTextHeight(String text, int maxWidth, int maxLines, LayoutDensity density) {
		float scale = bodyScale(density);
		int trimWidth = Math.max(1, (int) (maxWidth / scale));
		List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(safeText(text, "")), trimWidth);
		int visibleLines = maxLines <= 0 ? lines.size() : Math.min(maxLines, lines.size());
		if (visibleLines <= 0) {
			return 0;
		}
		int lineAdvance = Math.round(10 * scale) + 2;
		return (visibleLines * lineAdvance) - 2;
	}

	private int siegeActionStatusMaxLines(LayoutDensity density) {
		return density == LayoutDensity.COMPACT ? 5 : 4;
	}

	private int siegeActionButtonsTop(Layout layout, Rect actionsCard, ArmyLedgerSnapshot.SiegeEntry siege) {
		int summaryY = actionsCard.y + 28;
		String summary = snapshot.currentAgeName() + " age - " + snapshot.completedSieges() + " victories";
		int summaryHeight = wrappedTextHeight(summary, actionsCard.width - 24, 0, layout.density);
		int statusY = summaryY + summaryHeight + 8;
		int statusHeight = wrappedTextHeight(selectedSiegeStatus(siege), actionsCard.width - 24, 0, layout.density);
		int rallyY = statusY + statusHeight + 8;
		String rallyText = "Rally: " + (snapshot.hasRally() ? snapshot.rallyPos().toShortString() : "Not placed");
		int rallyHeight = wrappedTextHeight(rallyText, actionsCard.width - 24, 0, layout.density);
		return rallyY + rallyHeight + 12;
	}

	private int siegeActionsContentHeight(Layout layout, ArmyLedgerSnapshot.SiegeEntry siege) {
		Rect probe = new Rect(layout.detailBody.x, layout.detailBody.y, layout.detailBody.width, 0);
		int buttonsTop = siegeActionButtonsTop(layout, probe, siege);
		return (buttonsTop - probe.y) + 92;
	}

	private int siegeButtonFooterHeight() {
		return SIEGE_BUTTON_FOOTER_HEIGHT;
	}

	private Rect siegeButtonFooterRect(Layout layout) {
		int footerHeight = siegeButtonFooterHeight();
		return new Rect(
			layout.detailPanel.x + 12,
			layout.detailPanel.bottom() - 12 - footerHeight,
			layout.detailPanel.width - 24,
			footerHeight
		);
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

	private float ledgerUiScaleFactor() {
		if (this.client == null || this.client.getWindow() == null) {
			return 1.0F;
		}
		double currentScale = this.client.getWindow().getScaleFactor();
		if (currentScale <= 0.0D) {
			return 1.0F;
		}
		return LEDGER_TARGET_SCALE / (float) currentScale;
	}

	private int virtualScreenWidth() {
		return Math.max(1, Math.round(this.width / ledgerUiScaleFactor()));
	}

	private int virtualScreenHeight() {
		return Math.max(1, Math.round(this.height / ledgerUiScaleFactor()));
	}

	private int toLedgerX(double actualX) {
		return Math.round((float) (actualX / ledgerUiScaleFactor()));
	}

	private int toLedgerY(double actualY) {
		return Math.round((float) (actualY / ledgerUiScaleFactor()));
	}

	private double toLedgerCoord(double actualCoord) {
		return actualCoord / ledgerUiScaleFactor();
	}

	private int toActualX(int ledgerX) {
		return Math.round(ledgerX * ledgerUiScaleFactor());
	}

	private int toActualY(int ledgerY) {
		return Math.round(ledgerY * ledgerUiScaleFactor());
	}

	private int toActualLength(int ledgerLength) {
		return Math.max(1, Math.round(ledgerLength * ledgerUiScaleFactor()));
	}

	private Rect toActualRect(Rect ledgerRect) {
		int x = toActualX(ledgerRect.x);
		int y = toActualY(ledgerRect.y);
		int right = toActualX(ledgerRect.right());
		int bottom = toActualY(ledgerRect.bottom());
		return new Rect(x, y, Math.max(1, right - x), Math.max(1, bottom - y));
	}

	private LayoutDensity layoutDensity() {
		int screenWidth = virtualScreenWidth();
		int shortSide = Math.min(screenWidth, virtualScreenHeight());
		if (screenWidth >= 1400 && shortSide >= 620) {
			return LayoutDensity.WIDE;
		}
		if (screenWidth <= 980 || shortSide <= 520) {
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
		return 22 + 20 + 18 + (3 * 30);
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

	private record MapSample(int color, byte kind, int height) {
	}
}
