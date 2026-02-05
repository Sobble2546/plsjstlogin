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

package com.sobble.pleasejustlogin.bukkit.listener;

import com.sobble.pleasejustlogin.bukkit.OpenLoginBukkit;
import com.sobble.pleasejustlogin.bukkit.task.LoginQueue;
import com.sobble.pleasejustlogin.bukkit.ui.title.TitleAPI;
import com.sobble.pleasejustlogin.bukkit.util.TextComponentMessage;
import com.sobble.pleasejustlogin.common.model.Title;
import com.sobble.pleasejustlogin.common.settings.Messages;
import com.sobble.pleasejustlogin.common.settings.Settings;
import com.sobble.pleasejustlogin.common.util.ClassUtils;
import lombok.AllArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@AllArgsConstructor
public class PlayerJoinListeners implements Listener {

    private final OpenLoginBukkit plugin;

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        String name = player.getName();

        if (Settings.SPAWN_BEFORE_LOGIN_RETURN_LAST_LOCATION.asBoolean()) {
            plugin.rememberLoginLocation(player);
            plugin.markLoginTeleport(name);
            plugin.getFoliaLib().runAtEntity(player, task -> player.teleport(plugin.getLoginSpawnLocation(player)));
        }

        if (plugin.isNewUser()) {
            plugin.getFoliaLib().runLater(() -> {
                if (!player.isOnline()) {
                    return;
                }

                player.sendMessage("");
                player.sendMessage(" §eHello, " + player.getName() + "!");
                player.sendMessage("");
                player.sendMessage("  §7Welcome to §fPLEASE JUST LOGIN§7.");
                player.sendMessage("  §7Fork of OpenLogin with more features!");
                player.sendMessage("");
                player.sendMessage("  §7Notice: Generative AI was used to build");
                player.sendMessage("  §7the new features in this fork.");
                player.sendMessage("");

                if (ClassUtils.exists("net.md_5.bungee.api.chat.TextComponent")) {
                    TextComponentMessage.sendFirstRunAgreement(player);
                } else {
                    player.sendMessage(" §eTo continue, type: §f'/plsjstlogin setup'");
                }
                player.sendMessage("");

                TitleAPI.getApi().send(player,
                        new Title("", "§ePlease accept the first-run notice.", 0, 9999, 10));
            }, 30L);

            e.setJoinMessage("");
            return;
        }

        boolean registered = plugin.getAccountManagement().retrieveOrLoad(name).isPresent();
        LoginQueue.addToQueue(name, registered);

        player.setWalkSpeed(0F);
        player.setFlySpeed(0F);

        // Handle player visibility if invisible feature is enabled
        if (Settings.INVISIBLE_WHILE_UNAUTHENTICATED.asBoolean()) {
            // Hide unauthenticated players from everyone (including each other)
            for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                if (!onlinePlayer.equals(player)) {
                    boolean joinerAuth = plugin.getLoginManagement().isAuthenticated(name);
                    boolean onlineAuth = plugin.getLoginManagement().isAuthenticated(onlinePlayer.getName());
                    
                    // If either player is unauthenticated, hide them from each other
                    if (!joinerAuth || !onlineAuth) {
                        player.hidePlayer(onlinePlayer);
                        onlinePlayer.hidePlayer(player);
                    }
                }
            }
        }

        // Check if CAPTCHA is enabled
        boolean captchaEnabled = Settings.CAPTCHA_ENABLED.asBoolean();
        boolean captchaOnLogin = captchaEnabled && Settings.CAPTCHA_USE_ON_LOGIN.asBoolean();
        boolean captchaOnRegister = captchaEnabled && Settings.CAPTCHA_USE_ON_REGISTER.asBoolean();

        if (registered) {
            if (captchaOnLogin) {
                // Generate CAPTCHA immediately for login
                String captchaCode = plugin.getCaptchaManager().generateAndGiveCaptcha(player);
                if (captchaCode != null) {
                    player.sendMessage(Messages.CAPTCHA_MAP_GIVEN.asString());
                    player.sendMessage(Messages.CAPTCHA_INSTRUCTION.asString());
                    player.sendMessage(Messages.MESSAGE_LOGIN_CAPTCHA.asString());
                } else {
                    // CAPTCHA generation failed, log and fall back to normal login
                    plugin.getLogger().warning("Failed to generate CAPTCHA for player " + name + " (login). Falling back to normal login.");
                    player.sendMessage(Messages.MESSAGE_LOGIN.asString());
                }
            } else {
                player.sendMessage(Messages.MESSAGE_LOGIN.asString());
            }
            TitleAPI.getApi().send(player, Messages.TITLE_BEFORE_LOGIN.asTitle());
        } else {
            if (captchaOnRegister) {
                // Generate CAPTCHA immediately for registration
                String captchaCode = plugin.getCaptchaManager().generateAndGiveCaptcha(player);
                if (captchaCode != null) {
                    player.sendMessage(Messages.CAPTCHA_MAP_GIVEN.asString());
                    player.sendMessage(Messages.CAPTCHA_INSTRUCTION.asString());
                    player.sendMessage(Messages.MESSAGE_REGISTER_CAPTCHA.asString());
                } else {
                    // CAPTCHA generation failed, log and fall back to normal registration
                    plugin.getLogger().warning("Failed to generate CAPTCHA for player " + name + " (register). Falling back to normal registration.");
                    player.sendMessage(Messages.MESSAGE_REGISTER.asString());
                }
            } else {
                player.sendMessage(Messages.MESSAGE_REGISTER.asString());
            }
            TitleAPI.getApi().send(player, Messages.TITLE_BEFORE_REGISTER.asTitle());
        }
    }
}
