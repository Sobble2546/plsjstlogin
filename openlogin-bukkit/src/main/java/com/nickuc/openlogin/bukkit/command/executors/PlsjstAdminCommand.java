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
import com.sobble.pleasejustlogin.bukkit.command.BukkitAbstractCommand;
import com.sobble.pleasejustlogin.common.manager.AccountManagement;
import com.sobble.pleasejustlogin.common.security.hashing.BCrypt;
import com.sobble.pleasejustlogin.common.settings.Messages;
import com.sobble.pleasejustlogin.common.settings.Settings;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

public class PlsjstAdminCommand extends BukkitAbstractCommand {

    public PlsjstAdminCommand(OpenLoginBukkit plugin) {
        super(plugin, "plsjstadmin");
    }

    protected void perform(CommandSender sender, String lb, String[] args) {
        if (!sender.hasPermission("plsjstlogin.admin")) {
            sender.sendMessage(Messages.INSUFFICIENT_PERMISSIONS.asString());
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /" + lb + " <rmpass|migrate|changepass|import> <args>");
            return;
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "rmpass": {
                if (args.length != 2) {
                    sender.sendMessage("§cUsage: /" + lb + " rmpass <player>");
                    return;
                }
                String name = args[1];
                AccountManagement accountManagement = plugin.getAccountManagement();
                boolean removed = accountManagement.delete(name);
                plugin.clearLoginLocation(name);
                Player playerIfOnline = plugin.getServer().getPlayerExact(name);
                if (playerIfOnline != null && Settings.SPAWN_BEFORE_LOGIN_RETURN_LAST_LOCATION.asBoolean()) {
                    plugin.forceLoginLocation(playerIfOnline);
                }
                plugin.getLoginManagement().cleanup(name);
                sender.sendMessage(removed ?
                        "§aPassword removed for §f" + name + "§a." :
                        Messages.NOT_REGISTERED.asString());
                return;
            }
            case "migrate": {
                if (args.length != 3) {
                    sender.sendMessage("§cUsage: /" + lb + " migrate <oldname> <newname>");
                    return;
                }
                String oldName = args[1];
                String newName = args[2];
                AccountManagement accountManagement = plugin.getAccountManagement();
                boolean migrated = accountManagement.migrate(oldName, newName);
                if (migrated) {
                    plugin.getLoginManagement().cleanup(oldName);
                    sender.sendMessage("§aMigrated §f" + oldName + " §ato §f" + newName + "§a.");
                } else {
                    sender.sendMessage("§cFailed to migrate. Check if the old user exists and the new name is free.");
                }
                return;
            }
            case "changepass": {
                if (args.length != 3) {
                    sender.sendMessage("§cUsage: /" + lb + " changepass <player> <newpass>");
                    return;
                }
                String name = args[1];
                String password = args[2];
                int passwordLength = password.length();

                if (passwordLength <= Settings.PASSWORD_SMALL.asInt()) {
                    sender.sendMessage(Messages.PASSWORD_TOO_SMALL.asString());
                    return;
                }

                if (passwordLength >= Settings.PASSWORD_LARGE.asInt()) {
                    sender.sendMessage(Messages.PASSWORD_TOO_LARGE.asString());
                    return;
                }

                AccountManagement accountManagement = plugin.getAccountManagement();
                Optional<?> account = accountManagement.retrieveOrLoad(name);
                if (!account.isPresent()) {
                    sender.sendMessage(Messages.NOT_REGISTERED.asString());
                    return;
                }

                Player playerIfOnline = plugin.getServer().getPlayerExact(name);
                String address = playerIfOnline != null && playerIfOnline.getAddress() != null ?
                        playerIfOnline.getAddress().getAddress().getHostAddress() : null;
                String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
                if (!accountManagement.update(name, hashedPassword, address)) {
                    sender.sendMessage(Messages.DATABASE_ERROR.asString());
                    return;
                }
                accountManagement.invalidateCache(name.toLowerCase());
                sender.sendMessage(Messages.PASSWORD_CHANGED.asString());
                return;
            }
            case "import": {
                if (args.length != 2 || !args[1].equalsIgnoreCase("loginsecurity")) {
                    sender.sendMessage("§cUsage: /" + lb + " import LoginSecurity");
                    return;
                }
                importLoginSecurity(sender);
                return;
            }
            default:
                sender.sendMessage("§cUsage: /" + lb + " <rmpass|migrate|changepass|import> <args>");
        }
    }

    private void importLoginSecurity(CommandSender sender) {
        File pluginsDir = plugin.getDataFolder().getParentFile();
        File dbFile = resolveLoginSecurityDb(pluginsDir);
        if (dbFile == null) {
            sender.sendMessage("§cLoginSecurity.db not found in the plugins folder.");
            return;
        }

        int imported = 0;
        int skipped = 0;
        int unsupported = 0;

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            sender.sendMessage("§cSQLite driver not found.");
            return;
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toString());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT last_name, ip_address, password, last_login, registration_date FROM ls_players")) {

            while (rs.next()) {
                String name = rs.getString("last_name");
                if (name == null || name.trim().isEmpty()) {
                    skipped++;
                    continue;
                }

                String password = rs.getString("password");
                if (password == null || !password.startsWith("$2")) {
                    unsupported++;
                    continue;
                }

                if (plugin.getAccountManagement().search(name).isPresent()) {
                    skipped++;
                    continue;
                }

                String address = rs.getString("ip_address");
                long lastLogin = rs.getLong("last_login");
                long regDate = rs.getLong("registration_date");

                plugin.getDatabase().update(
                        "INSERT INTO `openlogin` (`name`, `realname`, `password`, `address`, `lastlogin`, `regdate`) " +
                                "VALUES (?, ?, ?, ?, ?, ?)",
                        name.toLowerCase(),
                        name,
                        password,
                        address == null ? "127.0.0.1" : address,
                        lastLogin,
                        regDate
                );
                imported++;
            }
        } catch (SQLException e) {
            sender.sendMessage("§cFailed to import LoginSecurity database.");
            e.printStackTrace();
            return;
        }

        sender.sendMessage("§aLoginSecurity import complete.");
        sender.sendMessage(" §7Imported: §f" + imported);
        sender.sendMessage(" §7Skipped: §f" + skipped);
        sender.sendMessage(" §7Unsupported hashes: §f" + unsupported);
    }

    private File resolveLoginSecurityDb(File pluginsDir) {
        File rootDb = new File(pluginsDir, "LoginSecurity.db");
        if (rootDb.exists()) {
            return rootDb;
        }
        File pluginFolder = new File(pluginsDir, "LoginSecurity");
        File nestedDb = new File(pluginFolder, "LoginSecurity.db");
        if (nestedDb.exists()) {
            return nestedDb;
        }
        File databaseDb = new File(pluginFolder, "database.db");
        if (databaseDb.exists()) {
            return databaseDb;
        }
        return null;
    }
}
