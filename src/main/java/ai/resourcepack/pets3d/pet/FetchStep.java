package ai.resourcepack.pets3d.pet;

/** Where a dog is in a game of fetch. */
enum FetchStep {
    /** Not playing. */
    NONE,
    /** Running after a ball still in the air. */
    CHASE,
    /** Going to where the ball came to rest. */
    GO,
    /** Picking it up. */
    PICKUP,
    /** Bringing it back. */
    RETURN,
    /** Dropping it at its owner's feet. */
    DROP
}
