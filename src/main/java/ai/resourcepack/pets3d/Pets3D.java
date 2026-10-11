package ai.resourcepack.pets3d;

import ai.resourcepack.engine3d.api.Engine3D;
import ai.resourcepack.engine3d.api.ModelInstance;
import ai.resourcepack.engine3d.api.Mover;
import ai.resourcepack.engine3d.api.event.ModelInteractEvent;
import ai.resourcepack.common.update.UpdateCheck;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 3D Pets: a dog that follows you about, built on 3D Engine.
 *
 * The engine draws it, animates it and walks it; this plugin is only what
 * makes it a dog. It potters about near you, stops to sniff, sits down when
 * you've stood still a while (and gets up when you leave), sits on a
 * right-click, and fetches: throw it a tennis ball and it runs after it,
 * picks it up, carries it back in its mouth and drops it at your feet.
 */
public final class Pets3D extends JavaPlugin implements Listener {

    static final String DOG = "3dpets/labrador";
    static final String BALL = "3dpets/ball";
    private static final List<String> FILES = List.of("labrador.glb", "labrador-motion.glb", "labrador.yml", "ball.obj", "ball.mtl", "ball.png", "ball.yml");
    private static final NamespacedKey IS_BALL = new NamespacedKey("3dpets", "ball");
    /** The testing subcommands, each behind its own 3dpets.<sub> node (3dpets.admin grants both). */
    private static final List<String> TOOLS = List.of("throw", "debug");

    /** Run and trot speeds while fetching, blocks per tick. */
    private static final double RUN = 0.24, TROT = 0.19;
    /** Walking up to the ball, as it does to one thrown close. */
    private static final double APPROACH = 0.1;
    /** Ticks a teleported display takes to slide: aim the ball where the mouth will be when it lands. */
    private static final int LEAD = 2;

    /** This plugin's SpigotMC resource id: the number in its page's URL. */
    private static final int SPIGOT_RESOURCE = 139512;

    private Engine3D engine;
    private final Map<UUID, Pet> pets = new HashMap<>();
    private boolean debug;
    private UpdateCheck updates;

    enum Fetch { NONE, CHASE, GO, PICKUP, RETURN, DROP }

    final class Pet {
        final Player owner;
        final ModelInstance body;
        final Mover legs;
        boolean sitting, autoSat;
        int ownerStill;
        Location ownerLast;
        int idleTicks;
        long sniffUntil;

        Fetch fetch = Fetch.NONE;
        Snowball thrown;
        ModelInstance ball;
        boolean carrying;
        int fetchTicks;
        Location ballFrom;

        Pet(Player owner, ModelInstance body, Mover legs) {
            this.owner = owner;
            this.body = body;
            this.legs = legs;
            this.ownerLast = owner.getLocation();
        }

        void setFetch(Fetch next) {
            // Following its owner would take over whenever the fetch stands
            // still: thrown far, it turned for home mid pick-up.
            if (next == Fetch.NONE) legs.follow(owner);
            else if (fetch == Fetch.NONE) legs.unfollow();
            fetch = next;
            fetchTicks = 0;
        }

        void sit(boolean sit) {
            sitting = sit;
            legs.freeze(sit);
            legs.hold(sit ? "sit" : null);
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        engine = Engine3D.get();
        installModels();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        // A reload re-bakes the models: give everyone their dog back on the new ones.
        engine.whenReady(() -> {
            for (Pet pet : List.copyOf(pets.values())) {
                if (pet.body.valid()) continue;
                pets.remove(pet.owner.getUniqueId());
                if (pet.owner.isOnline()) pets.put(pet.owner.getUniqueId(), spawn(pet.owner));
            }
        });
        updates = UpdateCheck.spigot(this, SPIGOT_RESOURCE)
                .name("3D Pets")
                .permission("3dpets.update")
                .enabledWhen(() -> getConfig().getBoolean("update-check", true))
                .player(this::tellUpdate)
                .start();
    }

