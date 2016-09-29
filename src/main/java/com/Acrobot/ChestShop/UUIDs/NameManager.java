package com.Acrobot.ChestShop.UUIDs;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

import com.Acrobot.ChestShop.ChestShop;
import com.Acrobot.ChestShop.Configuration.Properties;
import com.Acrobot.ChestShop.Database.Account;
import com.Acrobot.ChestShop.Database.Account2;
import com.Acrobot.ChestShop.Database.DaoCreator;
import com.Acrobot.ChestShop.Database.PlayerName;
import com.Acrobot.ChestShop.Signs.ChestShopSign;
import com.j256.ormlite.dao.Dao;

/**
 * Lets you save/cache username and UUID relations
 *
 * @author Andrzej Pomirski (Acrobot)
 */

public class NameManager {
    private static Dao<Account, String> accounts;
    private static Dao<Account2, String> accounts2;
    private static Dao<PlayerName, String> playerNames;

    private static Map<String, UUID> usedShortNames = new HashMap<String, UUID>();
    private static Map<UUID, String> currentShortName = new HashMap<UUID, String>();
    private static Map<UUID, String> lastSeenFullName = new HashMap<UUID, String>();

    private static UUID adminShopUUID;
    private static UUID serverAccountUUID;

    public static String getNameFor(Player player) {
        return currentShortName.get(player.getUniqueId());
    }

    public static String getLastSeenName(UUID playerId) {
        if (isAdminShop(playerId)) {
            return Properties.ADMIN_SHOP_NAME;
        }
        if (isServerAccount(playerId)) {
            return Properties.SERVER_ECONOMY_ACCOUNT;
        }
        return lastSeenFullName.get(playerId);
    }

    public static String getUsername(UUID playerId) {
        return getLastSeenName(playerId);
    }

    public static String getFullUsername(String shortName) {
        return getLastSeenName(getUUID(shortName));
    }

    public static UUID getUUID(String shortName) {
        if (Properties.ADMIN_SHOP_NAME.equalsIgnoreCase(shortName)) {
            return adminShopUUID;
        }
        if (Properties.SERVER_ECONOMY_ACCOUNT != null && Properties.SERVER_ECONOMY_ACCOUNT.length() > 0 && Properties.SERVER_ECONOMY_ACCOUNT.equals(shortName)) {
            return serverAccountUUID;
        }
        return usedShortNames.get(shortName.toLowerCase());
    }

    private static String createUseableShortName(String name, int id) {
        if (id == 0) {
            return name.length() > 15 ? name.substring(0, 15) : name;
        }
        String idString = Integer.toString(id);
        int maxLength = 15 - idString.length();
        return (name.length() > maxLength ? name.substring(0, maxLength) : name) + idString;
    }

    public static void storeUsername(final Player player) {
        final UUID uuid = player.getUniqueId();
        String name = player.getName();
        String foundShortName = storeUsername(uuid, name);
        currentShortName.put(uuid, foundShortName);
    }

    private static String storeUsername(final UUID uuid, String name) {
        int id = 0;
        String foundShortName = null;
        while (foundShortName == null) {
            String shortName = createUseableShortName(name, id++);
            UUID inUse = usedShortNames.get(shortName.toLowerCase());
            if (inUse == null) {
                usedShortNames.put(shortName.toLowerCase(), uuid);
                foundShortName = shortName;
                try {
                    accounts2.create(new Account2(foundShortName, uuid));
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            } else if (inUse.equals(uuid)) {
                foundShortName = shortName;
            }
        }
        String storedFullName = lastSeenFullName.put(uuid, name);
        if (storedFullName == null || !storedFullName.equals(name)) {
            try {
                playerNames.createOrUpdate(new PlayerName(name, uuid));
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return foundShortName;
    }

    public static boolean canUseName(Player player, String name) {
        if (ChestShopSign.isAdminShop(name)) {
            return false;
        }
        UUID inUse = usedShortNames.get(name.toLowerCase());
        return inUse != null && inUse.equals(player.getUniqueId());
    }

    public static boolean isAdminShop(UUID uuid) {
        return adminShopUUID.equals(uuid);
    }

    public static UUID getAdminShopUUID() {
        return adminShopUUID;
    }

    public static boolean isServerAccount(UUID uuid) {
        return serverAccountUUID.equals(uuid);
    }

    public static UUID getServerAccountUUID() {
        return serverAccountUUID;
    }

    public static void load() {
        adminShopUUID = UUID.nameUUIDFromBytes(("ChestShop-Adminshop").getBytes());
        serverAccountUUID = UUID.nameUUIDFromBytes(("ChestShop-ServerAccount").getBytes());
        try {
            accounts = DaoCreator.getDaoAndCreateTable(Account.class);

            // Account adminAccount = new Account(Properties.ADMIN_SHOP_NAME, Bukkit.getOfflinePlayer(Properties.ADMIN_SHOP_NAME).getUniqueId());
            // accounts.createOrUpdate(adminAccount);

            accounts2 = DaoCreator.getDaoAndCreateTable(Account2.class);

            playerNames = DaoCreator.getDaoAndCreateTable(PlayerName.class);

            for (PlayerName pn : playerNames.queryForAll()) {
                lastSeenFullName.put(pn.getUuid(), pn.getFullName());
            }
            for (Account2 a : accounts2.queryForAll()) {
                UUID id = a.getUuid();
                String name = a.getShortName();
                usedShortNames.put(name.toLowerCase(), id);
            }

            // import old data
            for (Account a : accounts.queryForAll()) {
                UUID id = a.getUuid();
                String name = a.getName();
                String name2 = a.getLastSeenName();
                if (name != null && !name.equalsIgnoreCase(Properties.ADMIN_SHOP_NAME)) {
                    ChestShop.getBukkitLogger().info("Importing " + name + " (" + id + ")...");
                    storeUsername(id, name);
                }
                if (name2 != null && !name2.equals(name) && !name2.equalsIgnoreCase(Properties.ADMIN_SHOP_NAME)) {
                    ChestShop.getBukkitLogger().info("Importing " + name2 + " (" + id + ")...");
                    storeUsername(id, name2);
                }
            }
            accounts.deleteBuilder().delete();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
