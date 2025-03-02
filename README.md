# SpreadsheetMenu

A Minecraft plugin for Paper 1.21 that creates inventory GUIs which are configured using CSV files.

## Features

- Create custom inventory menus using simple CSV files
- Configure menu properties like name, permissions, and escape behavior
- Support for PlaceholderAPI in menu items and conditions
- Execute commands when clicking on menu items
- Open other menus from menu items
- Prevent players from closing certain menus
- Multiple items per slot with priority-based display
- Conditional item visibility using PlaceholderAPI expressions

## Installation

1. Download the latest release from the releases page
2. Place the JAR file in your server's `plugins` folder
3. Restart your server
4. Edit the configuration files in the `plugins/SpreadsheetMenu` directory

## Configuration

### Core Menus Configuration

The `core_menus.csv` file defines the basic properties of each menu:

| Column | Description |
|--------|-------------|
| menu_id | Unique identifier for the menu |
| menu_name | Display name of the menu (supports color codes with &) |
| open_condition | PlaceholderAPI expression that must evaluate to true for the menu to open |
| permission | Permission required to open the menu |
| escapeable | Whether the player can close the menu with the escape key (true/false) |

### Menu Configuration

Each menu has its own CSV file named after its `menu_id`. For example, `example_menu.csv` for a menu with ID `example_menu`.

Menu item configuration columns:

| Column | Description |
|--------|-------------|
| slot | Inventory slot number (0-53) |
| material | Bukkit Material name |
| amount | Item stack size |
| name | Display name of the item (supports color codes with &) |
| lore | Item description (use \| to separate lines) |
| command | Command to execute when clicked |
| priority | Priority number for multiple items in the same slot (higher numbers = higher priority) |
| show_condition | PlaceholderAPI expression that must evaluate to true for the item to be shown |

### Multiple Items Per Slot

You can configure multiple items for the same slot with different priorities and show conditions. The system will:
1. Check which items' show conditions are met
2. Among the visible items, display the one with the highest priority
3. If multiple items have the same priority, the first one in the file is used
4. If no show condition is specified (empty string), the item is always visible

Example configuration:
```csv
slot,material,amount,name,lore,command,priority,show_condition
0,DIAMOND_SWORD,1,&bDiamond Sword,&7Requires permission,[give] diamond_sword,100,"%player_has_permission_some.perm%"
0,IRON_SWORD,1,&7Iron Sword,&7Default option,[give] iron_sword,0,""
```

In this example:
- If the player has the permission, they see the Diamond Sword (priority 100)
- If they don't have the permission, they see the Iron Sword (priority 0)

### Commands

Commands in the menu items can have special prefixes:

- `[player]` - Execute command as the player
- `[console]` - Execute command as the console
- `[close]` - Close the menu
- `[open]` - Open another menu

Example: `[console] give %player_name% DIAMOND 5`

## Usage

### In-game Commands

- `/spreadsheetmenu reload` or `/spm reload` - Reload the plugin configuration
- `/spreadsheetmenu open <menu_id>` or `/spm open <menu_id>` - Open a specific menu
- `/spreadsheetmenu list` or `/spm list` - List all available menus

### Permissions

- `spreadsheetmenu.command` - Access to the main command
- `spreadsheetmenu.reload` - Permission to reload the plugin
- Custom permissions for each menu as defined in `core_menus.csv`

## Example

The plugin comes with an example menu configuration to help you get started. The example includes demonstrations of:
- Basic menu items
- Multiple items per slot with priorities
- Conditional visibility using permissions
- Various command types

## Dependencies

- Paper 1.21+
- PlaceholderAPI

## Building from Source

1. Clone the repository
   ```
   git clone https://github.com/yourusername/SpreadsheetMenu.git
   cd SpreadsheetMenu
   ```
2. Run `mvn clean package`
3. The compiled JAR will be in the `target` directory

## License

This project is licensed under the MIT License - see the LICENSE file for details. 