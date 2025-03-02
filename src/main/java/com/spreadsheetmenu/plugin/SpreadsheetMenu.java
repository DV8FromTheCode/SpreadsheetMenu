package com.spreadsheetmenu.plugin;

import com.spreadsheetmenu.plugin.commands.SpreadsheetMenuCommand;
import com.spreadsheetmenu.plugin.config.ConfigManager;
import com.spreadsheetmenu.plugin.listeners.MenuListener;
import com.spreadsheetmenu.plugin.menu.MenuManager;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

public class SpreadsheetMenu extends JavaPlugin {
    
    private static SpreadsheetMenu instance;
    private ConfigManager configManager;
    private MenuManager menuManager;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Create data folder if it doesn't exist
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        
        // Initialize managers
        configManager = new ConfigManager(this);
        menuManager = new MenuManager(this);
        
        // Load configurations
        boolean configLoaded = configManager.loadConfigs();
        
        // Register commands
        getCommand("spreadsheetmenu").setExecutor(new SpreadsheetMenuCommand(this));
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        
        // Register common permissions dynamically
        registerCommonPermissions();
        
        getLogger().info("SpreadsheetMenu has been enabled!");
    }
    
    /**
     * Registers common permissions dynamically
     * This eliminates the need to define these permissions in plugin.yml
     */
    private void registerCommonPermissions() {
        // Register general plugin permissions
        String[] generalPermissions = {
            "storage", "vip", "admin"
        };
        
        for (String permName : generalPermissions) {
            String permissionName = "spreadsheetmenu." + permName;
            
            // Only register if not already registered
            if (getServer().getPluginManager().getPermission(permissionName) == null) {
                Permission permission = new Permission(
                    permissionName,
                    "Access to " + permName + " features",
                    PermissionDefault.OP
                );
                
                getServer().getPluginManager().addPermission(permission);
                getLogger().info("Dynamically registered permission: " + permissionName);
            }
        }
        
        // Common item categories
        String[] itemCategories = {
            "diamond", "emerald", "gold", "iron", "netherite", 
            "special", "sword", "armor", "tools", "food", "blocks"
        };
        
        for (String category : itemCategories) {
            String permissionName = "spreadsheetmenu.items." + category;
            
            // Only register if not already registered
            if (getServer().getPluginManager().getPermission(permissionName) == null) {
                Permission permission = new Permission(
                    permissionName,
                    "Access to " + category + " items in menus",
                    PermissionDefault.OP
                );
                
                getServer().getPluginManager().addPermission(permission);
                getLogger().info("Dynamically registered permission: " + permissionName);
            }
        }
    }
    
    @Override
    public void onDisable() {
        // Clean up resources
        if (menuManager != null) {
            menuManager.closeAllMenus();
        }
        
        getLogger().info("SpreadsheetMenu has been disabled!");
    }
    
    public static SpreadsheetMenu getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public MenuManager getMenuManager() {
        return menuManager;
    }
} 