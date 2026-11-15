package com.vladosiwe.solostatus.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The character's history: what was reached and when (the world day). Kept in the profile, newest last, at most
 * {@link #LIMIT} entries (the oldest go first). Shown in the System window; texts: solostatus.history.&lt;type&gt;.
 */
public final class History {
    public static final int LIMIT = 300;

    /** level (n), class (arg), spec (arg), advanced, skill (arg, n = rank), awaken (arg), achievement (arg),
     *  mastery (arg = weapon or magic:school, n = milestone), rank (arg = letter), boss (arg = entity id),
     *  mod (arg = skill, n = choice), limit (n = limit breaks). */
    public record Entry(String type, String arg, int n, long day) {
        CompoundTag write() {
            CompoundTag t = new CompoundTag();
            t.putString("t", type);
            if (!arg.isEmpty()) t.putString("a", arg);
            if (n != 0) t.putInt("n", n);
            t.putLong("d", day);
            return t;
        }

        static Entry read(CompoundTag t) {
            return new Entry(t.getString("t"), t.getString("a"), t.getInt("n"), t.getLong("d"));
        }
    }

    private History() {}

    /** The line shown for an entry (both sides: only translation keys and names). */
    public static net.minecraft.network.chat.Component describe(Entry e) {
        String key = "solostatus.history." + e.type();
        return switch (e.type()) {
            case "level", "limit" -> net.minecraft.network.chat.Component.translatable(key, e.n());
            case "class" -> net.minecraft.network.chat.Component.translatable(key, java.util.Arrays.stream(com.vladosiwe.solostatus.clazz.HunterClass.VALUES)
                    .filter(c -> c.key.equals(e.arg())).findFirst().map(com.vladosiwe.solostatus.clazz.HunterClass::displayName)
                    .orElse(net.minecraft.network.chat.Component.literal(e.arg())));
            case "spec" -> net.minecraft.network.chat.Component.translatable(key, java.util.Arrays.stream(com.vladosiwe.solostatus.clazz.Specialization.VALUES)
                    .filter(s -> s.key.equals(e.arg())).findFirst().map(com.vladosiwe.solostatus.clazz.Specialization::displayName)
                    .orElse(net.minecraft.network.chat.Component.literal(e.arg())));
            case "skill" -> e.n() <= 1
                    ? net.minecraft.network.chat.Component.translatable(key, skillName(e.arg()))
                    : net.minecraft.network.chat.Component.translatable(key + ".rank", skillName(e.arg()), e.n());
            case "awaken" -> net.minecraft.network.chat.Component.translatable(key, skillName(e.arg()));
            case "achievement" -> net.minecraft.network.chat.Component.translatable(key, net.minecraft.network.chat.Component.translatable("solostatus.achievement." + e.arg()));
            case "mastery" -> net.minecraft.network.chat.Component.translatable(key, e.arg().startsWith("magic:")
                    ? com.vladosiwe.solostatus.mastery.MasterySystem.schoolName(e.arg().substring(6))
                    : com.vladosiwe.solostatus.combat.WeaponType.byKey(e.arg()).displayName(), e.n());
            case "rank" -> net.minecraft.network.chat.Component.translatable(key, e.arg());
            case "boss" -> {
                var id = net.minecraft.resources.ResourceLocation.tryParse(e.arg());
                yield net.minecraft.network.chat.Component.translatable(key, id == null ? net.minecraft.network.chat.Component.literal(e.arg())
                        : net.minecraft.network.chat.Component.translatable(net.minecraft.Util.makeDescriptionId("entity", id)));
            }
            case "legendary" -> net.minecraft.network.chat.Component.translatable(key, net.minecraft.network.chat.Component.translatable("item.solostatus." + e.arg()));
            case "mod" -> net.minecraft.network.chat.Component.translatable(key,
                    net.minecraft.network.chat.Component.translatable("solostatus.mod." + e.arg() + "." + e.n()), skillName(e.arg()));
            default -> net.minecraft.network.chat.Component.translatable(key);
        };
    }

    private static net.minecraft.network.chat.Component skillName(String id) {
        return net.minecraft.network.chat.Component.translatable("solostatus.skill." + id);
    }

    public static void add(ServerPlayer player, PlayerData d, String type, String arg, int n) {
        add(d, type, arg, n, player.serverLevel().getDayTime() / 24000L + 1);
    }

    public static void add(PlayerData d, String type, String arg, int n, long day) {
        d.history.add(new Entry(type, arg == null ? "" : arg, n, day));
        while (d.history.size() > LIMIT) d.history.remove(0);
        d.historyChanged = true;
        d.markDirty();
    }

    static ListTag write(List<Entry> history) {
        ListTag list = new ListTag();
        for (Entry e : history) list.add(e.write());
        return list;
    }

    static void read(ListTag list, List<Entry> into) {
        into.clear();
        for (int i = 0; i < list.size() && i < LIMIT; i++) into.add(Entry.read(list.getCompound(i)));
    }

    static ListTag readList(CompoundTag t) {
        return t.getList("history", Tag.TAG_COMPOUND);
    }
}
