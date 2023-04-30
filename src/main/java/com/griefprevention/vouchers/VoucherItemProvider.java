package com.griefprevention.vouchers;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import uk.co.notnull.CustomItems.api.items.CustomItem;
import uk.co.notnull.CustomItems.api.items.CustomItemProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VoucherItemProvider extends CustomItemProvider
{
    private final VoucherManager manager;
    private Map<VoucherDenomination, VoucherCustomItem> items = new HashMap<>();

    public VoucherItemProvider(VoucherManager manager)
    {
        this.manager = manager;

        for (VoucherDenomination denomination : VoucherDenomination.values())
        {
            items.put(denomination, new VoucherCustomItem(denomination));
        }
    }

    @Override
    public List<CustomItem> provideItems()
    {
        return new ArrayList<>(items.values());
    }

    @Nullable
    @Override
    public CustomItem identifyItem(ItemStack itemStack)
    {
        return items.get(manager.getVoucherDenomination(itemStack));
    }
}
