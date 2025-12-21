package com.griefprevention.protection;

import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.TextMode;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Event handler for special interaction protection cases.
 */
public class InteractionProtectionHandler implements Listener
{

    /**
     * Special case to handle End portal frame interactions before the portal is created,
     * ensuring build permission checks happen prior to vanilla portal creation logic.
     *
     * @param event the player interaction event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEndPortalFrameInteract(@NotNull EntityChangeBlockEvent event)
    {
        if (!(event.getEntity() instanceof Player player)) return;

        // Use instanceof check instead of direct cast to safely handle potential modded block data implementations
        if (!(event.getBlock().getBlockData() instanceof EndPortalFrame frameData)) return;
        if (frameData.hasEye()) return;

        if (!(event.getBlockData() instanceof EndPortalFrame newFrameData)) return;
        if (!newFrameData.hasEye()) return;

        Supplier<String> noBuildReason = ProtectionHelper.checkPermission(player, event.getBlock().getLocation(), ClaimPermission.Build, event);
        if (noBuildReason != null)
        {
            event.setCancelled(true);
            GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason.get());
        }
    }
}
