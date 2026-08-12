package com.ruleoflaw.court;

import com.ruleoflaw.RuleOfLawMod;
import com.ruleoflaw.crime.CrimeType;
import com.ruleoflaw.prison.PrisonData;
import com.ruleoflaw.prison.PrisonManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * 法院系统：立案 -> 逮捕 -> 公诉 -> 判决 -> 执行。
 * 流程设计致敬《中华人民共和国宪法》第三十七条：
 * 任何公民，非经人民检察院批准或者决定或者人民法院决定，并由公安机关执行，不受逮捕。
 */
public final class CourtSystem {

    /** 对玩家提起公诉并立即开庭审理 */
    public static void prosecute(ServerPlayer player, CrimeType crime, String detail) {
        MinecraftServer server = player.server;
        ServerLevel level = server.overworld();
        PrisonData data = PrisonData.get(level);
        int count = data.addOffense(player.getUUID(), crime);
        String name = player.getGameProfile().getName();

        broadcast(server, "§8§m                                                ");
        broadcast(server, "§b【公安机关】§f玩家 §e" + name + " §f因涉嫌§c「" + crime.displayName + "」§f被当场抓获"
                + (detail == null || detail.isEmpty() ? "。" : "（" + detail + "）。"));
        broadcast(server, "§b【人民检察院】§f依据§6《中华人民共和国宪法》第三十七条§f批准逮捕，并向人民法院提起公诉。");
        String sentence = decideAndApply(player, crime, count, data);
        broadcast(server, "§b【Minecraft人民法院】§6§l判决：§f被告人 §e" + name + " §f犯§c"
                + crime.displayName + "§f，事实清楚，证据确实、充分。");
        broadcast(server, "§f依照§6" + crime.article + "§f之规定，判决如下：§e§l" + sentence);
        if (count > 1) {
            broadcast(server, "§7（系第 " + count + " 次触犯本罪，依法从重处罚）");
        }
        broadcast(server, "§7刑期按游戏时间计算（1 游戏日 = 现实 20 分钟），最长不超过 10 个游戏日。输入 /law 查看法律信息。");
        broadcast(server, "§8§m                                                ");
    }

