package com.stamperl.agesofsiege.workbench;

import com.stamperl.agesofsiege.AgesOfSiegeMod;
import com.stamperl.agesofsiege.defense.DefenderRole;
import com.stamperl.agesofsiege.defense.DefenderTokenData;
import com.stamperl.agesofsiege.defense.WorkbenchArmorTier;
import com.stamperl.agesofsiege.defense.WorkbenchStat;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public final class ArmyWorkBenchScreen extends Screen {
	private static final int SCRIM = 0xD0080B0F;
	private static final int FRAME = 0xFF12181D;
	private static final int BORDER = 0xC033404B;
	private static final int PANEL = 0xFF1B232A;
	private static final int PANEL_ALT = 0xFF202930;
	private static final int CARD = 0x202C363F;
	private static final int TEXT_PRIMARY = 0xFFEAEFF3;
	private static final int TEXT_SECONDARY = 0xFF99A6B1;
	private static final int TEXT_WARM = 0xFFD6C49E;
	private static final int GOLD = 0xFFB89B54;
	private static final int STEEL = 0xFF879DAF;
	private static final int RED = 0xFFAD5548;
	private static final int GREEN = 0xFF6C8C5C;

	private final ArmyWorkBenchSnapshot snapshot;
	private LivingEntity previewEntity;

	public ArmyWorkBenchScreen(ArmyWorkBenchSnapshot snapshot) {
		super(Text.literal("Army Work Bench"));
		this.snapshot = snapshot;
	}

	@Override
	protected void init() {
		super.init();
		createButtons();
		rebuildPreviewEntity();
	}

	private void createButtons() {
		int frameX = (this.width - Math.min(1360, this.width - 32)) / 2;
		int frameY = (this.height - Math.min(820, this.height - 32)) / 2;
		int frameWidth = Math.min(1360, this.width - 32);
		int frameHeight = Math.min(820, this.height - 32);
		int actionX = frameX + frameWidth - 292;
		int actionY = frameY + frameHeight - 232;
		int buttonWidth = 132;
		int rowGap = 8;

		addDrawableChild(ButtonWidget.builder(Text.literal("+ Vitality"), button -> sendSpend(WorkbenchStat.VITALITY))
			.dimensions(actionX, actionY, buttonWidth, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("+ Strength"), button -> sendSpend(WorkbenchStat.STRENGTH))
			.dimensions(actionX + 144, actionY, buttonWidth, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("+ Discipline"), button -> sendSpend(WorkbenchStat.DISCIPLINE))
			.dimensions(actionX, actionY + 20 + rowGap, buttonWidth, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("+ Agility"), button -> sendSpend(WorkbenchStat.AGILITY))
			.dimensions(actionX + 144, actionY + 20 + rowGap, buttonWidth, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Militia"), button -> sendEquip(WorkbenchArmorTier.LEATHER))
			.dimensions(actionX, actionY + 56, 84, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Chainmail"), button -> sendEquip(WorkbenchArmorTier.CHAINMAIL))
			.dimensions(actionX + 96, actionY + 56, 84, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Iron"), button -> sendEquip(WorkbenchArmorTier.IRON))
			.dimensions(actionX + 192, actionY + 56, 84, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Eject Token"), button -> sendEject())
			.dimensions(actionX, actionY + 92, 276, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		context.fill(0, 0, width, height, SCRIM);

		int frameWidth = Math.min(1360, this.width - 32);
		int frameHeight = Math.min(820, this.height - 32);
		int frameX = (this.width - frameWidth) / 2;
		int frameY = (this.height - frameHeight) / 2;
		int topHeight = 90;
		int leftWidth = 420;
		int rightWidth = 320;
		int centerWidth = frameWidth - leftWidth - rightWidth - 40;
		int contentY = frameY + topHeight + 16;
		int contentHeight = frameHeight - topHeight - 32;

		context.fill(frameX, frameY, frameX + frameWidth, frameY + frameHeight, FRAME);
		context.drawBorder(frameX, frameY, frameWidth, frameHeight, BORDER);

		context.fill(frameX + 12, frameY + 12, frameX + frameWidth - 12, frameY + topHeight, PANEL_ALT);
		context.drawBorder(frameX + 12, frameY + 12, frameWidth - 24, topHeight - 12, BORDER);
		context.drawText(textRenderer, title, frameX + 28, frameY + 26, TEXT_WARM, false);
		context.drawText(textRenderer, Text.literal(snapshot.soldierName()), frameX + 28, frameY + 48, TEXT_PRIMARY, false);
		context.drawText(textRenderer, Text.literal("Role: Soldier"), frameX + 250, frameY + 48, TEXT_SECONDARY, false);
		context.drawText(textRenderer, Text.literal("Level " + snapshot.level()), frameX + 380, frameY + 48, GOLD, false);
		context.drawText(textRenderer, Text.literal("Spendable Points: " + snapshot.availablePoints()), frameX + 470, frameY + 48, GREEN, false);
		context.drawText(textRenderer, Text.literal(snapshot.statusMessage()), frameX + 28, frameY + 68, TEXT_SECONDARY, false);

		int leftX = frameX + 12;
		int centerX = leftX + leftWidth + 8;
		int rightX = centerX + centerWidth + 8;
		drawPanel(context, leftX, contentY, leftWidth, contentHeight, "Soldier Model");
		drawPanel(context, centerX, contentY, centerWidth, contentHeight, "Ledger Details");
		drawPanel(context, rightX, contentY, rightWidth, contentHeight, "Bench Actions");

		drawPreview(context, leftX, contentY, leftWidth, contentHeight);
		drawDetails(context, centerX, contentY, centerWidth, contentHeight);
		drawActions(context, rightX, contentY, rightWidth, contentHeight);

		super.render(context, mouseX, mouseY, delta);
	}

	private void drawPanel(DrawContext context, int x, int y, int width, int height, String title) {
		context.fill(x, y, x + width, y + height, PANEL);
		context.drawBorder(x, y, width, height, BORDER);
		context.fill(x, y, x + width, y + 34, PANEL_ALT);
		context.drawText(textRenderer, Text.literal(title), x + 14, y + 12, TEXT_PRIMARY, false);
	}

	private void drawPreview(DrawContext context, int x, int y, int width, int height) {
		int cardX = x + 16;
		int cardY = y + 52;
		int cardWidth = width - 32;
		int cardHeight = height - 68;
		context.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, CARD);
		context.drawBorder(cardX, cardY, cardWidth, cardHeight, BORDER);
		if (previewEntity != null) {
			int size = Math.min(112, cardHeight / 2);
			InventoryScreen.drawEntity(context, cardX + (cardWidth / 2), cardY + cardHeight - 36, size, 0.0F, 0.0F, previewEntity);
		}
		context.drawText(textRenderer, Text.literal(snapshot.armorLabel()), cardX + 14, cardY + 14, TEXT_WARM, false);
		context.drawText(textRenderer, Text.literal("Token ready for redeploy"), cardX + 14, cardY + cardHeight - 22, TEXT_SECONDARY, false);
	}

	private void drawDetails(DrawContext context, int x, int y, int width, int height) {
		int bodyX = x + 18;
		int rowY = y + 56;
		drawMetric(context, bodyX, rowY, width - 36, "Health", String.format("%.0f / %.0f", snapshot.health(), snapshot.maxHealth()));
		rowY += 42;
		drawMetric(context, bodyX, rowY, width - 36, "Armor", snapshot.armorLabel() + "  |  " + round(snapshot.armorValue()) + " guard");
		rowY += 42;
		drawMetric(context, bodyX, rowY, width - 36, "Attack", round(snapshot.attackPower()) + " melee");
		rowY += 42;
		drawMetric(context, bodyX, rowY, width - 36, "Agility", round(snapshot.speedValue()) + " response");
		rowY += 52;

		context.drawText(textRenderer, Text.literal("XP Progress"), bodyX, rowY, TEXT_WARM, false);
		rowY += 14;
		drawBar(context, bodyX, rowY, width - 36, 14, snapshot.xpToNextLevel() <= 0 ? 1.0F : 1.0F - (snapshot.xpToNextLevel() / 25.0F), GOLD);
		rowY += 22;
		context.drawText(textRenderer, Text.literal(snapshot.xp() + " XP  |  " + snapshot.xpToNextLevel() + " to next level"), bodyX, rowY, TEXT_SECONDARY, false);
		rowY += 40;

		context.drawText(textRenderer, Text.literal("Stat Training"), bodyX, rowY, TEXT_WARM, false);
		rowY += 20;
		drawStat(context, bodyX, rowY, width - 36, WorkbenchStat.VITALITY.displayName(), snapshot.vitality(), GREEN);
		rowY += 28;
		drawStat(context, bodyX, rowY, width - 36, WorkbenchStat.STRENGTH.displayName(), snapshot.strength(), RED);
		rowY += 28;
		drawStat(context, bodyX, rowY, width - 36, WorkbenchStat.DISCIPLINE.displayName(), snapshot.discipline(), STEEL);
		rowY += 28;
		drawStat(context, bodyX, rowY, width - 36, WorkbenchStat.AGILITY.displayName(), snapshot.agility(), GOLD);
	}

	private void drawActions(DrawContext context, int x, int y, int width, int height) {
		int bodyX = x + 14;
		int rowY = y + 56;
		context.drawText(textRenderer, Text.literal("Armor Rack"), bodyX, rowY, TEXT_WARM, false);
		rowY += 18;
		context.drawText(textRenderer, Text.literal(armorLine("Militia Leathers", snapshot.leatherUnlocked(), "leather".equals(snapshot.armorTierId()))), bodyX, rowY, TEXT_SECONDARY, false);
		rowY += 16;
		context.drawText(textRenderer, Text.literal(armorLine("Trained Chainmail", snapshot.chainmailUnlocked(), "chainmail".equals(snapshot.armorTierId()))), bodyX, rowY, TEXT_SECONDARY, false);
		rowY += 16;
		context.drawText(textRenderer, Text.literal(armorLine("Veteran Iron", snapshot.ironUnlocked(), "iron".equals(snapshot.armorTierId()))), bodyX, rowY, TEXT_SECONDARY, false);
		rowY += 42;
		context.drawText(textRenderer, Text.literal("Level Points"), bodyX, rowY, TEXT_WARM, false);
		rowY += 18;
		context.drawText(textRenderer, Text.literal("Spend points to raise health, attack, discipline, and agility."), bodyX, rowY, TEXT_SECONDARY, false);
		rowY += 36;
		context.drawText(textRenderer, Text.literal("Workbench Rules"), bodyX, rowY, TEXT_WARM, false);
		rowY += 18;
		context.drawText(textRenderer, Text.literal("This bench only edits Soldier Tokens."), bodyX, rowY, TEXT_SECONDARY, false);
		rowY += 16;
		context.drawText(textRenderer, Text.literal("Upgrades save into the token and carry into redeployment."), bodyX, rowY, TEXT_SECONDARY, false);
	}

	private String armorLine(String label, boolean unlocked, boolean equipped) {
		String state = equipped ? "equipped" : unlocked ? "unlocked" : "locked";
		return label + " - " + state;
	}

	private void drawMetric(DrawContext context, int x, int y, int width, String label, String value) {
		context.fill(x, y, x + width, y + 32, CARD);
		context.drawBorder(x, y, width, 32, BORDER);
		context.drawText(textRenderer, Text.literal(label.toUpperCase()), x + 10, y + 6, TEXT_SECONDARY, false);
		context.drawText(textRenderer, Text.literal(value), x + 10, y + 18, TEXT_PRIMARY, false);
	}

	private void drawStat(DrawContext context, int x, int y, int width, String label, int value, int color) {
		context.drawText(textRenderer, Text.literal(label), x, y, TEXT_PRIMARY, false);
		drawBar(context, x + 92, y + 2, width - 92, 10, MathHelper.clamp(value / 10.0F, 0.0F, 1.0F), color);
		context.drawText(textRenderer, Text.literal(Integer.toString(value)), x + width - 10 - textRenderer.getWidth(Integer.toString(value)), y, TEXT_SECONDARY, false);
	}

	private void drawBar(DrawContext context, int x, int y, int width, int height, float progress, int fill) {
		context.fill(x, y, x + width, y + height, 0xFF11171C);
		context.drawBorder(x, y, width, height, BORDER);
		int fillWidth = Math.max(0, Math.round((width - 2) * MathHelper.clamp(progress, 0.0F, 1.0F)));
		context.fill(x + 1, y + 1, x + 1 + fillWidth, y + height - 1, fill);
	}

	private void rebuildPreviewEntity() {
		if (client == null || client.world == null || snapshot.tokenStack().isEmpty()) {
			previewEntity = null;
			return;
		}
		Identifier guardId = new Identifier("guardvillagers", "guard");
		var type = Registries.ENTITY_TYPE.getOrEmpty(guardId);
		previewEntity = type.isPresent()
			? createPreview(type.get())
			: new ArmorStandEntity(client.world, 0.0D, 0.0D, 0.0D);
		if (previewEntity != null) {
			DefenderTokenData.applyToEntity(previewEntity, DefenderTokenData.getData(snapshot.tokenStack()), DefenderRole.SOLDIER);
		}
	}

	private LivingEntity createPreview(EntityType<?> type) {
		if (!(type.create(client.world) instanceof LivingEntity living)) {
			return null;
		}
		return living;
	}

	private String round(double value) {
		return String.format(java.util.Locale.ROOT, "%.2f", value);
	}

	private void sendSpend(WorkbenchStat stat) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(snapshot.benchPos());
		buf.writeString(stat.id());
		ClientPlayNetworking.send(ArmyWorkBenchService.SPEND_PACKET, buf);
	}

	private void sendEquip(WorkbenchArmorTier tier) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(snapshot.benchPos());
		buf.writeString(tier.id());
		ClientPlayNetworking.send(ArmyWorkBenchService.EQUIP_PACKET, buf);
	}

	private void sendEject() {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(snapshot.benchPos());
		ClientPlayNetworking.send(ArmyWorkBenchService.EJECT_PACKET, buf);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