    /** The join nudge: the sentence, then the link as one clickable piece. */
    private void tellUpdate(Player player, UpdateCheck.Update update) {
        BaseComponent words = TextComponent.fromLegacy(
                ChatColor.GRAY + "3D Pets " + ChatColor.WHITE + update.latest()
                        + ChatColor.GRAY + " is out (you have " + update.running() + "). ");
        TextComponent link = new TextComponent("Get it on SpigotMC");
        link.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
        link.setUnderlined(true);
        link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, update.url()));
        link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.hover.content.Text(update.url())));
        // Both go in a fresh component: a deserialised one's extras can't be added to.
        player.spigot().sendMessage(new TextComponent(words, link));
    }

    /** Copies the dog and the ball into the engine's models folder (before its first tick, when it bakes). */
    private void installModels() {
        Path dir = engine.modelsFolder().resolve("3dpets");
        try {
            Files.createDirectories(dir);
            for (String f : FILES) {
                try (InputStream in = getResource("models/" + f)) {
                    if (in == null) continue;
                    byte[] bytes = in.readAllBytes();
                    Path to = dir.resolve(f);
                    if (!Files.exists(to) || !Arrays.equals(Files.readAllBytes(to), bytes)) Files.write(to, bytes);
                }
            }
            try (InputStream in = getResource("CREDITS.txt")) {
                if (in != null) Files.write(dir.resolve("CREDITS.txt"), in.readAllBytes());
            }
        } catch (IOException e) {
            getLogger().severe("Couldn't install the models: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (updates != null) updates.stop();
        pets.values().forEach(this::remove);
        pets.clear();
    }

    // ---- commands ------------------------------------------------------------------

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        // Permissions are the sender's own, also when the console acts as a player below.
        CommandSender actor = sender;
        // From the console: /pet <player> [sub].
        if (!(sender instanceof Player) && args.length > 0 && getServer().getPlayerExact(args[0]) != null) {
            sender = getServer().getPlayerExact(args[0]);
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
        Pet pet = pets.get(player.getUniqueId());
        switch (sub) {
            case "sit" -> {
                if (pet != null) {
                    pet.autoSat = false;
                    pet.sit(!pet.sitting);
                }
            }
            case "ball" -> {
                if (!engine.ready()) return notReady(player);
                player.getInventory().addItem(ball());
                player.sendMessage(ChatColor.GRAY + "A tennis ball. Throw it and your dog will fetch it.");
            }
            case "throw" -> {
                // For testing from the console: a ball lands this many blocks
                // ahead of the player and the dog goes for it.
                double far = args.length > 1 ? Double.parseDouble(args[1]) : 15;
                if (pet == null || pet.fetch != Fetch.NONE) return true;
                Location ahead = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(far));
                Location rest = restingPlace(ahead.add(0, 3, 0));
                if (rest == null) return true;
                pet.ball = engine.spawn(BALL, rest);
                pet.autoSat = false;
                pet.sit(false);
                pet.setFetch(Fetch.GO);
            }
            case "debug" -> {
                debug = !debug;
                player.sendMessage("3D Pets debug " + (debug ? "on" : "off"));
            }
            default -> {
                if (pet != null) {
                    remove(pet);
                    pets.remove(player.getUniqueId());
                    player.sendMessage(ChatColor.GRAY + "Your dog went home.");
                } else {
                    if (!engine.ready() || engine.model(DOG).isEmpty()) return notReady(player);
                    pets.put(player.getUniqueId(), spawn(player));
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

    private boolean notReady(Player p) {
        p.sendMessage(ChatColor.GRAY + "The dog isn't ready yet (3D Engine is still baking it).");
        return true;
    }

    private Pet spawn(Player owner) {
        ModelInstance body = engine.spawn(DOG, owner.getLocation());
        Mover legs = engine.mover(body, EntityType.WOLF);
        legs.follow(owner);
        return new Pet(owner, body, legs);
    }

    private void remove(Pet pet) {
        if (pet.ball != null || pet.thrown != null) {
            if (pet.ball != null) pet.ball.remove();
            if (pet.owner.isOnline()) pet.owner.getInventory().addItem(ball());
        }
        pet.legs.remove();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Pet pet = pets.remove(event.getPlayer().getUniqueId());
        if (pet != null) remove(pet);
    }

    // A right-click on the dog is the owner's say: sit, or get up.
    @EventHandler
    public void onInteract(ModelInteractEvent event) {
        Pet pet = pets.get(event.getPlayer().getUniqueId());
        if (pet == null || event.getModel() != pet.legs.body()) return;
        event.setCancelled(true);
        if (pet.fetch != Fetch.NONE) return;
        pet.autoSat = false;
        pet.sit(!pet.sitting);
    }

    // ---- the ball ------------------------------------------------------------------

    private ItemStack ball() {
        ItemStack model = engine.item(BALL, null);
        ItemStack item = new ItemStack(Material.SNOWBALL);
        item.setItemMeta(getServer().getItemFactory().asMetaFor(model.getItemMeta(), Material.SNOWBALL));
        ItemMeta meta = item.getItemMeta();
        meta.setItemName(ChatColor.YELLOW + "Tennis Ball");
        meta.getPersistentDataContainer().set(IS_BALL, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private static boolean isBall(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(IS_BALL);
    }

    /** Where a ball that came down near {@code at} comes to rest: on the first solid block below. */
    private static Location restingPlace(Location at) {
        Block b = at.getBlock();
        for (int i = 0; i < 24 && b.isPassable() && b.getY() > at.getWorld().getMinHeight(); i++) b = b.getRelative(0, -1, 0);
        if (b.isPassable()) return null;
        double top = b.getBoundingBox().getMaxY();
        if (top <= b.getY()) top = b.getY() + 1;
        return new Location(at.getWorld(), at.getX(), top, at.getZ());
    }

    @EventHandler
    public void onThrow(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball) || !isBall(snowball.getItem())) return;
        if (!(snowball.getShooter() instanceof Player player)) return;
        Pet pet = pets.get(player.getUniqueId());
        if (pet == null || pet.fetch != Fetch.NONE) return;
        pet.thrown = snowball;
        pet.autoSat = false;
        pet.sit(false);
        pet.setFetch(Fetch.CHASE);
    }

    @EventHandler
    public void onLand(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball) || !isBall(snowball.getItem())) return;
        if (event.getHitEntity() != null) event.setCancelled(true);
        Location rest = restingPlace(snowball.getLocation());
        snowball.remove();
        Pet pet = pets.values().stream().filter(p -> p.thrown == snowball).findFirst().orElse(null);
        if (rest == null) {
            if (pet != null) giveBack(pet);
            return;
        }
        if (pet == null) {
            // Nobody to fetch it: it is just a ball on the ground again.
            rest.getWorld().dropItem(rest, ball());
            return;
        }
        pet.thrown = null;
        pet.ball = engine.spawn(BALL, rest);
        pet.setFetch(Fetch.GO);
    }

    private void giveBack(Pet pet) {
        if (pet.ball != null) pet.ball.remove();
        pet.ball = null;
        pet.carrying = false;
        pet.thrown = null;
        pet.legs.stop();
        pet.legs.hold(null);
        pet.owner.getInventory().addItem(ball());
        pet.setFetch(Fetch.NONE);
    }

    // ---- each tick -------------------------------------------------------------------

    private void tick() {
        pets.entrySet().removeIf(entry -> {
            Pet pet = entry.getValue();
            if (!pet.legs.valid() || !pet.owner.isOnline()) {
                remove(pet);
                return true;
            }
            live(pet);
            return false;
        });
    }

    private void live(Pet pet) {
        Location now = pet.legs.brain().getLocation();
        long time = now.getWorld().getGameTime();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location ownerAt = pet.owner.getLocation();
        boolean sameWorld = ownerAt.getWorld() == now.getWorld();
        if (sameWorld && ownerAt.distanceSquared(pet.ownerLast) < 0.04) pet.ownerStill++;
        else {
            pet.ownerStill = 0;
            pet.ownerLast = ownerAt.clone();
        }
        double ownerDistance = sameWorld ? ownerAt.distance(now) : Double.MAX_VALUE;
        boolean fetching = pet.fetch != Fetch.NONE;
        boolean moving = pet.legs.speed() > 0.035;

        // Sitting down and getting up by itself.
        if (pet.autoSat && pet.sitting && pet.ownerStill == 0 && ownerDistance > 4) {
            pet.sit(false);
            pet.autoSat = false;
        }
        if (!fetching && !pet.sitting && pet.ownerStill > 20 * 25 && pet.idleTicks > 20 * 6 && random.nextInt(400) == 0) {
            pet.sit(true);
            pet.autoSat = true;
        }
        // Sat by its owner: it stays even if they walk off. Sat by itself: it follows.
        pet.legs.freeze(pet.sitting);

        // Pottering about: a short wander near its owner, now and then a dash.
        if (!fetching && !pet.sitting && !moving && !pet.legs.going() && pet.idleTicks > 20 * 4 && time > pet.sniffUntil && ownerDistance < 8) {
            int roll = random.nextInt(600);
            if (roll < 3) {
                double angle = random.nextDouble(Math.PI * 2), radius = 2 + random.nextDouble(4);
                Location spot = ownerAt.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                pet.legs.goTo(spot, roll == 0 ? RUN : 0.07, 0.4);
            } else if (roll < 6) {
                pet.sniffUntil = time + 60 + random.nextInt(80);
            }
        }

        String fetchWants = fetching ? fetchStep(pet) : null;
        String hold;
        if (fetchWants != null) hold = fetchWants;
        else if (pet.sitting) hold = "sit";
        else if (!fetching && !moving && time < pet.sniffUntil) hold = "sniff";
        else hold = null;
        pet.legs.hold(hold);

        String showing = hold != null ? hold : pet.legs.locomotion();
        if (showing.equals("idle") || showing.equals("sniff")) pet.idleTicks++;
        else pet.idleTicks = 0;
        if (moving || pet.legs.going()) pet.sniffUntil = 0;

        carry(pet);

        if (debug && pet.ball != null && (pet.fetch == Fetch.GO || pet.fetch == Fetch.PICKUP)) {
            Location b = pet.ball.location();
            getLogger().info(String.format("trace %s t%d %s dog %.2f %.2f %.2f yaw %.0f ball %.2f %.2f %.2f d %.2f speed %.3f going %s",
                    pet.fetch, pet.fetchTicks, pet.body.state(), now.getX(), now.getY(), now.getZ(), pet.body.location().getYaw(),
                    b.getX(), b.getY(), b.getZ(), Math.hypot(b.getX() - now.getX(), b.getZ() - now.getZ()), pet.legs.speed(), pet.legs.going()));
        }
        if (debug && time % 10 == 0) {
            String line = String.format("%s (hold %s)  fetch %s  speed %.3f", pet.body.state(), hold, pet.fetch, pet.legs.speed());
            pet.owner.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(line));
            getLogger().info(line);
        }
    }

    /** The fetch's own steps; returns the state it wants held, or null to leave it to the legs. */
    private String fetchStep(Pet pet) {
        pet.fetchTicks++;
        Location w = pet.legs.brain().getLocation();
        switch (pet.fetch) {
            case CHASE -> {
                if (pet.thrown == null || !pet.thrown.isValid() || pet.fetchTicks > 20 * 10) {
                    if (pet.ball == null) giveBack(pet);
                    return null;
                }
                pet.legs.goTo(pet.thrown.getLocation(), RUN, 0);
                return null;
            }
            case GO -> {
                // Gallop out, then walk the last few blocks: arriving at a run it
                // slid past the ball before it could stop. A short throw never
                // got up to speed, which is why that one always came out right.
                Location b = pet.ball.location();
                boolean near = b.getWorld() == w.getWorld() && Math.hypot(b.getX() - w.getX(), b.getZ() - w.getZ()) < 4;
                pet.legs.goTo(b, near ? APPROACH : RUN, 0.5);
                if (b.getWorld() == w.getWorld() && Math.hypot(b.getX() - w.getX(), b.getZ() - w.getZ()) < 1.1 && Math.abs(b.getY() - w.getY()) < 1.5) {
                    pet.legs.stop();
                    pet.setFetch(Fetch.PICKUP);
                } else if (pet.fetchTicks > 20 * 20) {
                    giveBack(pet);
                }
                return null;
            }
            case PICKUP -> {
                // Head down into the pick-up, close the mouth on the ball a few
                // ticks in, then lift it on the way out. The pick-up leaves only
                // from tick 12 (head down, its sync-at), so the grab lands just
                // before: miss that tick and the head stays down a whole loop.
                pet.legs.face(pet.ball.location());
                if (!pet.carrying && pet.body.settledIn("pickup") && pet.body.phase() >= 6) {
                    pet.carrying = true;
                    pet.ballFrom = pet.ball.location();
                    pet.fetchTicks = 0;
                    w.getWorld().playSound(w, Sound.ENTITY_FOX_BITE, 0.4f, 1.4f);
                }
                if (pet.carrying && pet.fetchTicks >= 5) {
                    pet.setFetch(Fetch.RETURN);
                    return "walk";
                }
                if (pet.fetchTicks > 20 * 6) pet.setFetch(Fetch.RETURN);
                return "pickup";
            }
            case RETURN -> {
                // Lift the head out of the pick-up before setting off.
                if (pet.body.state().startsWith("pickup")) return "walk";
                pet.legs.goTo(pet.owner.getLocation(), TROT, 1.6);
                if (pet.owner.getWorld() == w.getWorld() && pet.owner.getLocation().distance(w) < 1.9) {
                    pet.legs.stop();
                    pet.setFetch(Fetch.DROP);
                } else if (pet.fetchTicks > 20 * 30) {
                    giveBack(pet);
                }
                return null;
            }
            case DROP -> {
                pet.legs.face(pet.owner.getLocation());
                if (pet.body.settledIn("idle") && pet.fetchTicks > 12) {
                    Location from = pet.ball.location().add(0, 0.065, 0);
                    pet.ball.remove();
                    pet.ball = null;
                    pet.carrying = false;
                    Item dropped = w.getWorld().dropItem(from, ball());
                    Vector toward = pet.owner.getLocation().toVector().subtract(from.toVector()).setY(0);
                    if (toward.lengthSquared() > 0.01) toward.normalize().multiply(0.12);
                    dropped.setVelocity(toward.setY(0.12));
                    dropped.setOwner(pet.owner.getUniqueId());
                    dropped.setPickupDelay(8);
                    pet.setFetch(Fetch.NONE);
                    pet.idleTicks = 0;
                    return null;
                }
                return "idle";
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * Puts the carried ball in the mouth, wherever this frame of whichever
     * clip has it: aimed where the mouth will be when both displays' two-tick
     * slide lands, so the dog's own movement cancels out between them.
     */
    private void carry(Pet pet) {
        if (pet.ball == null || !pet.carrying) return;
        Location mouth = pet.body.point("mouth", LEAD);
        if (mouth == null) return;
        // The ball model stands on its lowest point: drop it by its radius.
        Location at = mouth.subtract(0, 0.065 * pet.body.scale(), 0);
        if (pet.fetch == Fetch.PICKUP && pet.ballFrom != null && pet.fetchTicks < 5) {
            at = pet.ballFrom.clone().add(at.toVector().subtract(pet.ballFrom.toVector()).multiply(pet.fetchTicks / 5.0));
        }
        pet.ball.teleport(at);
        if (debug && pet.owner.getWorld().getGameTime() % 5 == 0)
            getLogger().info(String.format("carry: %s %s, ball %.2f from the mouth, dog %.1f from you, ball display %s",
                    pet.body.state(), pet.fetch, pet.ball.location().distance(at), pet.body.location().distance(pet.owner.getLocation()),
                    pet.ball.display().isValid() ? "ok" : "gone"));
    }
}
