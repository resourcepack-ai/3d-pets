package ai.resourcepack.pets3d.pet;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;

import java.util.concurrent.ThreadLocalRandom;

/**
 * What a dog does by itself, each tick: sits down when its owner has stood
 * still a while and gets up when they leave, potters about near them, stops
 * to sniff, and holds whichever state that (or a fetch) calls for.
 */
final class PetBehaviour {

    private final Pets pets;
    private final Fetch fetch;

    PetBehaviour(Pets pets, Fetch fetch) {
        this.pets = pets;
        this.fetch = fetch;
    }

    void tick(Pet pet) {
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
        boolean fetching = pet.fetch != FetchStep.NONE;
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
                pet.legs.goTo(spot, roll == 0 ? Fetch.RUN : 0.07, 0.4);
            } else if (roll < 6) {
                pet.sniffUntil = time + 60 + random.nextInt(80);
            }
        }

        String fetchWants = fetching ? fetch.step(pet) : null;
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

        fetch.carry(pet);

        if (pets.debug && pet.ball != null && (pet.fetch == FetchStep.GO || pet.fetch == FetchStep.PICKUP)) {
            Location b = pet.ball.location();
            pets.log().info(String.format("trace %s t%d %s dog %.2f %.2f %.2f yaw %.0f ball %.2f %.2f %.2f d %.2f speed %.3f going %s",
                    pet.fetch, pet.fetchTicks, pet.body.state(), now.getX(), now.getY(), now.getZ(), pet.body.location().getYaw(),
                    b.getX(), b.getY(), b.getZ(), Math.hypot(b.getX() - now.getX(), b.getZ() - now.getZ()), pet.legs.speed(), pet.legs.going()));
        }
        if (pets.debug && time % 10 == 0) {
            String line = String.format("%s (hold %s)  fetch %s  speed %.3f", pet.body.state(), hold, pet.fetch, pet.legs.speed());
            pet.owner.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(line));
            pets.log().info(line);
        }
    }
}
