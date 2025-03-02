package com.spreadsheetmenu.plugin.menu;

import com.spreadsheetmenu.plugin.SpreadsheetMenu;
import me.clip.placeholderapi.PlaceholderAPI;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MenuManager {
    
    private final SpreadsheetMenu plugin;
    private final Map<UUID, String> openMenus;
    private final Map<UUID, Inventory> playerMenus;
    private final Map<UUID, Map<Integer, List<MenuItem>>> playerMenuItems;
    private final Set<UUID> forcedCloseMenus; // Track players whose menus are being closed by [close] command
    private final Map<String, Permission> dynamicPermissions; // Cache for dynamically registered permissions
    
    public MenuManager(SpreadsheetMenu plugin) {
        this.plugin = plugin;
        this.openMenus = new HashMap<>();
        this.playerMenus = new HashMap<>();
        this.playerMenuItems = new HashMap<>();
        this.forcedCloseMenus = new HashSet<>();
        this.dynamicPermissions = new HashMap<>();
    }
    
    public boolean openMenu(Player player, String menuId) {
        MenuInfo menuInfo = plugin.getConfigManager().getMenuInfo(menuId);
        
        if (menuInfo == null) {
            player.sendMessage(ChatColor.RED + "Menu not found: " + menuId);
            return false;
        }
        
        // Check permission (bypass for OP players)
        if (!menuInfo.getPermission().isEmpty() && !player.isOp()) {
            String permissionName = menuInfo.getPermission();
            
            // Check if this is a PlaceholderAPI condition instead of a permission
            if (permissionName.startsWith("%") && permissionName.endsWith("%")) {
                if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                    String condition = PlaceholderAPI.setPlaceholders(player, permissionName);
                    if (!Boolean.parseBoolean(condition)) {
                        player.sendMessage(ChatColor.RED + "You don't have permission to open this menu.");
                        return false;
                    }
                } else {
                    plugin.getLogger().warning("Menu " + menuId + " uses PlaceholderAPI condition but PlaceholderAPI is not installed.");
                    player.sendMessage(ChatColor.RED + "Cannot check permission for this menu.");
                    return false;
                }
            } else {
                // Regular permission check
                // Register the permission dynamically if it doesn't exist
                ensurePermissionExists(permissionName);
                
                if (!player.hasPermission(permissionName)) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to open this menu.");
                    return false;
                }
            }
        }
        
        // Check open condition if PlaceholderAPI is available
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") && !menuInfo.getOpenCondition().isEmpty()) {
            String condition = PlaceholderAPI.setPlaceholders(player, menuInfo.getOpenCondition());
            if (!Boolean.parseBoolean(condition)) {
                player.sendMessage(ChatColor.RED + "You cannot open this menu right now.");
                return false;
            }
        }
        
        // Create and open the inventory
        Inventory inventory = createInventory(player, menuInfo);
        if (inventory == null) {
            player.sendMessage(ChatColor.RED + "Failed to create menu: " + menuId);
            return false;
        }
        
        player.openInventory(inventory);
        openMenus.put(player.getUniqueId(), menuId);
        playerMenus.put(player.getUniqueId(), inventory);
        
        return true;
    }
    
    /**
     * Ensures that a permission exists in the server's permission system
     * If it doesn't exist, it will be registered dynamically
     * 
     * @param permissionName The name of the permission to check/register
     */
    public void ensurePermissionExists(String permissionName) {
        // Check if we've already registered this permission
        if (dynamicPermissions.containsKey(permissionName)) {
            return;
        }
        
        // Check if the permission already exists in the server
        if (Bukkit.getPluginManager().getPermission(permissionName) == null) {
            // Create and register the permission
            Permission permission = new Permission(
                permissionName,
                "Dynamically registered permission for menu access",
                PermissionDefault.OP
            );
            
            Bukkit.getPluginManager().addPermission(permission);
            dynamicPermissions.put(permissionName, permission);
            plugin.getLogger().info("Dynamically registered permission: " + permissionName);
        }
    }
    
    private Inventory createInventory(Player player, MenuInfo menuInfo) {
        if (menuInfo.getConfigFile() == null || !menuInfo.getConfigFile().exists()) {
            plugin.getLogger().warning("Menu config file not found for: " + menuInfo.getMenuId());
            return null;
        }
        
        // Determine inventory size (must be a multiple of 9)
        int inventorySize = 54; // Default to 6 rows
        
        // Create inventory
        Inventory inventory = Bukkit.createInventory(null, inventorySize, menuInfo.getMenuName());
        
        // Create a map to store items for each slot
        Map<Integer, List<MenuItem>> slotItems = new HashMap<>();
        
        // Load items from CSV
        try (Reader reader = new FileReader(menuInfo.getConfigFile());
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withHeader("slot", "material", "amount", "name", "lore", "command", "priority", "show_condition")
                     .withFirstRecordAsHeader()
                     .withIgnoreHeaderCase()
                     .withTrim())) {
            
            for (CSVRecord record : csvParser) {
                try {
                    int slot = Integer.parseInt(record.get("slot"));
                    
                    // Skip if slot is out of bounds
                    if (slot < 0 || slot >= inventorySize) {
                        plugin.getLogger().warning("Slot " + slot + " is out of bounds for menu: " + menuInfo.getMenuId());
                        continue;
                    }
                    
                    // Create item
                    String materialName = record.get("material").toUpperCase();
                    Material material = Material.getMaterial(materialName);
                    
                    if (material == null) {
                        plugin.getLogger().warning("Invalid material: " + materialName + " in menu: " + menuInfo.getMenuId());
                        continue;
                    }
                    
                    int amount = 1;
                    try {
                        amount = Integer.parseInt(record.get("amount"));
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid amount in menu: " + menuInfo.getMenuId() + ", using default: 1");
                    }
                    
                    ItemStack item = new ItemStack(material, amount);
                    ItemMeta meta = item.getItemMeta();
                    
                    if (meta != null) {
                        // Set name if provided
                        String name = record.get("name");
                        if (!name.isEmpty()) {
                            name = ChatColor.translateAlternateColorCodes('&', name);
                            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                                name = PlaceholderAPI.setPlaceholders(player, name);
                            }
                            meta.setDisplayName(name);
                        }
                        
                        // Set lore if provided
                        String loreString = record.get("lore");
                        if (!loreString.isEmpty()) {
                            List<String> lore = new ArrayList<>();
                            for (String line : loreString.split("\\|")) {
                                line = ChatColor.translateAlternateColorCodes('&', line);
                                if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                                    line = PlaceholderAPI.setPlaceholders(player, line);
                                }
                                lore.add(line);
                            }
                            meta.setLore(lore);
                        }
                        
                        item.setItemMeta(meta);
                    }
                    
                    int priority = 0;
                    try {
                        priority = Integer.parseInt(record.get("priority"));
                    } catch (NumberFormatException e) {
                        // Use default priority of 0
                    }
                    
                    String command = record.get("command");
                    String showCondition = record.get("show_condition");
                    
                    // Register any permission used in show_condition
                    if (!showCondition.isEmpty() && showCondition.contains("player_has_permission_")) {
                        // Extract permission from condition like %player_has_permission_spreadsheetmenu.items.diamond%
                        String permissionStr = showCondition.replaceAll("%player_has_permission_([^%]+)%", "$1");
                        if (!permissionStr.equals(showCondition)) {
                            // Register the permission dynamically
                            ensurePermissionExists(permissionStr);
                        }
                    }
                    
                    MenuItem menuItem = new MenuItem(item, command, priority, showCondition);
                    
                    // Add the item to the slot's list
                    slotItems.computeIfAbsent(slot, k -> new ArrayList<>()).add(menuItem);
                    
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error loading menu item in " + menuInfo.getMenuId(), e);
                }
            }
            
            // Process each slot and set the highest priority visible item
            for (Map.Entry<Integer, List<MenuItem>> entry : slotItems.entrySet()) {
                int slot = entry.getKey();
                List<MenuItem> items = entry.getValue();
                
                // Sort items by priority (highest first)
                items.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
                
                // Find the first visible item
                for (MenuItem item : items) {
                    String showCondition = item.getShowCondition();
                    
                    // OP players bypass permission checks
                    if (player.isOp() && showCondition.contains("player_has_permission_")) {
                        inventory.setItem(slot, item.getItem());
                        break;
                    }
                    
                    if (showCondition.isEmpty() || 
                        Boolean.parseBoolean(PlaceholderAPI.setPlaceholders(player, showCondition))) {
                        inventory.setItem(slot, item.getItem());
                        break;
                    }
                }
            }
            
            // Store the items for this player's menu
            playerMenuItems.put(player.getUniqueId(), slotItems);
            
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error reading menu config: " + menuInfo.getMenuId(), e);
            return null;
        }
        
        return inventory;
    }
    
    public void closeMenu(Player player) {
        // Mark this player as having a forced menu close
        forcedCloseMenus.add(player.getUniqueId());
        
        player.closeInventory();
        openMenus.remove(player.getUniqueId());
        playerMenus.remove(player.getUniqueId());
        playerMenuItems.remove(player.getUniqueId());
        
        // Schedule removal of the forced close flag after a short delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            forcedCloseMenus.remove(player.getUniqueId());
        }, 5L); // 5 ticks (0.25 seconds) should be enough
    }
    
    public void closeAllMenus() {
        for (UUID playerId : new HashSet<>(openMenus.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                closeMenu(player);
            }
        }
        openMenus.clear();
        playerMenus.clear();
        playerMenuItems.clear();
        forcedCloseMenus.clear();
    }
    
    public String getOpenMenu(Player player) {
        return openMenus.get(player.getUniqueId());
    }
    
    public boolean isMenuEscapeable(Player player) {
        // If this is a forced close, always allow it
        if (forcedCloseMenus.contains(player.getUniqueId())) {
            return true;
        }
        
        String menuId = openMenus.get(player.getUniqueId());
        if (menuId == null) {
            return true;
        }
        
        MenuInfo menuInfo = plugin.getConfigManager().getMenuInfo(menuId);
        return menuInfo == null || menuInfo.isEscapeable();
    }
    
    public boolean isForcedClose(Player player) {
        return forcedCloseMenus.contains(player.getUniqueId());
    }
    
    public boolean handleMenuClick(Player player, int slot) {
        String menuId = openMenus.get(player.getUniqueId());
        if (menuId == null) {
            return false;
        }
        
        Map<Integer, List<MenuItem>> slotItems = playerMenuItems.get(player.getUniqueId());
        if (slotItems == null) {
            return false;
        }
        
        List<MenuItem> items = slotItems.get(slot);
        if (items == null || items.isEmpty()) {
            return false;
        }
        
        // Find the first visible item and execute its command
        for (MenuItem item : items) {
            String showCondition = item.getShowCondition();
            
            // OP players bypass permission checks
            boolean canUse = player.isOp() && showCondition.contains("player_has_permission_");
            
            // Otherwise check the condition normally
            if (!canUse) {
                canUse = showCondition.isEmpty() || 
                    Boolean.parseBoolean(PlaceholderAPI.setPlaceholders(player, showCondition));
            }
            
            if (canUse) {
                String command = item.getCommand();
                if (command == null || command.isEmpty()) {
                    return true;
                }
                
                // Handle special command prefixes
                if (command.startsWith("[player]")) {
                    player.performCommand(command.substring(8).trim());
                } else if (command.startsWith("[console]")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                        PlaceholderAPI.setPlaceholders(player, command.substring(9).trim()));
                } else if (command.startsWith("[close]")) {
                    closeMenu(player);
                } else if (command.startsWith("[open]")) {
                    String newMenuId = command.substring(6).trim();
                    closeMenu(player);
                    openMenu(player, newMenuId);
                } else {
                    player.performCommand(command);
                }
                
                return true;
            }
        }
        
        return false;
    }
} 