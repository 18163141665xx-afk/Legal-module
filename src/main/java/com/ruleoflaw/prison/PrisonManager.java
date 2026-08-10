package com.ruleoflaw.prison;

import com.ruleoflaw.RuleOfLawMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 基岩监狱：结构生成、收监、释放、防越狱巡逻、管制执行。
 * 监狱在模组首次加载存档时自动生成于 (1000, 180, 1000) 的高空，
 * 整体由基岩构成，共 8 间独立牢房（铁栅栏朝走廊），并强制加载所在区块。
 */
public final class PrisonManager {
    /** 监狱西南角基准坐标 */
    public static final BlockPos ORIGIN = new BlockPos(1000, 180, 1000);

    public static final int CELLS = 8;

    private static final int WIDTH_X = CELLS * 5 + 1; // 41
    private static final int DEPTH_Z = 7;
    private static final int HEIGHT = 7;

    /**
     * 每秒（20 tick）由 BehaviorMonitor 调用一次：
     * 1) 刑满释放；2) 防越狱巡逻；3) 管制效果维持；4) 清理过期禁言。
     */
    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        PrisonData data = PrisonData.get(level);
        long now = level.getGameTime();

        // 1) 刑满释放
        List<UUID> toRelease = new ArrayList<>();
        for (Map.Entry<UUID, PrisonData.Prisoner> e : data.prisoners.entrySet()) {
            if (now >= e.getValue().releaseTime) {
                toRelease.add(e.getKey());
            }
        }
        for (UUID uuid : toRelease) {
            release(level, uuid, false);
        }

