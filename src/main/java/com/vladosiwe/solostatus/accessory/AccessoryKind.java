package com.vladosiwe.solostatus.accessory;

/** What a piece of jewellery is: it decides the slot (2 rings, 1 amulet) and which bonuses it can roll. */
public enum AccessoryKind {
    RING("ring", 2),
    AMULET("amulet", 1);

    public final String key;
    /** How many of this kind count at once, whatever the slots (System or Curios). */
    public final int limit;

    AccessoryKind(String key, int limit) {
        this.key = key;
        this.limit = limit;
    }
}
