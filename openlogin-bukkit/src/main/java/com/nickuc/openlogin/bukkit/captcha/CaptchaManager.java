/*
 * The MIT License (MIT)
 *
 * Copyright © 2020 - 2026 - OpenLogin Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.sobble.pleasejustlogin.bukkit.captcha;

import com.sobble.pleasejustlogin.bukkit.OpenLoginBukkit;
import com.sobble.pleasejustlogin.common.settings.Settings;
import com.sobble.pleasejustlogin.common.util.CaptchaGenerator;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages CAPTCHA generation, validation, and map item handling.
 */
public class CaptchaManager {

    private final OpenLoginBukkit plugin;
    private final Map<String, CaptchaSession> activeCaptchas = new ConcurrentHashMap<>();

    public CaptchaManager(OpenLoginBukkit plugin) {
        this.plugin = plugin;
    }

    /**
     * Generate a CAPTCHA code and give the player a map item displaying it.
     *
     * @param player the player to give the CAPTCHA to
     * @return the generated CAPTCHA code
     */
    public String generateAndGiveCaptcha(Player player) {
        String playerName = player.getName().toLowerCase();

        // Remove any existing CAPTCHA
        if (hasCaptcha(playerName)) {
            removeCaptcha(playerName);
        }

        // Generate CAPTCHA code
        int codeLength = Settings.CAPTCHA_CODE_LENGTH.asInt();
        String code = CaptchaGenerator.generate(codeLength);

        // Create map view
        MapView mapView = createMapView(player);
        if (mapView == null) {
            plugin.sendMessage("§cFailed to create CAPTCHA map for " + player.getName());
            return null; // Signal failure to caller
        }

        // Clear existing renderers and add CAPTCHA renderer
        mapView.getRenderers().clear();
        mapView.addRenderer(new CaptchaMapRenderer(code));

        // Create map item
        ItemStack mapItem = createMapItem(mapView);

        // Give map to player and get the replaced item
        ItemStack replacedItem = giveMapToPlayer(player, mapItem);

        // Store session with replaced item
        long expirationTime = System.currentTimeMillis() +
                (Settings.CAPTCHA_EXPIRATION_TIME.asInt() * 1000L);
        activeCaptchas.put(playerName, new CaptchaSession(code, expirationTime, getMapId(mapView), replacedItem));

        return code;
    }

    /**
     * Validate a CAPTCHA code for a player.
     *
     * @param playerName the player's name
     * @param inputCode  the code entered by the player
     * @return true if the code is valid, false otherwise
     */
    public boolean validateCaptcha(String playerName, String inputCode) {
        String key = playerName.toLowerCase();
        CaptchaSession session = activeCaptchas.get(key);

        if (session == null) {
            return false;
        }

        if (session.isExpired()) {
            // Use removeCaptcha to properly clean up the map item
            removeCaptcha(playerName);
            return false;
        }

        // Case-insensitive comparison
        return session.getCode().equalsIgnoreCase(inputCode);
    }

    /**
     * Check if a player has an active CAPTCHA.
     *
     * @param playerName the player's name
     * @return true if the player has an active CAPTCHA
     */
    public boolean hasCaptcha(String playerName) {
        String key = playerName.toLowerCase();
        CaptchaSession session = activeCaptchas.get(key);

        if (session == null) {
            return false;
        }

        if (session.isExpired()) {
            // Use removeCaptcha to properly clean up the map item
            removeCaptcha(playerName);
            return false;
        }

        return true;
    }

