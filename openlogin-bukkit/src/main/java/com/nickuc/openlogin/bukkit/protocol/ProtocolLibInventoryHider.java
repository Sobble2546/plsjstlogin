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

package com.sobble.pleasejustlogin.bukkit.protocol;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.sobble.pleasejustlogin.bukkit.OpenLoginBukkit;
import com.sobble.pleasejustlogin.common.settings.Settings;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ProtocolLibInventoryHider {

    private final OpenLoginBukkit plugin;
    private final ProtocolManager protocolManager;

    public ProtocolLibInventoryHider(OpenLoginBukkit plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    public void register() {
        protocolManager.addPacketListener(new PacketAdapter(this.plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.OPEN_WINDOW,
                PacketType.Play.Server.WINDOW_ITEMS,
                PacketType.Play.Server.SET_SLOT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                if (!ProtocolLibInventoryHider.this.plugin.getLoginManagement().isAuthenticated(player.getName())) {
                    // Check if CAPTCHA is enabled
                    boolean captchaEnabled = Settings.CAPTCHA_ENABLED.asBoolean();
                    if (captchaEnabled && event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
                        // Allow CAPTCHA map to be visible
                        if (isCaptchaMap(event)) {
                            return; // Don't cancel, allow the CAPTCHA map packet
                        }
                    }
                    event.setCancelled(true);
                }
            }
            
            private boolean isCaptchaMap(PacketEvent event) {
                try {
                    StructureModifier<ItemStack> items = event.getPacket().getItemModifier();
                    ItemStack item = items.read(0);
                    
                    if (item != null && item.getType() == Material.FILLED_MAP) {
                        ItemMeta meta = item.getItemMeta();
                        if (meta != null && meta.hasDisplayName()) {
                            return meta.getDisplayName().contains("CAPTCHA");
                        }
                    }
                } catch (Exception e) {
                    // Ignore errors
                }
                return false;
            }
        });
    }
}
