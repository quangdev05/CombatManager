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
    private Map<UUID, BossBar> playerBossBars = new HashMap<>();
    private long combatDuration;
    private List<String> quitCommands;
    private List<String> blacklistedWorlds;

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

    private BossBar createBossBar(Player player) {
        BossBar bossBar = Bukkit.createBossBar("Bạn đang trong trạng thái combat!", BarColor.RED, BarStyle.SOLID);
        bossBar.addPlayer(player);
        playerBossBars.put(player.getUniqueId(), bossBar);
        return bossBar;
    }

    private void updateBossBar(Player player, double progress) {
        BossBar bossBar = playerBossBars.get(player.getUniqueId());
        if (bossBar != null) {
            bossBar.setProgress(progress);
            if (!bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }
        }
    }

    private void removeBossBar(Player player) {
        BossBar bossBar = playerBossBars.get(player.getUniqueId());
        if (bossBar != null) {
            bossBar.removeAll();
            playerBossBars.remove(player.getUniqueId());
        }
    }

    private void handleCombat(Player damaged, Player damager) {
        // Kiểm tra nếu người chơi ở trong thế giới bị cấm
        if (blacklistedWorlds.contains(damaged.getWorld().getName()) || blacklistedWorlds.contains(damager.getWorld().getName())) {
            return;
        }

        // Đặt lại thời gian combat
        combatPlayers.put(damaged.getUniqueId(), System.currentTimeMillis());
        combatPlayers.put(damager.getUniqueId(), System.currentTimeMillis());

        // Tạo hoặc cập nhật BossBar cho người chơi bị sát thương
        BossBar damagedBossBar = playerBossBars.computeIfAbsent(damaged.getUniqueId(), uuid -> createBossBar(damaged));
        updateBossBar(damaged, 1.0);

        // Tạo hoặc cập nhật BossBar cho người chơi gây sát thương
        BossBar damagerBossBar = playerBossBars.computeIfAbsent(damager.getUniqueId(), uuid -> createBossBar(damager));
        updateBossBar(damager, 1.0);

        // Đặt thời gian để cập nhật BossBar
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
        }.runTaskTimer(this, 0L, 20L); // Chạy mỗi giây
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            Player damaged = (Player) event.getEntity();
            Player damager = (Player) event.getDamager();
            handleCombat(damaged, damager);
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
        return combatTime != null && (System.currentTimeMillis() - combatTime) < combatDuration;
    }
}
