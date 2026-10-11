package ai.resourcepack.pets3d.pet;

import ai.resourcepack.engine3d.api.Engine3D;
import ai.resourcepack.engine3d.api.ModelInstance;
import ai.resourcepack.engine3d.api.Mover;
import ai.resourcepack.pets3d.Models;
import ai.resourcepack.pets3d.ball.TennisBall;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Every player's dog, one each: calling one, sending it home, and running each
 * of them every tick.
 */
public final class Pets {

    private final JavaPlugin plugin;
    private final Engine3D engine;
    private final TennisBall balls;
    private final Map<UUID, Pet> pets = new HashMap<>();
    private final Fetch fetch;
    private final PetBehaviour behaviour;
    /** Set by /pet debug: every fetch traced to the console and the owner's action bar. */
    boolean debug;

    public Pets(JavaPlugin plugin, Engine3D engine, TennisBall balls) {
        this.plugin = plugin;
        this.engine = engine;
        this.balls = balls;
        this.fetch = new Fetch(this, balls);
        this.behaviour = new PetBehaviour(this, fetch);
    }

    // ---- what a player asks for ------------------------------------------------------

    /** Gives {@code owner} a dog: they must not have one, and the engine must have baked it. */
    public void summon(Player owner) {
        pets.put(owner.getUniqueId(), spawn(owner));
    }

    /** Sends {@code owner}'s dog home; false if they had none. */
    public boolean sendHome(Player owner) {
        Pet pet = pets.remove(owner.getUniqueId());
        if (pet == null) return false;
        remove(pet);
        return true;
    }

    /** Sits {@code owner}'s dog down, or gets it up. */
    public void toggleSit(Player owner) {
        Pet pet = pets.get(owner.getUniqueId());
        if (pet != null) {
            pet.autoSat = false;
            pet.sit(!pet.sitting);
        }
    }

    /** Lands a ball {@code far} blocks ahead of {@code owner} and sends their dog for it, for testing. */
    public void throwBall(Player owner, double far) {
        Pet pet = pets.get(owner.getUniqueId());
        if (pet == null || pet.fetch != FetchStep.NONE) return;
        Location ahead = owner.getLocation().add(owner.getLocation().getDirection().setY(0).normalize().multiply(far));
        Location rest = TennisBall.restingPlace(ahead.add(0, 3, 0));
        if (rest == null) return;
        pet.ball = engine.spawn(Models.BALL, rest);
        pet.autoSat = false;
        pet.sit(false);
        pet.setFetch(FetchStep.GO);
    }

    /** Turns fetch tracing on or off for every dog; returns whether it is now on. */
    public boolean toggleDebug() {
        debug = !debug;
        return debug;
    }

    // ---- the plugin's own ------------------------------------------------------------

    /** Each tick: forgets dogs that are gone or whose owner is, and runs the rest. */
    public void tick() {
        pets.entrySet().removeIf(entry -> {
            Pet pet = entry.getValue();
            if (!pet.legs.valid() || !pet.owner.isOnline()) {
                remove(pet);
                return true;
            }
            behaviour.tick(pet);
            return false;
        });
    }

    /** After a re-bake: every dog whose model went with the old bake comes back on the new one. */
    public void replaceStale() {
        for (Pet pet : List.copyOf(pets.values())) {
            if (pet.body.valid()) continue;
            pets.remove(pet.owner.getUniqueId());
            if (pet.owner.isOnline()) pets.put(pet.owner.getUniqueId(), spawn(pet.owner));
        }
    }

    /** Sends every dog home, as the plugin stops. */
    public void removeAll() {
        pets.values().forEach(this::remove);
        pets.clear();
    }

    // ---- for the rest of this package ------------------------------------------------

    Pet get(Player owner) {
        return pets.get(owner.getUniqueId());
    }

    Collection<Pet> all() {
        return pets.values();
    }

    Engine3D engine() {
        return engine;
    }

    TennisBall balls() {
        return balls;
    }

    Fetch fetch() {
        return fetch;
    }

    Logger log() {
        return plugin.getLogger();
    }

    private Pet spawn(Player owner) {
        ModelInstance body = engine.spawn(Models.DOG, owner.getLocation());
        Mover legs = engine.mover(body, EntityType.WOLF);
        legs.follow(owner);
        return new Pet(owner, body, legs);
    }

    /** Takes a dog away, and gives its owner back the ball if it had one out. */
    private void remove(Pet pet) {
        if (pet.ball != null || pet.thrown != null) {
            if (pet.ball != null) pet.ball.remove();
            if (pet.owner.isOnline()) pet.owner.getInventory().addItem(balls.item());
        }
        pet.legs.remove();
    }
}