        // 2) 防越狱：在押人员不在自己牢房范围内 -> 押回
        for (Map.Entry<UUID, PrisonData.Prisoner> e : data.prisoners.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) continue;
            boolean escaped = p.level().dimension() != Level.OVERWORLD
                    || !cellBounds(e.getValue().cell).contains(p.position());
            if (escaped) {
                BlockPos pos = cellSpawn(e.getValue().cell);
                p.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
                double remainDays = (e.getValue().releaseTime - now) / (double) RuleOfLawMod.TICKS_PER_DAY;
                p.sendSystemMessage(Component.literal("§c【狱警】越狱未遂！你已被押回 "
                        + (e.getValue().cell + 1) + " 号牢房。剩余刑期约 "
                        + String.format("%.1f", Math.max(0, remainDays)) + " 天。"));
            }
        }

        // 3) 管制（社区矫正）：缓慢 II + 虚弱 I
        List<UUID> controlExpired = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : data.controlUntil.entrySet()) {
            if (now >= e.getValue()) {
                controlExpired.add(e.getKey());
                continue;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, false, false));
            }
        }
        for (UUID uuid : controlExpired) {
            data.controlUntil.remove(uuid);
            data.setDirty();
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                p.sendSystemMessage(Component.literal("§a【法院】你的管制（社区矫正）已期满解除，请遵纪守法。"));
            }
        }

        // 4) 清理过期禁言记录
        boolean muteDirty = data.mutedUntil.entrySet().removeIf(e -> now >= e.getValue());
        if (muteDirty) data.setDirty();
    }

    /** 生成基岩监狱（仅在该存档第一次加载模组时执行） */
    public static void ensureGenerated(ServerLevel level, PrisonData data) {
        if (data.prisonGenerated) return;

        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        // 外壳为基岩，内部清空
        for (int x = 0; x < WIDTH_X; x++) {
            for (int z = 0; z < DEPTH_Z; z++) {
                for (int y = 0; y < HEIGHT; y++) {
                    boolean shell = x == 0 || z == 0 || y == 0
                            || x == WIDTH_X - 1 || z == DEPTH_Z - 1 || y == HEIGHT - 1;
                    level.setBlock(ORIGIN.offset(x, y, z), shell ? bedrock : air, 3);
                }
            }
        }

        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        for (int i = 0; i < CELLS; i++) {
            int startX = ORIGIN.getX() + 1 + i * 5; // 牢房内部起始 x（宽 3 格）

            // 牢房之间的双层基岩隔墙（z = 1..4）
            for (int dx = 3; dx <= 4; dx++) {
                for (int z = 1; z <= 4; z++) {
                    for (int y = 1; y < HEIGHT - 1; y++) {
                        level.setBlock(new BlockPos(startX + dx, ORIGIN.getY() + y, ORIGIN.getZ() + z), bedrock, 3);
                    }
                }
            }

            // 面向走廊的铁栅栏（z = 4，走廊在 z = 5）
            for (int dx = 0; dx < 3; dx++) {
                for (int y = 1; y <= 3; y++) {
                    level.setBlock(new BlockPos(startX + dx, ORIGIN.getY() + y, ORIGIN.getZ() + 4), bars, 3);
                }
            }

            // 红色床铺（床脚 z=2，床头 z=1）
            BlockPos foot = new BlockPos(startX, ORIGIN.getY() + 1, ORIGIN.getZ() + 2);
            level.setBlock(foot, Blocks.RED_BED.defaultBlockState()
                    .setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT), 3);
            level.setBlock(foot.north(), Blocks.RED_BED.defaultBlockState()
                    .setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD), 3);

            // 牢房与走廊的荧石照明
            level.setBlock(new BlockPos(startX + 1, ORIGIN.getY() + HEIGHT - 2, ORIGIN.getZ() + 2),
                    Blocks.GLOWSTONE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(startX + 1, ORIGIN.getY() + HEIGHT - 2, ORIGIN.getZ() + 5),
                    Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        // 强制加载监狱覆盖的所有区块，防止犯人掉线/区块卸载导致逻辑失效
        int minCx = ORIGIN.getX() >> 4;
        int maxCx = (ORIGIN.getX() + WIDTH_X - 1) >> 4;
        int minCz = ORIGIN.getZ() >> 4;
        int maxCz = (ORIGIN.getZ() + DEPTH_Z - 1) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }

        data.prisonGenerated = true;
        data.setDirty();
    }

    /** 收监：分配牢房、记录刑期、传送入狱并设置重生点 */
    public static void imprison(ServerPlayer player, int days, String crimeName) {
        days = Math.min(days, RuleOfLawMod.MAX_PRISON_DAYS);
        ServerLevel level = player.server.overworld();
        PrisonData data = PrisonData.get(level);
        ensureGenerated(level, data);

        int cell = findFreeCell(data);
        long release = level.getGameTime() + (long) days * RuleOfLawMod.TICKS_PER_DAY;
        data.prisoners.put(player.getUUID(), new PrisonData.Prisoner(cell, release, crimeName));
        data.setDirty();

        BlockPos pos = cellSpawn(cell);
        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        player.setRespawnPosition(level.dimension(), pos, 0f, true, false);
        player.sendSystemMessage(Component.literal("§c【狱警】你已被押送至基岩监狱 " + (cell + 1)
                + " 号牢房，刑期 " + days + " 天（游戏时间）。好好改造，重新做人！"));
    }

    /** 释放：移除记录、传送回世界出生点并全服公告 */
    public static void release(ServerLevel level, UUID uuid, boolean pardoned) {
        PrisonData data = PrisonData.get(level);
        PrisonData.Prisoner rec = data.prisoners.remove(uuid);
        data.setDirty();
        if (rec == null) return;

        MinecraftServer server = level.getServer();
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        String name = player != null ? player.getGameProfile().getName() : uuid.toString();

        if (player != null) {
            BlockPos spawn = level.getSharedSpawnPos();
            player.teleportTo(level, spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5, 0f, 0f);
            player.setRespawnPosition(level.dimension(), spawn, 0f, true, false);
        }

        server.getPlayerList().broadcastSystemMessage(Component.literal(pardoned
                ? "§6【特赦令】依据《中华人民共和国宪法》第六十七条、第八十条，§e" + name + " §6获得特赦，予以释放！"
                : "§a【监狱】§e" + name + " §a刑满释放。望其出狱后遵纪守法，重新做人。"), false);
    }

    /** 分配空牢房；满员时共用 1 号牢房 */
    private static int findFreeCell(PrisonData data) {
        boolean[] used = new boolean[CELLS];
        for (PrisonData.Prisoner p : data.prisoners.values()) {
            if (p.cell >= 0 && p.cell < CELLS) used[p.cell] = true;
        }
        for (int i = 0; i < CELLS; i++) {
            if (!used[i]) return i;
        }
        return 0;
    }

    /** 牢房内的传送落点（避开床铺） */
    public static BlockPos cellSpawn(int cell) {
        return new BlockPos(ORIGIN.getX() + 1 + cell * 5 + 1, ORIGIN.getY() + 1, ORIGIN.getZ() + 3);
    }

    /** 牢房内部包围盒（用于防越狱检测） */
    public static AABB cellBounds(int cell) {
        int minX = ORIGIN.getX() + 1 + cell * 5;
        int minY = ORIGIN.getY() + 1;
        int minZ = ORIGIN.getZ() + 1;
        return new AABB(minX, minY, minZ, minX + 3, ORIGIN.getY() + HEIGHT - 1, minZ + 3);
    }

    /** 判断坐标是否在监狱建筑范围内（含 1 格缓冲） */
    public static boolean insidePrison(BlockPos pos) {
        return pos.getX() >= ORIGIN.getX() - 1 && pos.getX() <= ORIGIN.getX() + WIDTH_X
                && pos.getZ() >= ORIGIN.getZ() - 1 && pos.getZ() <= ORIGIN.getZ() + DEPTH_Z
                && pos.getY() >= ORIGIN.getY() - 1 && pos.getY() <= ORIGIN.getY() + HEIGHT;
    }
              }
