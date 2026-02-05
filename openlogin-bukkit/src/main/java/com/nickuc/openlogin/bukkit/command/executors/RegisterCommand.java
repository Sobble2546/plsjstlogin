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
import com.sobble.pleasejustlogin.bukkit.api.events.AsyncRegisterEvent;
import com.sobble.pleasejustlogin.bukkit.captcha.CaptchaManager;
import com.sobble.pleasejustlogin.bukkit.command.BukkitAbstractCommand;
import com.sobble.pleasejustlogin.bukkit.ui.title.TitleAPI;
import com.sobble.pleasejustlogin.common.manager.AccountManagement;
import com.sobble.pleasejustlogin.common.manager.LoginManagement;
import com.sobble.pleasejustlogin.common.security.hashing.BCrypt;
import com.sobble.pleasejustlogin.common.settings.Messages;
import com.sobble.pleasejustlogin.common.settings.Settings;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;

import java.util.Objects;

public class RegisterCommand extends BukkitAbstractCommand {

    public RegisterCommand(OpenLoginBukkit plugin) {
        super(plugin, "register");
    }

    protected void perform(CommandSender sender, String lb, String[] args) {
        if (sender instanceof Player) {
            performPlayer((Player) sender, lb, args);
        } else {
            performConsole(sender, lb, args);
        }
    }

    private void performPlayer(Player sender, String lb, String[] args) {
        String name = sender.getName();
        LoginManagement loginManagement = plugin.getLoginManagement();
        if (loginManagement.isAuthenticated(name)) {
            sender.sendMessage(Messages.ALREADY_LOGIN.asString());
            return;
        }

        // Check if CAPTCHA is enabled for registration
        boolean captchaEnabled = Settings.CAPTCHA_ENABLED.asBoolean()
                               && Settings.CAPTCHA_USE_ON_REGISTER.asBoolean();
        
        CaptchaManager captchaManager = plugin.getCaptchaManager();
        
        if (captchaEnabled) {
            // Expect 3 arguments: <password> <password> <captcha>
            if (args.length != 3) {
                sender.sendMessage(Messages.MESSAGE_REGISTER_CAPTCHA.asString());
                return;
            }
            
            String password = args[0];
            String passwordConfirm = args[1];
            String captchaInput = args[2];
            
            // Validate CAPTCHA first
            if (!captchaManager.validateCaptcha(name, captchaInput)) {
                captchaManager.removeCaptcha(name);
                plugin.getFoliaLib().runAtEntity(sender, task ->
                    sender.kickPlayer(Messages.CAPTCHA_INCORRECT.asString())
                );
                return;
            }
            
            // CAPTCHA valid, proceed with registration (will remove CAPTCHA on success)
            performRegistration(sender, name, password, passwordConfirm, true);
        } else {
            // No CAPTCHA required, expect 2 arguments
            if (args.length != 2) {
                sender.sendMessage(Messages.MESSAGE_REGISTER.asString());
                return;
            }
            
            String password = args[0];
            String passwordConfirm = args[1];
            performRegistration(sender, name, password, passwordConfirm, false);
        }
    }