    /**
     * Remove CAPTCHA for a player and clean up the map item.
     * Also restores the item that was replaced by the CAPTCHA map.
     *
     * @param playerName the player's name
     */
    public void removeCaptcha(String playerName) {
        String key = playerName.toLowerCase();
        CaptchaSession session = activeCaptchas.remove(key);

        // Remove map from player's inventory if they're online
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null && player.isOnline()) {
            removeCaptchaMap(player);
            
            // Restore the replaced item if there was one
            if (session != null && session.getReplacedItem() != null) {
                int slot = Settings.CAPTCHA_MAP_SLOT.asInt();
                if (slot >= 0 && slot <= 8) {
                    player.getInventory().setItem(slot, session.getReplacedItem());
                } else {
                    // If slot is invalid, add to inventory and drop leftovers
                    Map<Integer, ItemStack> leftovers = player.getInventory().addItem(session.getReplacedItem());
                    // Drop any items that couldn't fit
                    for (ItemStack leftover : leftovers.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                    }
                }
                player.updateInventory();
            }
        }
    }

    /**
     * Check if a player's CAPTCHA has expired.
     *
     * @param playerName the player's name
     * @return true if expired or doesn't exist
     */
    public boolean isExpired(String playerName) {
        String key = playerName.toLowerCase();
        CaptchaSession session = activeCaptchas.get(key);
        return session == null || session.isExpired();
    }

    /**
     * Clean up expired CAPTCHA sessions.
     * Should be called periodically by a scheduled task.
     */
    public void cleanupExpired() {
        Iterator<Map.Entry<String, CaptchaSession>> iterator = activeCaptchas.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CaptchaSession> entry = iterator.next();
            if (entry.getValue().isExpired()) {
                CaptchaSession session = entry.getValue();
                iterator.remove();

                // Try to remove map from player if online and restore replaced item
                Player player = Bukkit.getPlayerExact(entry.getKey());
                if (player != null && player.isOnline()) {
                    // Run all player operations on the entity thread
                    plugin.getFoliaLib().runAtEntity(player, task -> {
                        removeCaptchaMap(player);
                        
                        // Restore the replaced item if there was one
                        if (session.getReplacedItem() != null) {
                            int slot = Settings.CAPTCHA_MAP_SLOT.asInt();
                            if (slot >= 0 && slot <= 8) {
                                player.getInventory().setItem(slot, session.getReplacedItem());
                            } else {
                                // If slot is invalid, add to inventory and drop leftovers
                                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(session.getReplacedItem());
                                // Drop any items that couldn't fit
                                for (ItemStack leftover : leftovers.values()) {
                                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                                }
                            }
                            player.updateInventory();
                        }
                    });
                }
            }
        }
    }

    /**
     * Create a MapView for the CAPTCHA.
     */
    private MapView createMapView(Player player) {
        try {
            // Try modern API (1.13+)
            return Bukkit.createMap(player.getWorld());
        } catch (NoSuchMethodError e) {
            try {
                // Fallback for older versions
                return Bukkit.getServer().createMap(player.getWorld());
            } catch (Exception ex) {
                plugin.sendMessage("§cFailed to create map view: " + ex.getMessage());
                return null;
            }
        }
    }

    /**
     * Create a map ItemStack with CAPTCHA metadata.
     */
    private ItemStack createMapItem(MapView mapView) {
        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        ItemMeta meta = mapItem.getItemMeta();

        if (meta instanceof MapMeta) {
            MapMeta mapMeta = (MapMeta) meta;
            try {
                // Try modern API
                mapMeta.setMapView(mapView);
            } catch (NoSuchMethodError e) {
                // Fallback for older versions - use setMapId if available
                try {
                    mapMeta.getClass().getMethod("setMapId", int.class).invoke(mapMeta, getMapId(mapView));
                } catch (Exception ex) {
                    plugin.sendMessage("§cFailed to set map ID: " + ex.getMessage());
                }
            }
        }

        if (meta != null) {
            meta.setDisplayName("§c§lCAPTCHA Verification");
            meta.setLore(Arrays.asList(
                    "§7Enter the code shown on this map",
                    "§7in your login/register command"
            ));
            mapItem.setItemMeta(meta);
        }

        return mapItem;
    }

    /**
     * Get the map ID from a MapView.
     */
    private int getMapId(MapView mapView) {
        try {
            // Try modern API
            return mapView.getId();
        } catch (NoSuchMethodError e) {
            try {
                // Fallback for older versions
                return (short) mapView.getClass().getMethod("getId").invoke(mapView);
            } catch (Exception ex) {
                return -1;
            }
        }
    }

    /**
     * Give the CAPTCHA map to the player and return the replaced item.
     * @return The item that was replaced, or null if the slot was empty
     */
    private ItemStack giveMapToPlayer(Player player, ItemStack mapItem) {
        PlayerInventory inv = player.getInventory();
        int slot = Settings.CAPTCHA_MAP_SLOT.asInt();
        ItemStack replacedItem = null;

        if (slot >= 0 && slot <= 8) {
            // Save the existing item before replacing it
            replacedItem = inv.getItem(slot);
            // Clone the item to avoid reference issues
            if (replacedItem != null && replacedItem.getType() != Material.AIR) {
                replacedItem = replacedItem.clone();
            } else {
                replacedItem = null;
            }
            
            // Place in specific hotbar slot
            inv.setItem(slot, mapItem);
            // Switch player's held item to the CAPTCHA map slot so they can see it
            plugin.getFoliaLib().runAtEntity(player, task -> player.getInventory().setHeldItemSlot(slot));
        } else {
            // Add to first empty slot and drop leftovers
            Map<Integer, ItemStack> leftovers = inv.addItem(mapItem);
            // Drop any items that couldn't fit (shouldn't happen with a single map, but handle it)
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }

        player.updateInventory();
        return replacedItem;
    }

    /**
     * Remove CAPTCHA map from player's inventory.
     */
    private void removeCaptchaMap(Player player) {
        PlayerInventory inv = player.getInventory();

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == Material.FILLED_MAP) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName() &&
                        meta.getDisplayName().contains("CAPTCHA")) {
                    inv.setItem(i, null);
                }
            }
        }

        player.updateInventory();
    }

    /**
     * Internal class to store CAPTCHA session data.
     */
    @Getter
    private static class CaptchaSession {
        private final String code;
        private final long expirationTime;
        private final int mapId;
        private final ItemStack replacedItem; // Store the item that was replaced

        public CaptchaSession(String code, long expirationTime, int mapId, ItemStack replacedItem) {
            this.code = code;
            this.expirationTime = expirationTime;
            this.mapId = mapId;
            this.replacedItem = replacedItem;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }
}
