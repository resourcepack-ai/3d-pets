package ai.resourcepack.pets3d;

import ai.resourcepack.common.update.UpdateCheck;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/** What a player with {@code 3dpets.update} is told when a newer 3D Pets is out. */
final class UpdateNotice {

    private UpdateNotice() {
    }

    /** The join nudge: the sentence, then the link as one clickable piece. */
    static void tell(Player player, UpdateCheck.Update update) {
        BaseComponent words = TextComponent.fromLegacy(
                ChatColor.GRAY + "3D Pets " + ChatColor.WHITE + update.latest()
                        + ChatColor.GRAY + " is out (you have " + update.running() + "). ");
        TextComponent link = new TextComponent("Get it on SpigotMC");
        link.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
        link.setUnderlined(true);
        link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, update.url()));
        link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.hover.content.Text(update.url())));
        // Both go in a fresh component: a deserialised one's extras can't be added to.
        player.spigot().sendMessage(new TextComponent(words, link));
    }
}
