package ai.resourcepack.pets3d;

import ai.resourcepack.engine3d.api.Engine3D;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * The dog's and the ball's models. Their files ship inside this jar and are
 * copied into 3D Engine's models folder, under {@code 3dpets/}, which is what
 * gives them the names below.
 */
public final class Models {

    /** The Labrador. */
    public static final String DOG = "3dpets/labrador";
    /** The tennis ball, in the dog's mouth and on the ground. */
    public static final String BALL = "3dpets/ball";

    private static final List<String> FILES = List.of("labrador.glb", "labrador-motion.glb", "labrador.yml", "ball.obj", "ball.mtl", "ball.png", "ball.yml");

    private Models() {
    }

    /** Copies the dog and the ball into the engine's models folder (before its first tick, when it bakes). */
    static void install(JavaPlugin plugin, Engine3D engine) {
        Path dir = engine.modelsFolder().resolve("3dpets");
        try {
            Files.createDirectories(dir);
            for (String f : FILES) {
                try (InputStream in = plugin.getResource("models/" + f)) {
                    if (in == null) continue;
                    byte[] bytes = in.readAllBytes();
                    Path to = dir.resolve(f);
                    if (!Files.exists(to) || !Arrays.equals(Files.readAllBytes(to), bytes)) Files.write(to, bytes);
                }
            }
            try (InputStream in = plugin.getResource("CREDITS.txt")) {
                if (in != null) Files.write(dir.resolve("CREDITS.txt"), in.readAllBytes());
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Couldn't install the models: " + e.getMessage());
        }
    }
}
