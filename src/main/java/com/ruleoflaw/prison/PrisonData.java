package com.ruleoflaw.prison;

import com.ruleoflaw.crime.CrimeType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 世界存档数据：监狱是否已生成、在押犯人、禁言/管制截止时间、前科记录。
 * 保存于存档的 data/ruleoflaw_prison.dat，重启后自动恢复。
 */
public class PrisonData extends SavedData {
    private static final String NAME = "ruleoflaw_prison";

    public boolean prisonGenerated = false;

    /** 在押犯人：UUID -> 服刑信息 */
    public final Map<UUID, Prisoner> prisoners = new HashMap<>();

    /** 禁言（剥夺政治权利）截止时间，单位：游戏 tick */
    public final Map<UUID, Long> mutedUntil = new HashMap<>();

    /** 管制（社区矫正）截止时间，单位：游戏 tick */
    public final Map<UUID, Long> controlUntil = new HashMap<>();

    /** 前科记录：UUID -> 罪名 -> 次数 */
    public final Map<UUID, Map<String, Integer>> offenseCounts = new HashMap<>();

    public static class Prisoner {
        public int cell;
        public long releaseTime;
        public String crime;

        public Prisoner() {
        }

        public Prisoner(int cell, long releaseTime, String crime) {
            this.cell = cell;
            this.releaseTime = releaseTime;
            this.crime = crime;
        }
    }

    public static PrisonData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PrisonData::new, PrisonData::load, null), NAME);
    }

    public static PrisonData load(CompoundTag tag) {
        PrisonData data = new PrisonData();
        data.prisonGenerated = tag.getBoolean("generated");

        ListTag list = tag.getList("prisoners", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            data.prisoners.put(t.getUUID("uuid"),
                    new Prisoner(t.getInt("cell"), t.getLong("release"), t.getString("crime")));
        }

        loadTimeMap(tag.getCompound("muted"), data.mutedUntil);
        loadTimeMap(tag.getCompound("control"), data.controlUntil);

        CompoundTag offenses = tag.getCompound("offenses");
        for (String key : offenses.getAllKeys()) {
            CompoundTag per = offenses.getCompound(key);
            Map<String, Integer> map = new HashMap<>();
            for (String crime : per.getAllKeys()) {
                map.put(crime, per.getInt(crime));
            }
            data.offenseCounts.put(UUID.fromString(key), map);
        }
        return data;
    }

    private static void loadTimeMap(CompoundTag tag, Map<UUID, Long> out) {
        for (String key : tag.getAllKeys()) {
            out.put(UUID.fromString(key), tag.getLong(key));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("generated", prisonGenerated);

        ListTag list = new ListTag();
        for (Map.Entry<UUID, Prisoner> e : prisoners.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("uuid", e.getKey());
            t.putInt("cell", e.getValue().cell);
            t.putLong("release", e.getValue().releaseTime);
            t.putString("crime", e.getValue().crime);
            list.add(t);
        }
        tag.put("prisoners", list);

        tag.put("muted", saveTimeMap(mutedUntil));
        tag.put("control", saveTimeMap(controlUntil));

        CompoundTag offenses = new CompoundTag();
        for (Map.Entry<UUID, Map<String, Integer>> e : offenseCounts.entrySet()) {
            CompoundTag per = new CompoundTag();
            for (Map.Entry<String, Integer> c : e.getValue().entrySet()) {
                per.putInt(c.getKey(), c.getValue());
            }
            offenses.put(e.getKey().toString(), per);
        }
        tag.put("offenses", offenses);
        return tag;
    }

    private CompoundTag saveTimeMap(Map<UUID, Long> map) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<UUID, Long> e : map.entrySet()) {
            tag.putLong(e.getKey().toString(), e.getValue());
        }
        return tag;
    }

    /** 记录一次前科并返回该罪名累计次数 */
    public int addOffense(UUID uuid, CrimeType crime) {
        Map<String, Integer> map = offenseCounts.computeIfAbsent(uuid, k -> new HashMap<>());
        int n = map.getOrDefault(crime.name(), 0) + 1;
        map.put(crime.name(), n);
        setDirty();
        return n;
    }
}
