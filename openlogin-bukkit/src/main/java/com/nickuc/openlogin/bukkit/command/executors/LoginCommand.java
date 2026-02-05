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

package com.sobble.pleasejustlogin.bukkit.command.executors;

import com.sobble.pleasejustlogin.bukkit.OpenLoginBukkit;
import com.sobble.pleasejustlogin.bukkit.api.events.AsyncAuthenticateEvent;
import com.sobble.pleasejustlogin.bukkit.api.events.AsyncLoginEvent;
import com.sobble.pleasejustlogin.bukkit.captcha.CaptchaManager;
import com.sobble.pleasejustlogin.bukkit.command.BukkitAbstractCommand;
import com.sobble.pleasejustlogin.bukkit.ui.title.TitleAPI;
import com.sobble.pleasejustlogin.common.manager.AccountManagement;
import com.sobble.pleasejustlogin.common.manager.LoginManagement;
import com.sobble.pleasejustlogin.common.model.Account;
import com.sobble.pleasejustlogin.common.settings.Messages;
import com.sobble.pleasejustlogin.common.settings.Settings;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;

import java.util.Optional;

public class LoginCommand extends BukkitAbstractCommand {

    public LoginCommand(OpenLoginBukkit plugin) {
        super(plugin, "login");
    }

    protected void perform(CommandSender sender, String lb, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Messages.PLAYER_COMMAND_USAGE.asString());
            return;
        }

        Player player = (Player) sender;
        String name = sender.getName();
        LoginManagement loginManagement = plugin.getLoginManagement();
        
        if (loginManagement.isAuthenticated(name)) {
            sender.sendMessage(Messages.ALREADY_LOGIN.asString());
            return;
        }

        // Check if CAPTCHA is enabled for login
        boolean captchaEnabled = Settings.CAPTCHA_ENABLED.asBoolean()
                               && Settings.CAPTCHA_USE_ON_LOGIN.asBoolean();
        
        CaptchaManager captchaManager = plugin.getCaptchaManager();
        
        if (captchaEnabled) {
            // Expect 2 arguments: <password> <captcha>
            if (args.length != 2) {
                sender.sendMessage(Messages.MESSAGE_LOGIN_CAPTCHA.asString());
                return;
            }
            
            String password = args[0];
            String captchaInput = args[1];
            
            // Validate CAPTCHA first
            if (!captchaManager.validateCaptcha(name, captchaInput)) {
                captchaManager.removeCaptcha(name);
                plugin.getFoliaLib().runAtEntity(player, task ->
                    player.kickPlayer(Messages.CAPTCHA_INCORRECT.asString())
                );
                return;
            }
            
            // CAPTCHA valid, remove it and proceed with password validation
            captchaManager.removeCaptcha(name);
            
            // Continue with normal login flow
            performLogin(player, name, password);
        } else {
            // No CAPTCHA required, expect 1 argument
            if (args.length != 1) {
                sender.sendMessage(Messages.MESSAGE_LOGIN.asString());
                return;
            }
            
            String password = args[0];
            performLogin(player, name, password);
        }
    }

    private void performLogin(Player player, String name, String password) {
        AccountManagement accountManagement = plugin.getAccountManagement();
        Optional<Account> accountOpt = accountManagement.retrieveOrLoad(name);
        
        if (!accountOpt.isPresent()) {
            player.sendMessage(Messages.NOT_REGISTERED.asString());
            return;
        }

        Account account = accountOpt.get();
        
        if (!accountManagement.comparePassword(account, password)) {
            plugin.getFoliaLib().runAtEntity(player, task ->
                player.kickPlayer(Messages.INCORRECT_PASSWORD.asString())
            );
            return;
        }

        AsyncLoginEvent loginEvent = new AsyncLoginEvent(player);
        if (loginEvent.callEvt()) {
            plugin.getLoginManagement().setAuthenticated(name);

            player.sendMessage(Messages.SUCCESSFUL_LOGIN.asString());
            TitleAPI.getApi().send(player, Messages.TITLE_AFTER_LOGIN.asTitle());

            plugin.getFoliaLib().runAtEntity(player, task -> {
                player.setWalkSpeed(0.2F);
                player.setFlySpeed(0.1F);
                if (Settings.SPAWN_BEFORE_LOGIN_RETURN_LAST_LOCATION.asBoolean()) {
                    Location lastLocation = plugin.popLoginLocation(name);
                    if (lastLocation != null) {
                        player.teleport(lastLocation);
                    }
                }
                player.updateInventory();
                
                // Show player to all other players if invisible feature is enabled
                if (Settings.INVISIBLE_WHILE_UNAUTHENTICATED.asBoolean()) {
                    for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                        if (!onlinePlayer.equals(player)) {
                            onlinePlayer.showPlayer(player);
                            player.showPlayer(onlinePlayer);
                        }
                    }
                }
            });

            new AsyncAuthenticateEvent(player).callEvt();
        }
    }
}
