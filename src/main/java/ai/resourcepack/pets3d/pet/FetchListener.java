package ai.resourcepack.pets3d.pet;

import ai.resourcepack.pets3d.Models;
import ai.resourcepack.pets3d.ball.TennisBall;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

/**
 * Where a game of fetch starts: a tennis ball thrown sets its thrower's dog
 * running after it, and where it lands the ball's model is put down for the
 * dog to fetch.
 */
public final class FetchListener implements Listener {

    private final Pets pets;

    public FetchListener(Pets pets) {
        this.pets = pets;
    }

    @EventHandler
    public void onThrow(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball) || !TennisBall.is(snowball.getItem())) return;
        if (!(snowball.getShooter() instanceof Player player)) return;
        Pet pet = pets.get(player);
        if (pet == null || pet.fetch != FetchStep.NONE) return;
        pet.thrown = snowball;
        pet.autoSat = false;
        pet.sit(false);
        pet.setFetch(FetchStep.CHASE);
    }

    @EventHandler
    public void onLand(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball) || !TennisBall.is(snowball.getItem())) return;
        if (event.getHitEntity() != null) event.setCancelled(true);
        Location rest = TennisBall.restingPlace(snowball.getLocation());
        snowball.remove();
        Pet pet = pets.all().stream().filter(p -> p.thrown == snowball).findFirst().orElse(null);
        if (rest == null) {
            if (pet != null) pets.fetch().giveBack(pet);
            return;
        }
        if (pet == null) {
            // Nobody to fetch it: it is just a ball on the ground again.
            rest.getWorld().dropItem(rest, pets.balls().item());
            return;
        }
        pet.thrown = null;
        pet.ball = pets.engine().spawn(Models.BALL, rest);
        pet.setFetch(FetchStep.GO);
    }
}