    private void performRegistration(Player sender, String name, String password, String passwordConfirm, boolean hasCaptcha) {
        int passwordLength = password.length();

        if (passwordLength <= Settings.PASSWORD_SMALL.asInt()) {
            sender.sendMessage(Messages.PASSWORD_TOO_SMALL.asString());
            return;
        }

        if (passwordLength >= Settings.PASSWORD_LARGE.asInt()) {
            sender.sendMessage(Messages.PASSWORD_TOO_LARGE.asString());
            return;
        }

        if (!password.equals(passwordConfirm)) {
            sender.sendMessage(Messages.PASSWORDS_DONT_MATCH.asString());
            return;
        }

        AccountManagement accountManagement = plugin.getAccountManagement();
        boolean exists = accountManagement.retrieveOrLoad(name).isPresent();
        if (exists) {
            sender.sendMessage(Messages.ALREADY_REGISTERED.asString());
            return;
        }

        String salt = BCrypt.gensalt();
        String hashedPassword = BCrypt.hashpw(password, salt);
        String address = sender.getAddress().getAddress().getHostAddress();
        if (!accountManagement.update(name, hashedPassword, address, false)) {
            sender.sendMessage(Messages.DATABASE_ERROR.asString());
            return;
        }

        AsyncRegisterEvent registerEvent = new AsyncRegisterEvent(sender);
        if (registerEvent.callEvt()) {
            plugin.getLoginManagement().setAuthenticated(name);
            
            // Remove CAPTCHA only after successful registration
            if (hasCaptcha) {
                plugin.getCaptchaManager().removeCaptcha(name);
            }

            TitleAPI.getApi().send(sender, Messages.TITLE_AFTER_REGISTER.asTitle());
            sender.sendMessage(Messages.SUCCESSFUL_REGISTER.asString());

            plugin.getFoliaLib().runAtEntity(sender, task -> {
                sender.setWalkSpeed(0.2F);
                sender.setFlySpeed(0.1F);
                if (Settings.SPAWN_BEFORE_LOGIN_RETURN_LAST_LOCATION.asBoolean()) {
                    Location lastLocation = plugin.popLoginLocation(name);
                    if (lastLocation != null) {
                        sender.teleport(lastLocation);
                    }
                }
                sender.updateInventory();
                
                // Show player to all other players if invisible feature is enabled
                if (Settings.INVISIBLE_WHILE_UNAUTHENTICATED.asBoolean()) {
                    for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                        if (!onlinePlayer.equals(sender)) {
                            // Always show the newly registered player to others
                            onlinePlayer.showPlayer(sender);
                            // Only show other players to the newly registered player if they are authenticated
                            if (plugin.getLoginManagement().isAuthenticated(onlinePlayer.getName())) {
                                sender.showPlayer(onlinePlayer);
                            }
                        }
                    }
                }
            });

            new AsyncAuthenticateEvent(sender).callEvt();
        }
    }

    private void performConsole(CommandSender sender, String lb, String[] args) {
        if (!sender.hasPermission("plsjstlogin.admin")) {
            sender.sendMessage(Messages.INSUFFICIENT_PERMISSIONS.asString());
            return;
        }

        if (args.length != 2) {
            sender.sendMessage("§cUsage: /" + lb + " <player> <password>");
            return;
        }

        String playerName = args[0];
        String password = args[1];
        int passwordLength = password.length();

        if (passwordLength <= Settings.PASSWORD_SMALL.asInt()) {
            sender.sendMessage(Messages.PASSWORD_TOO_SMALL.asString());
            return;
        }

        if (passwordLength >= Settings.PASSWORD_LARGE.asInt()) {
            sender.sendMessage(Messages.PASSWORD_TOO_LARGE.asString());
            return;
        }

        Player playerIfOnline = plugin.getServer().getPlayerExact(playerName);
        if (playerIfOnline != null) {
            playerName = playerIfOnline.getName();
        }

        AccountManagement accountManagement = plugin.getAccountManagement();
        boolean exists = accountManagement.retrieveOrLoad(playerName).isPresent();
        if (exists) {
            sender.sendMessage(Messages.ALREADY_REGISTERED.asString());
            return;
        }

        String salt = BCrypt.gensalt();
        String hashedPassword = BCrypt.hashpw(password, salt);
        String address = playerIfOnline != null ?
                Objects.requireNonNull(playerIfOnline.getAddress()).getAddress().getHostAddress() : null;
        if (!accountManagement.update(playerName, hashedPassword, address, false)) {
            sender.sendMessage(Messages.DATABASE_ERROR.asString());
            return;
        }

        sender.sendMessage(Messages.SUCCESSFUL_REGISTER.asString());

        if (playerIfOnline != null) {
            AsyncRegisterEvent registerEvent = new AsyncRegisterEvent(playerIfOnline);
            if (registerEvent.callEvt()) {
                plugin.getLoginManagement().setAuthenticated(playerName);

                TitleAPI.getApi().send(playerIfOnline, Messages.TITLE_AFTER_REGISTER.asTitle());
                playerIfOnline.sendMessage(Messages.SUCCESSFUL_REGISTER.asString());

                plugin.getFoliaLib().runAtEntity(playerIfOnline, task -> {
                    playerIfOnline.setWalkSpeed(0.2F);
                    playerIfOnline.setFlySpeed(0.1F);
                    if (Settings.SPAWN_BEFORE_LOGIN_RETURN_LAST_LOCATION.asBoolean()) {
                        Location lastLocation = plugin.popLoginLocation(playerIfOnline.getName());
                        if (lastLocation != null) {
                            playerIfOnline.teleport(lastLocation);
                        }
                    }
                    playerIfOnline.updateInventory();
                    
                    // Show player to all other players if invisible feature is enabled
                    if (Settings.INVISIBLE_WHILE_UNAUTHENTICATED.asBoolean()) {
                        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                            if (!onlinePlayer.equals(playerIfOnline)) {
                                // Only show the newly registered player to others (don't reveal others to them)
                                onlinePlayer.showPlayer(playerIfOnline);
                            }
                        }
                    }
                });

                new AsyncAuthenticateEvent(playerIfOnline).callEvt();
            }
        }
    }
}
