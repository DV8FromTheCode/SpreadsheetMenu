package com.spreadsheetmenu.plugin.commands;

import com.spreadsheetmenu.plugin.SpreadsheetMenu;
import com.spreadsheetmenu.plugin.menu.MenuInfo;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SpreadsheetMenuCommand implements CommandExecutor, TabCompleter {
    
    private final SpreadsheetMenu plugin;
    
    public SpreadsheetMenuCommand(SpreadsheetMenu plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "reload":
                // Bypass permission check for OP players
                if (!sender.isOp() && !sender.hasPermission("spreadsheetmenu.reload")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to reload the plugin.");
                    return true;
                }
                
                sender.sendMessage(ChatColor.YELLOW + "Reloading SpreadsheetMenu configuration...");
                
                // Reload configs
                boolean success = plugin.getConfigManager().loadConfigs();
                
                if (success) {
                    sender.sendMessage(ChatColor.GREEN + "Configuration reloaded successfully!");
                    
                    // Close all open menus to prevent issues with outdated configurations
                    plugin.getMenuManager().closeAllMenus();
                    
                    sender.sendMessage(ChatColor.GREEN + "All open menus have been closed.");
                } else {
                    sender.sendMessage(ChatColor.RED + "There were errors reloading the configuration.");
                    List<String> errors = plugin.getConfigManager().getValidationErrors();
                    for (String error : errors) {
                        sender.sendMessage(ChatColor.RED + "- " + error);
                    }
                }
                
                return true;
                
            case "forcereload":
                // Bypass permission check for OP players
                if (!sender.isOp() && !sender.hasPermission("spreadsheetmenu.reload")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to force reload the plugin.");
                    return true;
                }
                
                sender.sendMessage(ChatColor.YELLOW + "Force reloading all menu files from resources...");
                
                // Force reload all menu files
                plugin.getConfigManager().forceReloadMenuFiles();
                
                // Close all open menus to prevent issues with outdated configurations
                plugin.getMenuManager().closeAllMenus();
                
                sender.sendMessage(ChatColor.GREEN + "All menu files have been force reloaded from resources.");
                sender.sendMessage(ChatColor.GREEN + "All open menus have been closed.");
                
                return true;
                
            case "open":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " open <menu_id> [player|selector]");
                    return true;
                }
                
                String menuId = args[1];
                
                // If no player/selector is specified, open for the sender if they're a player
                if (args.length == 2) {
                    if (sender instanceof Player) {
                        Player player = (Player) sender;
                        if (plugin.getMenuManager().openMenu(player, menuId)) {
                            sender.sendMessage(ChatColor.GREEN + "Opened menu '" + menuId + "'.");
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "Usage from console: /" + label + " open <menu_id> <player|selector>");
                    }
                    return true;
                }
                
                // Open for specified player or selector
                String targetSelector = args[2];
                return openForTargets(sender, menuId, targetSelector);
                
            case "list":
                // Bypass permission check for OP players
                if (!sender.isOp() && !sender.hasPermission("spreadsheetmenu.command")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to list menus.");
                    return true;
                }
                
                Map<String, MenuInfo> menus = plugin.getConfigManager().getMenuInfoMap();
                
                if (menus.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "No menus are currently configured.");
                    return true;
                }
                
                sender.sendMessage(ChatColor.GREEN + "Available menus:");
                for (MenuInfo info : menus.values()) {
                    String permission = info.getPermission().isEmpty() ? "None" : info.getPermission();
                    sender.sendMessage(ChatColor.YELLOW + "- " + info.getMenuId() + 
                            ChatColor.GRAY + " (Permission: " + permission + ")");
                }
                
                sender.sendMessage(ChatColor.YELLOW + "TIP: Use /" + label + " open <menu_id> to open any menu.");
                return true;
                
            default:
                sendHelp(sender);
                return true;
        }
    }
    
    /**
     * Opens a menu for players matching the given selector
     * 
     * @param sender The command sender
     * @param menuId The menu ID to open
     * @param targetSelector The player name or selector
     * @return true if the command was successful
     */
    private boolean openForTargets(CommandSender sender, String menuId, String targetSelector) {
        // Check if the menu exists
        if (!plugin.getConfigManager().getMenuInfoMap().containsKey(menuId)) {
            sender.sendMessage(ChatColor.RED + "Menu not found: " + menuId);
            return true;
        }
        
        // Check if this is a selector
        if (targetSelector.startsWith("@")) {
            List<Player> targets = new ArrayList<>();
            
            try {
                // Use Bukkit's selector system for all selector types
                targets.addAll(Bukkit.selectEntities(sender, targetSelector).stream()
                        .filter(entity -> entity instanceof Player)
                        .map(entity -> (Player) entity)
                        .collect(Collectors.toList()));
                
                // Handle special case for console sender with @p
                if (targets.isEmpty() && targetSelector.equals("@p") && !(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Cannot use @p from console without a location.");
                    return true;
                }
                
                // Handle special case for console sender with @s
                if (targets.isEmpty() && targetSelector.equals("@s") && !(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Cannot use @s from console.");
                    return true;
                }
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Error processing selector: " + e.getMessage());
                return true;
            }
            
            if (targets.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "No players found matching selector: " + targetSelector);
                return true;
            }
            
            int successCount = 0;
            for (Player target : targets) {
                if (plugin.getMenuManager().openMenu(target, menuId)) {
                    successCount++;
                }
            }
            
            sender.sendMessage(ChatColor.GREEN + "Opened menu '" + menuId + "' for " + successCount + " player(s).");
            return true;
        } else {
            // Process as a player name
            Player target = Bukkit.getPlayer(targetSelector);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + targetSelector);
                return true;
            }
            
            if (plugin.getMenuManager().openMenu(target, menuId)) {
                sender.sendMessage(ChatColor.GREEN + "Opened menu '" + menuId + "' for " + target.getName() + ".");
                return true;
            } else {
                return true;
            }
        }
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "=== SpreadsheetMenu Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/spm reload" + ChatColor.GRAY + " - Reload the plugin configuration");
        sender.sendMessage(ChatColor.YELLOW + "/spm forcereload" + ChatColor.GRAY + " - Force reload all menu files from resources");
        sender.sendMessage(ChatColor.YELLOW + "/spm open <menu_id> [player|selector]" + ChatColor.GRAY + " - Open a menu for player(s)");
        sender.sendMessage(ChatColor.YELLOW + "/spm list" + ChatColor.GRAY + " - List all available menus");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            subCommands.add("reload");
            subCommands.add("forcereload");
            subCommands.add("open");
            subCommands.add("list");
            
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("open")) {
            Map<String, MenuInfo> menus = plugin.getConfigManager().getMenuInfoMap();
            
            completions = menus.keySet().stream()
                    .filter(menuId -> menuId.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("open")) {
            String current = args[2].toLowerCase();
            
            // Add player name completions
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(current)) {
                    completions.add(player.getName());
                }
            }
            
            // Add basic selectors
            List<String> basicSelectors = List.of("@p", "@a", "@r", "@s");
            for (String selector : basicSelectors) {
                if (selector.startsWith(current)) {
                    completions.add(selector);
                }
            }
            
            // Add advanced selector patterns if the user has started typing one
            if (current.startsWith("@")) {
                String selectorBase = current.split("\\[", 2)[0]; // Get the base selector (@p, @a, etc.)
                
                // If they've typed a valid selector base
                if (basicSelectors.contains(selectorBase)) {
                    // If they haven't started the bracket yet, suggest it
                    if (!current.contains("[")) {
                        completions.add(selectorBase + "[");
                    } 
                    // If they've started the bracket but haven't closed it
                    else if (current.contains("[") && !current.endsWith("]")) {
                        // Get what they've typed inside the brackets so far
                        String insideBrackets = current.substring(current.indexOf("[") + 1);
                        
                        // Suggest common selector arguments
                        List<String> selectorArgs = List.of(
                            "distance=", "limit=", "sort=nearest", "sort=furthest", "sort=random",
                            "level=", "gamemode=survival", "gamemode=creative", "gamemode=adventure", "gamemode=spectator",
                            "name=", "team=", "tag=", "scores=", "advancements=", "predicate="
                        );
                        
                        // If they've started typing an argument
                        if (!insideBrackets.isEmpty()) {
                            // If they've typed a comma, suggest a new argument
                            if (insideBrackets.endsWith(",")) {
                                for (String arg : selectorArgs) {
                                    completions.add(current + arg);
                                }
                            } 
                            // Otherwise, filter based on what they've typed
                            else {
                                String lastArg = insideBrackets.contains(",") 
                                    ? insideBrackets.substring(insideBrackets.lastIndexOf(",") + 1).trim() 
                                    : insideBrackets.trim();
                                
                                for (String arg : selectorArgs) {
                                    if (arg.startsWith(lastArg)) {
                                        // Replace the current partial argument with the full one
                                        String newCompletion = current.substring(0, current.length() - lastArg.length()) + arg;
                                        completions.add(newCompletion);
                                    }
                                }
                            }
                        } 
                        // If they've just opened the bracket, suggest all arguments
                        else {
                            for (String arg : selectorArgs) {
                                completions.add(current + arg);
                            }
                        }
                        
                        // Always suggest closing the bracket
                        completions.add(current + "]");
                    }
                }
            }
        }
        
        return completions;
    }
} 