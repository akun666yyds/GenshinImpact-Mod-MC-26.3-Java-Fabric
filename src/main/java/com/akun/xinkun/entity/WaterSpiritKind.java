package com.akun.xinkun.entity;

public enum WaterSpiritKind {
    SEAHORSE("seahorse"),
    CRAB("crab"),
    OCTOPUS("octopus");

    private final String id;

    WaterSpiritKind(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public String translationKey() {
        return "entity.xinkun_genshin.water_spirit_" + this.id;
    }

    public static WaterSpiritKind byId(String id) {
        for (WaterSpiritKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return SEAHORSE;
    }
}
