package tencer.quangdev05.combatManager;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class CombatCommandBlocker implements Listener {

    private CombatManager plugin;
    private List<String> blockedCommands;

    public CombatCommandBlocker(CombatManager plugin) {
        this.plugin = plugin;
        this.blockedCommands = plugin.getConfig().getStringList("blocked-commands");
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (plugin.isInCombat(event.getPlayer())) {
            String message = event.getMessage().toLowerCase();
            for (String command : blockedCommands) {
                if (message.startsWith("/" + command.toLowerCase())) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage("Bạn không thể sử dụng lệnh này khi đang trong trạng thái combat!");
                    return;
                }
            }
        }
    }
}