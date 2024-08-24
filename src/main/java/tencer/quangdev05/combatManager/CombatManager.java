package tencer.quangdev05.combatManager;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CombatManager extends JavaPlugin implements Listener {

    private Map<UUID, Long> combatPlayers = new HashMap<>();
    private long combatDuration;
    private List<String> quitCommands;
    private List<String> blacklistedWorlds;
    private Map<UUID, BossBar> playerBossBars = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new CombatCommandBlocker(this), this);
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
        this.saveDefaultConfig();
        this.reloadConfig();
        FileConfiguration config = this.getConfig();
        combatDuration = config.getLong("combat-duration", 5) * 1000L;
        quitCommands = config.getStringList("quit-commands");
        blacklistedWorlds = config.getStringList("blacklisted-worlds");
    }

    private void createBossBar(Player player) {
    BossBar bossBar = Bukkit.createBossBar("Bạn đang trong trạng thái combat!", BarColor.RED, BarStyle.SOLID);
    bossBar.addPlayer(player);
    playerBossBars.put(player.getUniqueId(), bossBar);
}

private void updateBossBar(Player player, double progress) {
    BossBar bossBar = playerBossBars.get(player.getUniqueId());
    if (bossBar != null) {
        bossBar.setProgress(progress);
    }
}

private void removeBossBar(Player player) {
    BossBar bossBar = playerBossBars.get(player.getUniqueId());
    if (bossBar != null) {
        bossBar.removeAll();
        playerBossBars.remove(player.getUniqueId());
    }
}

@EventHandler
public void onPlayerDamage(EntityDamageByEntityEvent event) {
    if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
        Player damaged = (Player) event.getEntity();
        Player damager = (Player) event.getDamager();

        if (blacklistedWorlds.contains(damaged.getWorld().getName())
                || blacklistedWorlds.contains(damager.getWorld().getName())) {
            return;
        }

        // Update or create combat status
        combatPlayers.put(damaged.getUniqueId(), System.currentTimeMillis());
        combatPlayers.put(damager.getUniqueId(), System.currentTimeMillis());

        // Check if boss bars need to be created
        if (!playerBossBars.containsKey(damaged.getUniqueId())) {
            createBossBar(damaged);
        }
        if (!playerBossBars.containsKey(damager.getUniqueId())) {
            createBossBar(damager);
        }

        new BukkitRunnable() {
            long startTime = System.currentTimeMillis();

            @Override
            public void run() {
                long elapsedTime = System.currentTimeMillis() - startTime;
                double timeLeft = combatDuration - elapsedTime;
                double progress = timeLeft / (double) combatDuration;

                if (progress <= 0) {
                    combatPlayers.remove(damaged.getUniqueId());
                    combatPlayers.remove(damager.getUniqueId());
                    removeBossBar(damaged);
                    removeBossBar(damager);
                    this.cancel();
                } else {
                    updateBossBar(damaged, progress);
                    updateBossBar(damager, progress);
                }
            }
        }.runTaskTimer(this, 0L, 20L); // Runs every second
    }
}

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (combatPlayers.containsKey(player.getUniqueId())) {
            for (String command : quitCommands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
            }
            removeBossBar(player);
            combatPlayers.remove(player.getUniqueId());
        }
    }

    public boolean isInCombat(Player player) {
        Long combatTime = combatPlayers.get(player.getUniqueId());
        if (combatTime == null) {
            return false; // Người chơi không có trong trạng thái combat
        }
        // Kiểm tra xem người chơi có còn trong trạng thái combat dựa trên thời gian
        // combat
        long duration = System.currentTimeMillis() - combatTime;
        return duration < combatDuration;
    }
}
