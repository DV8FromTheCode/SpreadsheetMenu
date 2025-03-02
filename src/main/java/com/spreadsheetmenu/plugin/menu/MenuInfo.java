package com.spreadsheetmenu.plugin.menu;

import java.io.File;

public class MenuInfo {
    
    private final String menuId;
    private final String menuName;
    private final String openCondition;
    private final String permission;
    private final boolean escapeable;
    private File configFile;
    
    public MenuInfo(String menuId, String menuName, String openCondition, String permission, boolean escapeable) {
        this.menuId = menuId;
        this.menuName = menuName;
        this.openCondition = openCondition;
        this.permission = permission;
        this.escapeable = escapeable;
    }
    
    public String getMenuId() {
        return menuId;
    }
    
    public String getMenuName() {
        return menuName;
    }
    
    public String getOpenCondition() {
        return openCondition;
    }
    
    public String getPermission() {
        return permission;
    }
    
    public boolean isEscapeable() {
        return escapeable;
    }
    
    public File getConfigFile() {
        return configFile;
    }
    
    public void setConfigFile(File configFile) {
        this.configFile = configFile;
    }
} 