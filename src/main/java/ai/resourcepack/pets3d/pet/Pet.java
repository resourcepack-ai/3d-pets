package ai.resourcepack.pets3d.pet;

import ai.resourcepack.engine3d.api.ModelInstance;
import ai.resourcepack.engine3d.api.Mover;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;

/** One player's dog: the model drawn, the legs that walk it, and what it is up to. */
final class Pet {

    final Player owner;
    final ModelInstance body;
    final Mover legs;

    // Its own life: sitting (and whether it sat by itself), how long its
    // owner has stood still, how long it has stood about, and its sniffing.
    boolean sitting, autoSat;
    int ownerStill;
    Location ownerLast;
    int idleTicks;
    long sniffUntil;

    // A game of fetch: where it is in one, the ball in the air or on the
    // ground, and the ball in its mouth.
    FetchStep fetch = FetchStep.NONE;
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

    void setFetch(FetchStep next) {
        // Following its owner would take over whenever the fetch stands
        // still: thrown far, it turned for home mid pick-up.
        if (next == FetchStep.NONE) legs.follow(owner);
        else if (fetch == FetchStep.NONE) legs.unfollow();
        fetch = next;
        fetchTicks = 0;
    }

    void sit(boolean sit) {
        sitting = sit;
        legs.freeze(sit);
        legs.hold(sit ? "sit" : null);
    }
}
