package com.spreadsheetmenu.plugin.config;

import com.spreadsheetmenu.plugin.SpreadsheetMenu;
import com.spreadsheetmenu.plugin.menu.MenuInfo;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.bukkit.ChatColor;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Stream;

public class ConfigManager {
    
    private final SpreadsheetMenu plugin;
    private final Path dataFolder;
    private final Path menusFolder;
    private final Path coreMenusFile;
    
    private Map<String, MenuInfo> menuInfoMap;
    private List<String> validationErrors;
    
    public ConfigManager(SpreadsheetMenu plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder().toPath();
        this.menusFolder = dataFolder.resolve("menus");
        this.coreMenusFile = dataFolder.resolve("core_menus.csv");
        this.menuInfoMap = new HashMap<>();
        this.validationErrors = new ArrayList<>();
        
        // Create necessary folders
        try {
            Files.createDirectories(menusFolder);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create menus directory", e);
        }
        
        // Create default files if they don't exist
        createDefaultFiles();
    }
    
    private void createDefaultFiles() {
        if (!Files.exists(coreMenusFile)) {
            try {
                Files.copy(plugin.getResource("core_menus.csv"), coreMenusFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create default core_menus.csv", e);
            }
        }
        
        // Copy all menu files from resources to the menus directory
        try {
            // Get all menu files from core_menus.csv
            if (Files.exists(coreMenusFile)) {
                List<String> menuIds = new ArrayList<>();
                try (CSVParser csvParser = CSVParser.parse(
                        coreMenusFile,
                        java.nio.charset.StandardCharsets.UTF_8,
                        CSVFormat.DEFAULT
                            .withHeader("menu_id", "menu_name", "open_condition", "permission", "escapeable")
                            .withFirstRecordAsHeader()
                            .withIgnoreHeaderCase()
                            .withTrim())) {
                    
                    for (CSVRecord record : csvParser) {
                        menuIds.add(record.get("menu_id"));
                    }
                }
                
                // Copy each menu file if it doesn't exist
                for (String menuId : menuIds) {
                    Path menuFile = menusFolder.resolve(menuId + ".csv");
                    if (Files.notExists(menuFile)) {
                        // Check if the resource exists
                        if (plugin.getResource(menuId + ".csv") != null) {
                            Files.copy(plugin.getResource(menuId + ".csv"), menuFile, StandardCopyOption.REPLACE_EXISTING);
                            plugin.getLogger().info("Created default menu file: " + menuId + ".csv");
                        } else {
                            plugin.getLogger().warning("Menu " + menuId + " is defined in core_menus.csv but no template file exists in the plugin resources.");
                        }
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create default menu files", e);
        }
    }
    
    public boolean loadConfigs() {
        menuInfoMap.clear();
        validationErrors.clear();
        
        // Load core menus configuration
        loadCoreMenus();
        
        // Load individual menu configurations
        loadMenuConfigs();
        
        // Log validation results
        if (validationErrors.isEmpty()) {
            plugin.getLogger().info("Loaded " + menuInfoMap.size() + " menus from configuration. All menu files are valid.");
            return true;
        } else {
            plugin.getLogger().warning("Loaded " + menuInfoMap.size() + " menus from configuration with " + validationErrors.size() + " validation errors:");
            for (String error : validationErrors) {
                plugin.getLogger().warning("- " + error);
            }
            return false;
        }
    }
    
    private void loadCoreMenus() {
        if (!Files.exists(coreMenusFile)) {
            plugin.getLogger().warning("core_menus.csv not found. Creating default file.");
            createDefaultFiles();
            return;
        }
        
        try (CSVParser csvParser = CSVParser.parse(
                coreMenusFile,
                java.nio.charset.StandardCharsets.UTF_8,
                CSVFormat.DEFAULT
                    .withHeader("menu_id", "menu_name", "open_condition", "permission", "escapeable")
                    .withFirstRecordAsHeader()
                    .withIgnoreHeaderCase()
                    .withTrim())) {
            
            for (CSVRecord record : csvParser) {
                String menuId = record.get("menu_id");
                String menuName = ChatColor.translateAlternateColorCodes('&', record.get("menu_name"));
                String openCondition = record.get("open_condition");
                String permission = record.get("permission");
                boolean escapeable = Boolean.parseBoolean(record.get("escapeable"));
                
                // Create MenuInfo object
                MenuInfo menuInfo = new MenuInfo(menuId, menuName, openCondition, permission, escapeable);
                menuInfoMap.put(menuId, menuInfo);
                
                // Register menu permission if it's a regular permission (not a PlaceholderAPI condition)
                if (!permission.isEmpty() && !permission.startsWith("%")) {
                    plugin.getMenuManager().ensurePermissionExists(permission);
                }
            }
            
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading core_menus.csv", e);
            validationErrors.add("Failed to parse core_menus.csv: " + e.getMessage());
        }
    }
    
    private void loadMenuConfigs() {
        try (Stream<Path> menuFiles = Files.list(menusFolder)) {
            menuFiles
                .filter(path -> path.toString().toLowerCase().endsWith(".csv"))
                .forEach(this::processMenuFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error reading menus directory", e);
            validationErrors.add("Failed to read menus directory: " + e.getMessage());
        }
    }
    
    private void processMenuFile(Path menuFile) {
        String menuId = menuFile.getFileName().toString().replace(".csv", "");
        
        // Skip if menu info doesn't exist in core_menus.csv
        if (!menuInfoMap.containsKey(menuId)) {
            String error = "Menu file " + menuFile.getFileName() + " exists but is not defined in core_menus.csv. Skipping.";
            plugin.getLogger().warning(error);
            validationErrors.add(error);
            return;
        }
        
        // Load menu items from the CSV file
        try (CSVParser csvParser = CSVParser.parse(
                menuFile,
                java.nio.charset.StandardCharsets.UTF_8,
                CSVFormat.DEFAULT
                    .withHeader()
                    .withFirstRecordAsHeader()
                    .withIgnoreHeaderCase()
                    .withTrim())) {
            
            // Validate CSV structure
            boolean hasRequiredColumns = csvParser.getHeaderMap().containsKey("slot") &&
                                        csvParser.getHeaderMap().containsKey("material");
            
            if (!hasRequiredColumns) {
                String error = "Menu file " + menuFile.getFileName() + " is missing required columns (slot, material).";
                plugin.getLogger().warning(error);
                validationErrors.add(error);
                return;
            }
            
            // Validate menu items
            boolean hasValidItems = false;
            for (CSVRecord record : csvParser) {
                try {
                    int slot = Integer.parseInt(record.get("slot"));
                    String material = record.get("material");
                    
                    if (slot >= 0 && !material.isEmpty()) {
                        hasValidItems = true;
                        break;
                    }
                } catch (NumberFormatException e) {
                    // Skip invalid rows
                }
            }
            
            if (!hasValidItems) {
                String error = "Menu file " + menuFile.getFileName() + " does not contain any valid menu items.";
                plugin.getLogger().warning(error);
                validationErrors.add(error);
                return;
            }
            
            MenuInfo menuInfo = menuInfoMap.get(menuId);
            menuInfo.setConfigFile(menuFile.toFile());
            
            plugin.getLogger().info("Registered menu: " + menuId);
            
        } catch (IOException e) {
            String error = "Error loading menu file: " + menuFile.getFileName() + " - " + e.getMessage();
            plugin.getLogger().log(Level.SEVERE, error);
            validationErrors.add(error);
        }
    }
    
    public Map<String, MenuInfo> getMenuInfoMap() {
        return menuInfoMap;
    }
    
    public MenuInfo getMenuInfo(String menuId) {
        return menuInfoMap.get(menuId);
    }
    
    public List<String> getValidationErrors() {
        return validationErrors;
    }
    
    /**
     * Forces a reload of all menu files from the plugin resources
     * This is useful for debugging or when menu files are missing
     */
    public void forceReloadMenuFiles() {
        try {
            // First, ensure the core_menus.csv file exists
            Files.copy(plugin.getResource("core_menus.csv"), coreMenusFile, StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Force reloaded core_menus.csv from resources");
            
            // Get all menu files from core_menus.csv
            List<String> menuIds = new ArrayList<>();
            try (CSVParser csvParser = CSVParser.parse(
                    coreMenusFile,
                    java.nio.charset.StandardCharsets.UTF_8,
                    CSVFormat.DEFAULT
                        .withHeader("menu_id", "menu_name", "open_condition", "permission", "escapeable")
                        .withFirstRecordAsHeader()
                        .withIgnoreHeaderCase()
                        .withTrim())) {
                
                for (CSVRecord record : csvParser) {
                    menuIds.add(record.get("menu_id"));
                }
            }
            
            // Copy each menu file from resources
            for (String menuId : menuIds) {
                Path menuFile = menusFolder.resolve(menuId + ".csv");
                
                // Check if the resource exists
                if (plugin.getResource(menuId + ".csv") != null) {
                    Files.copy(plugin.getResource(menuId + ".csv"), menuFile, StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("Force reloaded menu file: " + menuId + ".csv");
                } else {
                    plugin.getLogger().warning("Menu " + menuId + " is defined in core_menus.csv but no template file exists in the plugin resources.");
                }
            }
            
            // Reload the configurations
            loadConfigs();
            
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to force reload menu files", e);
        }
    }
} 