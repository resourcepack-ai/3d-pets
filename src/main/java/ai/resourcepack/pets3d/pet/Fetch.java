package ai.resourcepack.pets3d.pet;

import ai.resourcepack.pets3d.ball.TennisBall;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.util.Vector;

/**
 * Playing fetch: out after the ball, pick it up, carry it back in the mouth
 * and drop it at its owner's feet. Where a dog is in that is its
 * {@link FetchStep}; this is what each step does, a tick at a time.
 */
final class Fetch {

    /** Run and trot speeds while fetching, blocks per tick. */
    static final double RUN = 0.24, TROT = 0.19;
    /** Walking up to the ball, as it does to one thrown close. */
    static final double APPROACH = 0.1;
    /** Ticks a teleported display takes to slide: aim the ball where the mouth will be when it lands. */
    static final int LEAD = 2;

    private final Pets pets;
    private final TennisBall balls;

    Fetch(Pets pets, TennisBall balls) {
        this.pets = pets;
        this.balls = balls;
    }

    /** The fetch's own steps; returns the state it wants held, or null to leave it to the legs. */
    String step(Pet pet) {
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
                    pet.setFetch(FetchStep.PICKUP);
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
                    pet.setFetch(FetchStep.RETURN);
                    return "walk";
                }
                if (pet.fetchTicks > 20 * 6) pet.setFetch(FetchStep.RETURN);
                return "pickup";
            }
            case RETURN -> {
                // Lift the head out of the pick-up before setting off.
                if (pet.body.state().startsWith("pickup")) return "walk";
                pet.legs.goTo(pet.owner.getLocation(), TROT, 1.6);
                if (pet.owner.getWorld() == w.getWorld() && pet.owner.getLocation().distance(w) < 1.9) {
                    pet.legs.stop();
                    pet.setFetch(FetchStep.DROP);
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
                    Item dropped = w.getWorld().dropItem(from, balls.item());
                    Vector toward = pet.owner.getLocation().toVector().subtract(from.toVector()).setY(0);
                    if (toward.lengthSquared() > 0.01) toward.normalize().multiply(0.12);
                    dropped.setVelocity(toward.setY(0.12));
                    dropped.setOwner(pet.owner.getUniqueId());
                    dropped.setPickupDelay(8);
                    pet.setFetch(FetchStep.NONE);
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
    void carry(Pet pet) {
        if (pet.ball == null || !pet.carrying) return;
        Location mouth = pet.body.point("mouth", LEAD);
        if (mouth == null) return;
        // The ball model stands on its lowest point: drop it by its radius.
        Location at = mouth.subtract(0, 0.065 * pet.body.scale(), 0);
        if (pet.fetch == FetchStep.PICKUP && pet.ballFrom != null && pet.fetchTicks < 5) {
            at = pet.ballFrom.clone().add(at.toVector().subtract(pet.ballFrom.toVector()).multiply(pet.fetchTicks / 5.0));
        }
        pet.ball.teleport(at);
        if (pets.debug && pet.owner.getWorld().getGameTime() % 5 == 0)
            pets.log().info(String.format("carry: %s %s, ball %.2f from the mouth, dog %.1f from you, ball display %s",
                    pet.body.state(), pet.fetch, pet.ball.location().distance(at), pet.body.location().distance(pet.owner.getLocation()),
                    pet.ball.display().isValid() ? "ok" : "gone"));
    }

    /** Calls a fetch off: the ball goes back in its owner's inventory and the dog back to following them. */
    void giveBack(Pet pet) {
        if (pet.ball != null) pet.ball.remove();
        pet.ball = null;
        pet.carrying = false;
        pet.thrown = null;
        pet.legs.stop();
        pet.legs.hold(null);
        pet.owner.getInventory().addItem(balls.item());
        pet.setFetch(FetchStep.NONE);
    }
}
