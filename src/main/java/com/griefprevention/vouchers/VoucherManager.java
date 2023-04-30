package com.griefprevention.vouchers;

import me.ryanhamshire.GriefPrevention.CustomLogEntryTypes;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

public class VoucherManager
{
    private final GriefPrevention plugin;
    private final NamespacedKey voucherKey;

    public VoucherManager(GriefPrevention plugin)
    {
        this.plugin = plugin;
        this.voucherKey = new NamespacedKey(plugin, "voucher");
    }

    public ItemStack createVoucher(VoucherDenomination denomination)
    {
        ItemStack item = new ItemStack(Material.FEATHER, 1);
        ItemMeta meta = item.getItemMeta();

        meta.getPersistentDataContainer().set(voucherKey, PersistentDataType.INTEGER, denomination.getBlockCount());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setCustomModelData(denomination.getCustomModelData());
        meta.displayName(denomination.getDisplayName());
        meta.lore(denomination.getLore());

        item.setItemMeta(meta);

        return item;
    }

    public boolean isVoucher(@Nullable ItemStack item)
    {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(voucherKey);
    }

    public @Nullable VoucherDenomination getVoucherDenomination(ItemStack item)
    {
        if (item == null || !item.hasItemMeta())
        {
            return null;
        }

        ItemMeta meta = item.getItemMeta();

        if (!meta.getPersistentDataContainer().has(voucherKey))
        {
            return null;
        }

        return VoucherDenomination.valueOf(
                meta.getPersistentDataContainer().get(voucherKey, PersistentDataType.INTEGER)
        );
    }

    public void redeemVoucher(Player player, ItemStack voucher)
    {
        VoucherDenomination denomination = getVoucherDenomination(voucher);

        if (denomination == null)
        {
            throw new IllegalArgumentException("Item is not a voucher");
        }

        //add blocks
        PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
        playerData.setBonusClaimBlocks(playerData.getBonusClaimBlocks() + denomination.getBlockCount());
        plugin.dataStore.savePlayerData(player.getUniqueId(), playerData);

        voucher.subtract();

        //inform player
        GriefPrevention.sendMessage(player, TextMode.Success, Messages.RedeemConfirmation,
                String.valueOf(playerData.getRemainingClaimBlocks()));
        GriefPrevention.AddLogEntry("Player " + player.getName() + " redeemed a voucher for " + denomination.getBlockCount() + " claim blocks", CustomLogEntryTypes.Debug);
        playerData.getRemainingClaimBlocks();
    }
}
