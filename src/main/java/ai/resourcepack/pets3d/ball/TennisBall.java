package ai.resourcepack.pets3d.ball;

import ai.resourcepack.engine3d.api.Engine3D;
import ai.resourcepack.pets3d.Models;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The tennis ball a player throws: a snowball wearing the ball's model,
 * marked so that it is told apart from any other snowball.
 */
public final class TennisBall {

    private static final NamespacedKey IS_BALL = new NamespacedKey("3dpets", "ball");

    private final Engine3D engine;
    private final Server server;

    public TennisBall(Engine3D engine, Server server) {
        this.engine = engine;
        this.server = server;
    }

    /** A new ball to throw. */
    public ItemStack item() {
        ItemStack model = engine.item(Models.BALL, null);
        ItemStack item = new ItemStack(Material.SNOWBALL);
        item.setItemMeta(server.getItemFactory().asMetaFor(model.getItemMeta(), Material.SNOWBALL));
        ItemMeta meta = item.getItemMeta();
        meta.setItemName(ChatColor.YELLOW + "Tennis Ball");
        meta.getPersistentDataContainer().set(IS_BALL, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Whether {@code item} is a tennis ball rather than any other snowball. */
    public static boolean is(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(IS_BALL);
    }

    /** Where a ball that came down near {@code at} comes to rest: on the first solid block below. */
    public static Location restingPlace(Location at) {
        Block b = at.getBlock();
        for (int i = 0; i < 24 && b.isPassable() && b.getY() > at.getWorld().getMinHeight(); i++) b = b.getRelative(0, -1, 0);
        if (b.isPassable()) return null;
        double top = b.getBoundingBox().getMaxY();
        if (top <= b.getY()) top = b.getY() + 1;
        return new Location(at.getWorld(), at.getX(), top, at.getZ());
    }
}
