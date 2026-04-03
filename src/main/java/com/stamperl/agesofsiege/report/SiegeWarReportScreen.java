package com.stamperl.agesofsiege.report;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.Locale;

public final class SiegeWarReportScreen extends Screen {
	private final SiegeWarReportSnapshot snapshot;
	private ButtonWidget claimRewardsButton;
	private ButtonWidget closeButton;
	private int scroll;
	private int maxScroll;
	private int panelWidth;
	private int panelHeight;
	private int panelLeft;
	private int panelTop;

	public SiegeWarReportScreen(SiegeWarReportSnapshot snapshot) {
		super(Text.literal("War Report"));
		this.snapshot = snapshot;
	}

	@Override
	protected void init() {
		this.panelWidth = Math.min(620, this.width - 80);
		this.panelHeight = Math.min(360, this.height - 80);
		this.panelLeft = (this.width - panelWidth) / 2;
		this.panelTop = (this.height - panelHeight) / 2;

		int footerY = panelTop + panelHeight - 30;
		int buttonWidth = 160;
		this.clearChildren();

		if (snapshot.hasClaimableRewards() || snapshot.rewardsClaimed()) {
			this.claimRewardsButton = this.addDrawableChild(ButtonWidget.builder(
				Text.literal(snapshot.rewardsClaimed() ? "Rewards Claimed" : "Claim Rewards"),
				button -> claimRewards())
				.dimensions(panelLeft + 18, footerY, buttonWidth, 20)
				.build());
			this.claimRewardsButton.active = snapshot.hasClaimableRewards();
		} else {
			this.claimRewardsButton = null;
		}

		this.closeButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> closeReport())
			.dimensions(panelLeft + panelWidth - buttonWidth - 18, footerY, buttonWidth, 20)
			.build());
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	@Override
	public void close() {
		sendAck();
		super.close();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (maxScroll <= 0) {
			return super.mouseScrolled(mouseX, mouseY, amount);
		}
		scroll = MathHelper.clamp(scroll - (int) Math.signum(amount) * 16, 0, maxScroll);
		return true;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		int panelRight = panelLeft + panelWidth;
		int panelBottom = panelTop + panelHeight;
		context.fill(panelLeft, panelTop, panelRight, panelBottom, 0xEE141A21);
		context.drawBorder(panelLeft, panelTop, panelWidth, panelHeight, 0xFF48586A);

		Text title = snapshot.victory()
			? Text.literal("Victory Report").formatted(Formatting.GOLD)
			: Text.literal("Defeat Report").formatted(Formatting.RED);
		context.drawText(this.textRenderer, title, panelLeft + 18, panelTop + 14, 0xFFFFFFFF, false);
		context.drawText(
			this.textRenderer,
			Text.literal(snapshot.siegeName() + " - " + snapshot.ageName()),
			panelLeft + 18,
			panelTop + 30,
			0xFFB8C6D2,
			false
		);

		int footerTop = panelBottom - 42;
		int contentLeft = panelLeft + 16;
		int contentTop = panelTop + 48;
		int contentWidth = panelWidth - 32;
		int contentHeight = footerTop - contentTop - 8;
		context.fill(contentLeft, contentTop, contentLeft + contentWidth, contentTop + contentHeight, 0x88202931);

		context.enableScissor(contentLeft, contentTop, contentLeft + contentWidth, contentTop + contentHeight);
		int y = contentTop + 10 - scroll;
		y = drawSummaryCard(context, contentLeft + 10, y, contentWidth - 20);
		y += 8;
		y = drawContributionStrip(context, contentLeft + 10, y, contentWidth - 20);
		y += 8;

		boolean splitColumns = contentWidth >= 520;
		if (splitColumns) {
			int columnGap = 12;
			int columnWidth = (contentWidth - 20 - columnGap) / 2;
			int leftHeight = drawLossSection(
				context,
				Text.literal("Attacker Losses"),
				snapshot.attackerLosses(),
				contentLeft + 10,
				y,
				columnWidth,
				0xFFE49A6B,
				true
			);
			int rightHeight = drawLossSection(
				context,
				Text.literal("Defender Losses"),
				snapshot.defenderLosses(),
				contentLeft + 10 + columnWidth + columnGap,
				y,
				columnWidth,
				0xFF9EC7E8,
				true
			);
			y += Math.max(leftHeight, rightHeight) + 8;
		} else {
			y += drawLossSection(context, Text.literal("Attacker Losses"), snapshot.attackerLosses(), contentLeft + 10, y, contentWidth - 20, 0xFFE49A6B, true) + 8;
			y += drawLossSection(context, Text.literal("Defender Losses"), snapshot.defenderLosses(), contentLeft + 10, y, contentWidth - 20, 0xFF9EC7E8, true) + 8;
		}

		y += drawRewardsSection(context, contentLeft + 10, y, contentWidth - 20);
		context.disableScissor();

		int visibleBottom = contentTop + contentHeight;
		maxScroll = Math.max(0, y - visibleBottom + scroll + 10);
		if (maxScroll > 0) {
			int trackTop = contentTop + 8;
			int trackBottom = visibleBottom - 8;
			context.fill(panelRight - 10, trackTop, panelRight - 6, trackBottom, 0xFF27323C);
			int trackHeight = trackBottom - trackTop;
			int thumbHeight = Math.max(28, (int) (trackHeight * (contentHeight / (float) (contentHeight + maxScroll))));
			int thumbTravel = Math.max(0, trackHeight - thumbHeight);
			int thumbTop = trackTop + (maxScroll == 0 ? 0 : (int) (thumbTravel * (scroll / (float) maxScroll)));
			context.fill(panelRight - 10, thumbTop, panelRight - 6, thumbTop + thumbHeight, 0xFF7A8FA3);
		}

		super.render(context, mouseX, mouseY, delta);
	}

	private int drawSummaryCard(DrawContext context, int x, int y, int width) {
		List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(snapshot.summaryText()), width - 12);
		int height = Math.max(28, 10 + lines.size() * 10);
		context.fill(x, y, x + width, y + height, 0x66222D37);
		context.drawBorder(x, y, width, height, 0xFF31414F);
		int lineY = y + 6;
		for (OrderedText line : lines) {
			context.drawText(this.textRenderer, line, x + 6, lineY, 0xFFFFFFFF, false);
			lineY += 10;
		}
		return y + height;
	}

	private int drawContributionStrip(DrawContext context, int x, int y, int width) {
		int gap = 8;
		int boxWidth = (width - gap * 2) / 3;
		drawStatCard(context, x, y, boxWidth, "Damage Dealt", formatFloat(snapshot.playerDamageDealt()), 0xFF7FD0D8);
		drawStatCard(context, x + boxWidth + gap, y, boxWidth, "Damage Taken", formatFloat(snapshot.playerDamageTaken()), 0xFFE49A6B);
		drawStatCard(context, x + (boxWidth + gap) * 2, y, boxWidth, "Player Kills", Integer.toString(snapshot.playerKills()), 0xFF9EC7E8);
		return y + 34;
	}

	private void drawStatCard(DrawContext context, int x, int y, int width, String label, String value, int accent) {
		context.fill(x, y, x + width, y + 30, 0x66222D37);
		context.drawBorder(x, y, width, 30, 0xFF31414F);
		context.drawText(this.textRenderer, Text.literal(label).formatted(Formatting.GRAY), x + 6, y + 5, 0xFFB8C6D2, false);
		context.drawText(this.textRenderer, Text.literal(value), x + 6, y + 17, accent, false);
	}

	private int drawLossSection(
		DrawContext context,
		Text title,
		List<SiegeWarReportSnapshot.LossEntry> entries,
		int x,
		int y,
		int width,
		int accent,
		boolean card
	) {
		int rows = Math.max(1, entries == null ? 0 : entries.size());
		int height = 14 + rows * 12 + 8;
		if (card) {
			context.fill(x, y, x + width, y + height, 0x66222D37);
			context.drawBorder(x, y, width, height, 0xFF31414F);
		}
		context.drawText(this.textRenderer, title, x + 6, y + 6, accent, false);
		int rowY = y + 20;
		if (entries == null || entries.isEmpty()) {
			context.drawText(this.textRenderer, Text.literal("None"), x + 10, rowY, 0xFF8F9AA4, false);
			return height;
		}
		for (SiegeWarReportSnapshot.LossEntry entry : entries) {
			context.drawText(this.textRenderer, Text.literal(entry.label()), x + 10, rowY, 0xFFFFFFFF, false);
			String countText = "x" + entry.count();
			int countWidth = this.textRenderer.getWidth(countText);
			context.drawText(this.textRenderer, Text.literal(countText), x + width - 10 - countWidth, rowY, 0xFFB8C6D2, false);
			rowY += 12;
		}
		return height;
	}

	private int drawRewardsSection(DrawContext context, int x, int y, int width) {
		List<ItemStack> rewards = snapshot.claimableRewards();
		int rows = 1;
		if (rewards != null && !rewards.isEmpty()) {
			rows = rewards.size();
		} else if (snapshot.rewardsClaimed()) {
			rows = 1;
		}
		int height = 14 + rows * 18 + 8;
		context.fill(x, y, x + width, y + height, 0x66222D37);
		context.drawBorder(x, y, width, height, 0xFF31414F);
		context.drawText(this.textRenderer, Text.literal("Rewards").formatted(Formatting.AQUA), x + 6, y + 6, 0xFF7FD0D8, false);
		int rowY = y + 22;
		if (snapshot.rewardsClaimed()) {
			context.drawText(this.textRenderer, Text.literal("Rewards claimed"), x + 10, rowY, 0xFF8FD08F, false);
			return height;
		}
		if (rewards == null || rewards.isEmpty()) {
			context.drawText(this.textRenderer, Text.literal(snapshot.victory() ? "No rewards waiting" : "No recoveries waiting"), x + 10, rowY, 0xFF8F9AA4, false);
			return height;
		}
		for (ItemStack stack : rewards) {
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			context.drawItem(stack, x + 8, rowY - 2);
			context.drawText(this.textRenderer, stack.getName(), x + 28, rowY, 0xFFFFFFFF, false);
			String countText = "x" + stack.getCount();
			int countWidth = this.textRenderer.getWidth(countText);
			context.drawText(this.textRenderer, Text.literal(countText), x + width - 10 - countWidth, rowY, 0xFFB8C6D2, false);
			rowY += 18;
		}
		return height;
	}

	private static String formatFloat(float value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	private void claimRewards() {
		ClientPlayNetworking.send(SiegeWarReportService.CLAIM_REWARDS_PACKET, PacketByteBufs.create());
	}

	private void closeReport() {
		sendAck();
		if (this.client != null) {
			this.client.setScreen(null);
		}
	}

	private void sendAck() {
		if (this.client == null || this.client.player == null) {
			return;
		}
		ClientPlayNetworking.send(SiegeWarReportService.ACK_PACKET, PacketByteBufs.create());
	}
}
