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
        /** 原判释放时刻（减刑下限 = 原判刑期的一半） */
        public long originalRelease;
        /** 原判刑期（游戏日） */
        public int originalDays;
        /** 连续良好服刑累计 tick（每满 1 个游戏日减刑 1 天） */
        public long goodTicks;
        /** 越狱次数（>=2 取消减刑资格） */
        public int escapes;

        public Prisoner() {
        }

        public Prisoner(int cell, long releaseTime, String crime) {
            this.cell = cell;
            this.releaseTime = releaseTime;
            this.crime = crime;
            this.originalRelease = releaseTime;
            this.originalDays = 0;
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
            Prisoner p = new Prisoner(t.getInt("cell"), t.getLong("release"), t.getString("crime"));
            // 旧存档兼容：无原判记录则视为不可减刑（下限 = 当前释放时刻）
            p.originalRelease = t.contains("origRel") ? t.getLong("origRel") : p.releaseTime;
            p.originalDays = t.getInt("origDays");
            p.goodTicks = t.getLong("goodTicks");
            p.escapes = t.getInt("escapes");
            data.prisoners.put(t.getUUID("uuid"), p);
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
            t.putLong("origRel", e.getValue().originalRelease);
            t.putInt("origDays", e.getValue().originalDays);
            t.putLong("goodTicks", e.getValue().goodTicks);
            t.putInt("escapes", e.getValue().escapes);
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