    /** 量刑并立即执行，返回判决书上的刑罚描述 */
    private static String decideAndApply(ServerPlayer player, CrimeType crime, int count, PrisonData data) {
        RandomSource rand = player.getRandom();
        switch (crime) {
            case MURDER_PLAYER: {
                // 累犯（第 3 次故意杀人）依法判处死刑
                if (count >= 3) {
                    deathPenalty(player);
                    return "死刑（立即执行），剥夺政治权利终身";
                }
                int days = range(rand, crime.minDays, crime.maxDays);
                PrisonManager.imprison(player, days, crime.displayName);
                mute(player, days, data);
                fine(player, 6);
                return "有期徒刑 " + days + " 天，附加剥夺政治权利 " + days + " 天（游戏内禁言），并处罚金（扣除 6 点生命值）";
            }
            case MURDER_VILLAGER: {
                int days = range(rand, crime.minDays, crime.maxDays);
                if (count >= 4) days = crime.maxDays;
                PrisonManager.imprison(player, days, crime.displayName);
                return "有期徒刑 " + days + " 天";
            }
            case ASSAULT: {
                if (count == 1) {
                    fine(player, 4);
                    return "警告，并处罚金（扣除 4 点生命值）";
                }
                if (count == 2) {
                    control(player, 2, data);
                    return "管制 2 天（社区矫正：缓慢与虚弱）";
                }
                int days = Math.min(3, count);
                PrisonManager.imprison(player, days, crime.displayName);
                return "有期徒刑 " + days + " 天";
            }
            case ENDANGERED_ANIMAL: {
                int days = range(rand, crime.minDays, crime.maxDays);
                if (count >= 3) days = crime.maxDays;
                PrisonManager.imprison(player, days, crime.displayName);
                fine(player, 6);
                return "有期徒刑 " + days + " 天，并处罚金（扣除 6 点生命值）";
            }
            case ILLEGAL_HUNTING: {
                if (count == 1) {
                    fine(player, 2);
                    control(player, 1, data);
                    return "罚金（扣除 2 点生命值），并处管制 1 天";
                }
                PrisonManager.imprison(player, 1, crime.displayName);
                return "拘役 1 天";
            }
            case ARSON: {
                int days = range(rand, crime.minDays, crime.maxDays);
                if (count >= 3) days = crime.maxDays;
                PrisonManager.imprison(player, days, crime.displayName);
                return "有期徒刑 " + days + " 天";
            }
            case EXPLOSION: {
                // 累犯（第 3 次爆炸）依法判处死刑
                if (count >= 3) {
                    deathPenalty(player);
                    return "死刑（立即执行）";
                }
                int days = range(rand, crime.minDays, crime.maxDays);
                PrisonManager.imprison(player, days, crime.displayName);
                return "有期徒刑 " + days + " 天";
            }
            case THEFT: {
                if (count == 1) return "警告一次，责令改正";
                if (count == 2) {
                    fine(player, 4);
                    return "罚金（扣除 4 点生命值）";
                }
                int days = range(rand, 1, 2);
                PrisonManager.imprison(player, days, crime.displayName);
                return "有期徒刑 " + days + " 天";
            }
            case VANDALISM: {
                if (count == 1) return "警告一次，责令赔偿损失（请自觉补回方块）";
                if (count == 2) {
                    fine(player, 4);
                    return "罚金（扣除 4 点生命值）";
                }
                int days = range(rand, 1, 2);
                PrisonManager.imprison(player, days, crime.displayName);
                return "有期徒刑 " + days + " 天";
            }
            case PICKING_QUARRELS: {
                if (count == 1) {
                    mute(player, 1, data);
                    return "禁言 1 天";
                }
                mute(player, 2, data);
                control(player, 1, data);
                return "禁言 2 天，并处管制 1 天";
            }
            default:
                return "警告";
        }
    }

    private static int range(RandomSource rand, int min, int max) {
        return min + rand.nextInt(max - min + 1);
    }

    /** 罚金：扣除生命值（点数） */
    private static void fine(ServerPlayer player, int hp) {
        player.hurt(player.damageSources().generic(), hp);
    }

    /**
     * 死刑（刑法中的死刑立即执行）：
     * 先清除监狱记录、把重生点重置回世界出生点，再直接击杀玩家。
     * 玩家复活后会在出生点，不会被困在监狱里。
     */
    private static void deathPenalty(ServerPlayer player) {
        ServerLevel level = player.server.overworld();
        PrisonData data = PrisonData.get(level);

        // 若恰好在押，先解除服刑记录
        if (data.prisoners.containsKey(player.getUUID())) {
            data.prisoners.remove(player.getUUID());
            data.setDirty();
        }

        // 重生点重置为世界出生点
        BlockPos spawn = level.getSharedSpawnPos();
        player.setRespawnPosition(level.dimension(), spawn, 0f, true, false);

        broadcast(player.server, "§4§l【Minecraft人民法院】对 §e" + player.getGameProfile().getName()
                + " §4§l执行死刑——立即执行！");
        player.kill();
    }

    /** 剥夺政治权利（游戏内表现为禁言） */
    private static void mute(ServerPlayer player, int days, PrisonData data) {
        long until = player.level().getGameTime() + days * RuleOfLawMod.TICKS_PER_DAY;
        data.mutedUntil.merge(player.getUUID(), until, Math::max);
        data.setDirty();
    }

    /** 管制（社区矫正：缓慢 + 虚弱） */
    private static void control(ServerPlayer player, int days, PrisonData data) {
        long until = player.level().getGameTime() + days * RuleOfLawMod.TICKS_PER_DAY;
        data.controlUntil.merge(player.getUUID(), until, Math::max);
        data.setDirty();
    }

    public static void broadcast(MinecraftServer server, String msg) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
    }
}