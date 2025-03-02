package com.spreadsheetmenu.plugin.listeners;

import com.spreadsheetmenu.plugin.SpreadsheetMenu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MenuListener implements Listener {
    
    private final SpreadsheetMenu plugin;
    
    public MenuListener(SpreadsheetMenu plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        String openMenu = plugin.getMenuManager().getOpenMenu(player);
        
        if (openMenu != null) {
            event.setCancelled(true);
            
            // Handle the click if it's in the top inventory
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        if (player.isOnline()) {
                            plugin.getMenuManager().handleMenuClick(player, event.getSlot());
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error handling menu click for player " + player.getName() + ": " + e.getMessage());
                        if (e.getCause() != null) {
                            plugin.getLogger().warning("Caused by: " + e.getCause().getMessage());
                        }
                    }
                });
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        String openMenu = plugin.getMenuManager().getOpenMenu(player);
        
        if (openMenu != null) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        String openMenu = plugin.getMenuManager().getOpenMenu(player);
        
        if (openMenu != null) {
            // Check if the menu is escapeable or if it's a forced close from a [close] command
            if (!plugin.getMenuManager().isMenuEscapeable(player)) {
                // Reopen the menu in the next tick
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        if (player.isOnline() && plugin.getMenuManager().getOpenMenu(player) != null) {
                            plugin.getMenuManager().openMenu(player, openMenu);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error reopening menu for player " + player.getName() + ": " + e.getMessage());
                        if (e.getCause() != null) {
                            plugin.getLogger().warning("Caused by: " + e.getCause().getMessage());
                        }
                    }
                });
            } else {
                try {
                    // Only call closeMenu if it's not already being closed by a [close] command
                    if (!plugin.getMenuManager().isForcedClose(player)) {
                        plugin.getMenuManager().closeMenu(player);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error closing menu for player " + player.getName() + ": " + e.getMessage());
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        try {
            plugin.getMenuManager().closeMenu(player);
        } catch (Exception e) {
            plugin.getLogger().warning("Error closing menu on player quit for " + player.getName() + ": " + e.getMessage());
        }
    }
} 