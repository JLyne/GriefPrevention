package com.griefprevention.vouchers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum VoucherDenomination
{
    ONE(1, 2000),
    FIFTY(50, 2001),
    ONE_HUNDRED(100, 2002),
    ONE_THOUSAND(1000, 2003);

    private final int blockCount;
    private final int customModelData;

    private static final Map<Integer, VoucherDenomination> map = new HashMap<>();
    private final TranslatableComponent displayName;
    private final List<TranslatableComponent> lore;

    VoucherDenomination(int blockCount, int customModelData)
    {
        this.blockCount = blockCount;
        this.customModelData = customModelData;
        this.displayName = Component.translatable(
                        String.format("item.claimblockvoucher.%s.name", blockCount),
                        String.format("Claim Block Voucher - %d blocks", blockCount))
                .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false);
        this.lore = List.of(
                Component.translatable("item.claimblockvoucher." + blockCount + ".lore.line1")
                .decoration(TextDecoration.ITALIC, false),
                Component.translatable("item.claimblockvoucher." + blockCount + ".lore.line2")
                .decoration(TextDecoration.ITALIC, false),
                Component.translatable("item.claimblockvoucher." + blockCount + ".lore.line3")
                .decoration(TextDecoration.ITALIC, false));
    }

    static
    {
        for (VoucherDenomination tier : VoucherDenomination.values())
        {
            map.put(tier.blockCount, tier);
        }
    }

    public static @Nullable VoucherDenomination valueOf(int tier)
    {
        return map.get(tier);
    }

    public int getBlockCount()
    {
        return blockCount;
    }

    public int getCustomModelData()
    {
        return customModelData;
    }

    public TranslatableComponent getDisplayName()
    {
        return displayName;
    }

    public List<TranslatableComponent> getLore()
    {
        return lore;
    }
}
