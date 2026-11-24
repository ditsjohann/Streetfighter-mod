package com.streetfighter.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.streetfighter.fight.AIDifficulty;
import com.streetfighter.fight.FighterType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;

/**
 * /streetfighter difficulty [normal|hard]
 * /streetfighter styles
 * /streetfighter unlock <style|all>   (op)
 * /streetfighter resetstyles           (op)
 */
public final class SFCommands {
    private SFCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("streetfighter")
                .then(Commands.literal("difficulty")
                        .executes(SFCommands::showDifficulty)
                        .then(Commands.argument("level", StringArgumentType.word())
                                .requires(src -> src.hasPermission(2))
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"normal", "hard"}, b))
                                .executes(SFCommands::setDifficulty)))
                .executes(SFCommands::help)
                .then(Commands.literal("help").executes(SFCommands::help))
                .then(Commands.literal("styles").executes(SFCommands::listStyles))
                .then(Commands.literal("training")
                        .then(Commands.literal("on").executes(ctx -> training(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> training(ctx, false))))
                .then(Commands.literal("unlock")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("style", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        java.util.stream.Stream.concat(java.util.stream.Stream.of("all"), Arrays.stream(FighterType.values())
                                                .filter(t -> t.learnable).map(t -> t.id)).toList(), b))
                                .executes(SFCommands::unlock)))
                .then(Commands.literal("resetstyles")
                        .requires(src -> src.hasPermission(2))
                        .executes(SFCommands::reset)));
    }

    private static int showDifficulty(CommandContext<CommandSourceStack> ctx) {
        AIDifficulty d = WorldFightData.get(ctx.getSource().getServer()).difficulty();
        String text = d == null ? "not chosen yet (defaults to Normal)" : d.displayName;
        ctx.getSource().sendSuccess(() -> Component.literal("Street Fighter AI difficulty: " + text).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int setDifficulty(CommandContext<CommandSourceStack> ctx) {
        AIDifficulty d = AIDifficulty.byId(StringArgumentType.getString(ctx, "level"));
        if (d == null) {
            ctx.getSource().sendFailure(Component.literal("Use 'normal' or 'hard'."));
            return 0;
        }
        WorldFightData.get(ctx.getSource().getServer()).setDifficulty(ctx.getSource().getServer(), d);
        ctx.getSource().sendSuccess(() -> Component.literal("Street Fighter AI difficulty set to " + d.displayName).withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        String[] lines = {
                "§6§l--- Street Fighter mode ---",
                "§eFight:§r look at a mob and §bHOLD E§r (a quick tap still opens your inventory).",
                "§eMove:§r A/D walk, W jump, S crouch. §eBlock:§r hold the key pointing away from the enemy.",
                "§eAttack:§r U I O punch, J K L kick (light/medium/heavy).",
                "§eSpecials:§r H + a direction. Hold §bTAB§r in a fight to see yours.",
                "§eWin§r for big loot and to learn the mob's style.",
                "§7Press §bG§7 (outside a fight) for the full Fight Guide. /streetfighter styles shows your progress."};
        for (String l : lines) ctx.getSource().sendSuccess(() -> Component.literal(l), false);
        return 1;
    }

    private static int listStyles(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<String> styles = PlayerFightData.unlockedStyles(player);
        StringBuilder known = new StringBuilder("Steve");
        StringBuilder todo = new StringBuilder();
        int have = 1, total = 1;
        for (FighterType t : FighterType.values()) {
            if (!t.learnable) continue;
            total++;
            if (styles.contains(t.id)) {
                known.append(", ").append(t.displayName);
                have++;
            } else {
                if (todo.length() > 0) todo.append(", ");
                todo.append(t.displayName);
            }
        }
        String header = "§6Your styles (" + have + "/" + total + ")§r  §7Wins: " + PlayerFightData.totalWins(player);
        String knownLine = "§aKnown:§r " + known;
        String todoLine = todo.length() == 0 ? "§aYou know every style. Respect!" : "§cStill to beat:§r " + todo;
        ctx.getSource().sendSuccess(() -> Component.literal(header), false);
        ctx.getSource().sendSuccess(() -> Component.literal(knownLine), false);
        ctx.getSource().sendSuccess(() -> Component.literal(todoLine), false);
        return 1;
    }

    private static int training(CommandContext<CommandSourceStack> ctx, boolean on)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerFightData.setTraining(player, on);
        ctx.getSource().sendSuccess(() -> Component.literal(on
                ? "Training mode ON: opponents stand still and refill their health. No rewards or penalties."
                : "Training mode OFF.").withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int unlock(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String style = StringArgumentType.getString(ctx, "style");
        List<FighterType> types = style.equals("all")
                ? Arrays.stream(FighterType.values()).filter(t -> t.learnable).toList()
                : List.of(FighterType.byId(style));
        int n = 0;
        for (FighterType t : types) if (PlayerFightData.unlock(player, t)) n++;
        int count = n;
        ctx.getSource().sendSuccess(() -> Component.literal("Unlocked " + count + " style(s).").withStyle(ChatFormatting.AQUA), false);
        return count;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerFightData.resetStyles(player);
        ctx.getSource().sendSuccess(() -> Component.literal("Your learned styles were reset.").withStyle(ChatFormatting.AQUA), false);
        return 1;
    }
}
