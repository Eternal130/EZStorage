package com.zerofall.ezstorage.enums;

public enum SortMode {

    AMOUNT(0, "hud.msg.unifiedstorage.sort.mode.amount"),
    NAME(1, "hud.msg.unifiedstorage.sort.mode.name"),
    MOD(2, "hud.msg.unifiedstorage.sort.mode.mod");

    public final int index;
    public final String langKey;

    SortMode(int index, String langKey) {
        this.index = index;
        this.langKey = langKey;
    }

    public static SortMode fromIndex(int index) {
        for (SortMode mode : values()) {
            if (mode.index == index) return mode;
        }
        return AMOUNT;
    }

    public SortMode next() {
        return fromIndex((this.index + 1) % values().length);
    }
}
