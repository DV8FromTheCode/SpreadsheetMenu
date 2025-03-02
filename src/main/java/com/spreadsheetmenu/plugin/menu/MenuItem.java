package com.spreadsheetmenu.plugin.menu;

import org.bukkit.inventory.ItemStack;

public class MenuItem {
    private final ItemStack item;
    private final String command;
    private final int priority;
    private final String showCondition;

    public MenuItem(ItemStack item, String command, int priority, String showCondition) {
        this.item = item;
        this.command = command;
        this.priority = priority;
        this.showCondition = showCondition;
    }

    public ItemStack getItem() {
        return item;
    }

    public String getCommand() {
        return command;
    }

    public int getPriority() {
        return priority;
    }

    public String getShowCondition() {
        return showCondition;
    }
} 