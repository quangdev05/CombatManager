package tencer.quangdev05.combatManager;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CombatManager extends JavaPlugin implements Listener {

    private Map<UUID, Long> combatPlayers = new HashMap<>();
    private long combatDuration;
    private List<String> quitCommands;

    @Override
    public void onEnable() {
        // Đăng ký sự kiện
        Bukkit.getPluginManager().registerEvents(this, this);

        // Đăng ký blocker cho lệnh
        Bukkit.getPluginManager().registerEvents(new CombatCommandBlocker(this), this);

        // Lấy thời gian combat từ config
        reloadPluginConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("combat")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("combat.reload") || sender.isOp()) {
                    reloadPluginConfig();
                    sender.sendMessage("Config đã được tải lại.");
                } else {
                    sender.sendMessage("Bạn không có quyền sử dụng lệnh này.");
                }
                return true;
            }
        }
        return false;
    }

    private void reloadPluginConfig() {
        this.saveDefaultConfig(); // Tạo lại config nếu chưa tồn tại
        this.reloadConfig(); // Tải lại config từ file
        FileConfiguration config = this.getConfig();
        combatDuration = config.getLong("combat-duration", 5) * 1000L;
        // Cập nhật các cài đặt khác nếu cần
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            Player damaged = (Player) event.getEntity();
            Player damager = (Player) event.getDamager();

            // Đưa cả hai vào trạng thái combat
            combatPlayers.put(damaged.getUniqueId(), System.currentTimeMillis());
            combatPlayers.put(damager.getUniqueId(), System.currentTimeMillis());

            // Thông báo cho người chơi
            damaged.sendMessage("Bạn đã vào trạng thái combat!");
            damager.sendMessage("Bạn đã vào trạng thái combat!");

            // Bắt đầu đếm ngược thời gian combat
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (combatPlayers.containsKey(damaged.getUniqueId()) &&
                            System.currentTimeMillis() - combatPlayers.get(damaged.getUniqueId()) > combatDuration) {
                        combatPlayers.remove(damaged.getUniqueId());
                        damaged.sendMessage("Bạn đã thoát khỏi trạng thái combat!");
                    }
                    if (combatPlayers.containsKey(damager.getUniqueId()) &&
                            System.currentTimeMillis() - combatPlayers.get(damager.getUniqueId()) > combatDuration) {
                        combatPlayers.remove(damager.getUniqueId());
                        damager.sendMessage("Bạn đã thoát khỏi trạng thái combat!");
                    }
                }
            }.runTaskTimer(this, 20L, 20L); // Lặp lại mỗi giây
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (combatPlayers.containsKey(player.getUniqueId())) {
            // Thực thi các lệnh khi người chơi thoát trong trạng thái combat
            for (String command : quitCommands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
            }

            // Loại bỏ người chơi khỏi trạng thái combat
            combatPlayers.remove(player.getUniqueId());
        }
    }

    public boolean isInCombat(Player player) {
        return combatPlayers.containsKey(player.getUniqueId());
    }
}
