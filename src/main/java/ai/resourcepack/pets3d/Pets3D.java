package ai.resourcepack.pets3d;

import ai.resourcepack.common.update.UpdateCheck;
import ai.resourcepack.engine3d.api.Engine3D;
import ai.resourcepack.pets3d.ball.TennisBall;
import ai.resourcepack.pets3d.command.PetCommand;
import ai.resourcepack.pets3d.pet.FetchListener;
import ai.resourcepack.pets3d.pet.PetListener;
import ai.resourcepack.pets3d.pet.Pets;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 3D Pets: a dog that follows you about, built on 3D Engine.
 *
 * The engine draws it, animates it and walks it; this plugin is only what
 * makes it a dog. It potters about near you, stops to sniff, sits down when
 * you've stood still a while (and gets up when you leave), sits on a
 * right-click, and fetches: throw it a tennis ball and it runs after it,
 * picks it up, carries it back in its mouth and drops it at your feet.
 *
 * This class only puts the pieces together:
 * <ul>
 *   <li>{@code pet}: the dogs themselves, what each does by itself every
 *   tick, and the game of fetch;</li>
 *   <li>{@code ball}: the tennis ball a player throws;</li>
 *   <li>{@code command}: {@code /pet};</li>
 *   <li>{@link Models}: the dog's and the ball's model files, installed into
 *   3D Engine's models folder.</li>
 * </ul>
 */
public final class Pets3D extends JavaPlugin {

    /** This plugin's SpigotMC resource id: the number in its page's URL. */
    private static final int SPIGOT_RESOURCE = 139512;

    private Pets pets;
    private UpdateCheck updates;

    @Override
    public void onLoad() {
        // Asked before the worlds load, so the answer is back for the startup banner.
        ai.resourcepack.common.update.UpdateCheck.early(this, SPIGOT_RESOURCE);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (updates != null) updates.stop();
        updates = UpdateCheck.spigot(this, SPIGOT_RESOURCE)
                .name("3D Pets")
                .permission("3dpets.update")
                .enabledWhen(() -> getConfig().getBoolean("update-check", true))
                .player(UpdateNotice::tell);
        ai.resourcepack.common.banner.StartupBanner banner = ai.resourcepack.common.banner.StartupBanner.begin(this)
                .name("3D Pets")
                .addonOf("3D Engine")
                .tagline("A dog that follows you and plays fetch.")
                .docs("https://resourcepack.ai/docs/3d-pets")
                .updates(updates);
        Engine3D engine = Engine3D.get();
        Models.install(this, engine);
        TennisBall balls = new TennisBall(engine, getServer());
        pets = new Pets(this, engine, balls);
        getServer().getPluginManager().registerEvents(new PetListener(pets), this);
        getServer().getPluginManager().registerEvents(new FetchListener(pets), this);
        getServer().getScheduler().runTaskTimer(this, pets::tick, 1L, 1L);
        // A reload re-bakes the models: give everyone their dog back on the new ones.
        engine.whenReady(pets::replaceStale);
        PluginCommand pet = getCommand("pet");
        if (pet != null) {
            PetCommand command = new PetCommand(getServer(), engine, pets, balls);
            pet.setExecutor(command);
            pet.setTabCompleter(command);
        }
        updates.start();
        banner.ok("Commands", "/pet for a dog");
        banner.print();
    }

    @Override
    public void onDisable() {
        if (updates != null) updates.stop();
        if (pets != null) pets.removeAll();
    }
}
