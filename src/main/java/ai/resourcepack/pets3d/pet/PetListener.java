package ai.resourcepack.pets3d.pet;

import ai.resourcepack.engine3d.api.event.ModelInteractEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** A dog goes home when its owner leaves, and sits or gets up when they right-click it. */
public final class PetListener implements Listener {

    private final Pets pets;

    public PetListener(Pets pets) {
        this.pets = pets;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pets.sendHome(event.getPlayer());
    }

    // A right-click on the dog is the owner's say: sit, or get up.
    @EventHandler
    public void onInteract(ModelInteractEvent event) {
        Pet pet = pets.get(event.getPlayer());
        if (pet == null || event.getModel() != pet.legs.body()) return;
        event.setCancelled(true);
        if (pet.fetch != FetchStep.NONE) return;
        pet.autoSat = false;
        pet.sit(!pet.sitting);
    }
}
