package ai.resourcepack.pets3d.command;

import ai.resourcepack.engine3d.api.Engine3D;
import ai.resourcepack.pets3d.Models;
import ai.resourcepack.pets3d.ball.TennisBall;
import ai.resourcepack.pets3d.pet.Pets;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code /pet}: calls your dog or sends it home; {@code /pet sit},
 * {@code /pet ball}, and the two testing tools, {@code /pet throw} and
 * {@code /pet debug}. From the console, {@code /pet <player> [sub]} acts for
 * that player.
 */
public final class PetCommand implements CommandExecutor, TabCompleter {

    /** The testing subcommands, each behind its own 3dpets.<sub> node (3dpets.admin grants both). */
    private static final List<String> TOOLS = List.of("throw", "debug");

    private final Server server;
    private final Engine3D engine;
    private final Pets pets;
    private final TennisBall balls;

    public PetCommand(Server server, Engine3D engine, Pets pets, TennisBall balls) {
        this.server = server;
        this.engine = engine;
        this.pets = pets;
        this.balls = balls;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        // Permissions are the sender's own, also when the console acts as a player below.
        CommandSender actor = sender;
        // From the console: /pet <player> [sub].
        if (!(sender instanceof Player) && args.length > 0 && server.getPlayerExact(args[0]) != null) {
            sender = server.getPlayerExact(args[0]);
            args = Arrays.copyOfRange(args, 1, args.length);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only (or /pet <player> from the console).");
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        // The testing tools, a node each: debug logs every tick of every fetch, so not for everyone.
        if (TOOLS.contains(sub) && !actor.hasPermission("3dpets." + sub)) {
            actor.sendMessage(ChatColor.GRAY + "You need 3dpets." + sub + " for that.");
            return true;
        }
        switch (sub) {
            case "sit" -> pets.toggleSit(player);
            case "ball" -> {
                if (!engine.ready()) return notReady(player);
                player.getInventory().addItem(balls.item());
                player.sendMessage(ChatColor.GRAY + "A tennis ball. Throw it and your dog will fetch it.");
            }
            case "throw" -> {
                // For testing from the console: a ball lands this many blocks
                // ahead of the player and the dog goes for it.
                double far = args.length > 1 ? Double.parseDouble(args[1]) : 15;
                pets.throwBall(player, far);
            }
            case "debug" -> {
                boolean debug = pets.toggleDebug();
                player.sendMessage("3D Pets debug " + (debug ? "on" : "off"));
            }
            default -> {
                if (pets.sendHome(player)) {
                    player.sendMessage(ChatColor.GRAY + "Your dog went home.");
                } else {
                    if (!engine.ready() || engine.model(Models.DOG).isEmpty()) return notReady(player);
                    pets.summon(player);
                    player.sendMessage(ChatColor.GRAY + "Here's your dog. Right-click it to make it sit, /pet ball for a ball.");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        // The console names a player first: let the server offer the names.
        if (!(sender instanceof Player)) return null;
        if (args.length != 1) return List.of();
        // Only what they may run: the testing tools are left out for anybody without their node.
        List<String> out = new ArrayList<>();
        for (String sub : List.of("sit", "ball", "throw", "debug"))
            if (sub.startsWith(args[0].toLowerCase()) && (!TOOLS.contains(sub) || sender.hasPermission("3dpets." + sub))) out.add(sub);
        return out;
    }

    private static boolean notReady(Player p) {
        p.sendMessage(ChatColor.GRAY + "The dog isn't ready yet (3D Engine is still baking it).");
        return true;
    }
}
