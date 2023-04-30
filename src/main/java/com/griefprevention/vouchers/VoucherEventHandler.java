package com.griefprevention.vouchers;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

import static org.bukkit.event.Event.Result.DENY;
import static org.bukkit.inventory.EquipmentSlot.HAND;

public class VoucherEventHandler implements Listener
{
    private final GriefPrevention plugin;

    public VoucherEventHandler(GriefPrevention plugin)
    {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemUse(PlayerInteractEvent event)
    {
        if(event.useItemInHand() == DENY || !event.getAction().isRightClick() || event.getHand() != HAND) {
            return;
        }

        if (plugin.getVoucherManager().isVoucher(event.getItem()))
        {
            plugin.getVoucherManager().redeemVoucher(event.getPlayer(), event.getItem());
        }
    }
}
