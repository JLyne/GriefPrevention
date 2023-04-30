package com.griefprevention.vouchers;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import uk.co.notnull.CustomItems.api.items.AbstractCustomItem;
import uk.co.notnull.CustomItems.api.items.CreationContext;


public class VoucherCustomItem extends AbstractCustomItem
{
    private final VoucherDenomination denomination;

    public VoucherCustomItem(VoucherDenomination denomination)
    {
        super(new NamespacedKey(GriefPrevention.instance, "voucher_" + denomination.getBlockCount()),
                denomination.getDisplayName(),false, false);
        this.denomination = denomination;
    }

    @Override
    public ItemStack createItem(CreationContext creationContext, int i)
    {
        return GriefPrevention.instance.getVoucherManager().createVoucher(denomination);
    }
}
