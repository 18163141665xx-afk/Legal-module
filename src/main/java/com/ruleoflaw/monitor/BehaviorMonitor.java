package com.ruleoflaw.monitor;

import com.ruleoflaw.RuleOfLawMod;
import com.ruleoflaw.command.LawCommands;
import com.ruleoflaw.crime.CrimeType;
import com.ruleoflaw.court.CourtSystem;
import com.ruleoflaw.prison.PrisonData;
import com.ruleoflaw.prison.PrisonManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家行为监控系统（全部事件的入口）。
 *
 * 监控范围：
 * 击杀玩家/村民、攻击玩家、滥杀动物、杀害珍稀动物、纵火、引爆 TNT、
 * 翻他人附近的箱子、破坏村庄方块、聊天刷屏、囚犯越狱等。
 */
@Mod.EventBusSubscriber(modid = RuleOfLawMod.MOD_ID)
public final class BehaviorMonitor {

    /** 珍贵、濒危野生动物（对应刑法第三百四十一条第一款） */
    private static final Set<EntityType<?>> ENDANGERED = Set.of(
            EntityType.PANDA, EntityType.AXOLOTL, EntityType.DOLPHIN,
            EntityType.TURTLE, EntityType.POLAR_BEAR, EntityType.SNIFFER);

    /** 故意伤害立案冷却（30 秒，防止连击重复立案） */
    private static final Map<UUID, Long> ASSAULT_CD = new HashMap<>();
    /** 毁坏财物立案冷却（60 秒） */
    private static final Map<UUID, Long> VANDAL_CD = new HashMap<>();
    /** 盗窃立案冷却（30 秒） */
    private static final Map<UUID, Long> THEFT_CD = new HashMap<>();
    /** 每日狩猎计数：{游戏日序号, 当日已猎杀数} */
    private static final Map<UUID, int[]> HUNT_COUNT = new HashMap<>();
    /** 聊天时间戳队列（刷屏检测） */
    private static final Map<UUID, Deque<Long>> CHAT_TIMES = new HashMap<>();

    /** 每日合法狩猎配额 */
    private static final int DAILY_HUNT_QUOTA = 10;

    // ==================== 击杀：杀人罪 / 危害珍稀动物 / 非法狩猎 ====================

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        LivingEntity dead = event.getEntity();

        if (dead instanceof ServerPlayer victim && victim != player) {
            CourtSystem.prosecute(player, CrimeType.MURDER_PLAYER,
                    "被害人：" + victim.getGameProfile().getName());
        } else if (dead instanceof AbstractVillager) {
            CourtSystem.prosecute(player, CrimeType.MURDER_VILLAGER, "被害人：村民");
        } else if (ENDANGERED.contains(dead.getType())) {
            CourtSystem.prosecute(player, CrimeType.ENDANGERED_ANIMAL,
                    "受害动物：" + dead.getType().getDescription().getString());
        } else if (dead instanceof Animal) {
            long day = player.level().getDayTime() / RuleOfLawMod.TICKS_PER_DAY;
            int[] rec = HUNT_COUNT.computeIfAbsent(player.getUUID(), k -> new int[]{0, 0});
            if (rec[0] != (int) day) {
                rec[0] = (int) day;
                rec[1] = 0;
            }
            rec[1]++;
            if (rec[1] == DAILY_HUNT_QUOTA) {
                player.sendSystemMessage(Component.literal("§e【林业公安局】警告：你今日猎杀动物已达 "
                        + DAILY_HUNT_QUOTA + " 只，继续猎杀将涉嫌非法狩猎罪！"));
            } else if (rec[1] > DAILY_HUNT_QUOTA) {
                CourtSystem.prosecute(player, CrimeType.ILLEGAL_HUNTING,
                        "单日猎杀 " + rec[1] + " 只动物");
            }
        }
    }

    // ==================== 伤害：故意伤害罪 ====================

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (!(event.getEntity() instanceof ServerPlayer victim) || victim == attacker) return;

        long now = attacker.level().getGameTime();
        Long last = ASSAULT_CD.get(attacker.getUUID());
        if (last != null && now - last < 600) return; // 30 秒内不重复立案
        ASSAULT_CD.put(attacker.getUUID(), now);

        CourtSystem.prosecute(attacker, CrimeType.ASSAULT,
                "被害人：" + victim.getGameProfile().getName()
                        + "，伤害 " + String.format("%.1f", event.getAmount()) + " 点");
    }

    // ==================== 纵火 / 存放爆炸物 ====================

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BlockState state = event.getPlacedBlock();

        if (state.is(Blocks.FIRE)) {
            CourtSystem.prosecute(player, CrimeType.ARSON, "点燃方块引发火灾");
        } else if (state.is(Blocks.TNT)) {
            player.sendSystemMessage(Component.literal(
                    "§e【公安机关】警告：你正在存放爆炸物！一旦引爆将构成爆炸罪。"));
        }
    }

    // ==================== 爆炸罪 ====================

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Start event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getExplosion().getExploder() instanceof PrimedTnt tnt)) return;
        if (tnt.getOwner() instanceof ServerPlayer player) {
            CourtSystem.prosecute(player, CrimeType.EXPLOSION, "引爆 TNT 炸药");
        }
    }

    // ==================== 破坏方块：毁坏财物罪 / 囚犯禁止破坏 ====================

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        PrisonData data = PrisonData.get(player.server.overworld());

        // 服刑期间禁止破坏（基岩本就挖不动，双保险）
        if (data.prisoners.containsKey(player.getUUID())) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("§c【狱警】服刑期间禁止破坏监狱设施！"));
            return;
        }

        // 在村民聚居区（村庄）32 格内破坏方块 = 故意毁坏财物
        long now = level.getGameTime();
        Long last = VANDAL_CD.get(player.getUUID());
        if (last != null && now - last < 1200) return;

        boolean nearVillager = !level.getEntitiesOfClass(AbstractVillager.class,
                player.getBoundingBox().inflate(32)).isEmpty();
        if (nearVillager) {
            VANDAL_CD.put(player.getUUID(), now);
            CourtSystem.prosecute(player, CrimeType.VANDALISM, "在村庄范围内破坏他人财物");
        }
    }

    // ==================== 翻他人箱子：盗窃罪 ====================

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getContainer() instanceof ChestMenu)) return;

        // 6 格内有其他在线玩家 -> 涉嫌盗窃他人财物
        List<ServerPlayer> nearby = player.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                player.getBoundingBox().inflate(6), p -> p != player);
        if (nearby.isEmpty()) return;

        long now = player.serverLevel().getGameTime();
        Long last = THEFT_CD.get(player.getUUID());
        if (last != null && now - last < 600) return;
        THEFT_CD.put(player.getUUID(), now);

        CourtSystem.prosecute(player, CrimeType.THEFT,
                "翻查 " + nearby.get(0).getGameProfile().getName() + " 附近的箱子");
    }

    // ==================== 聊天：禁言执行 / 刷屏寻衅滋事 ====================

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        ServerLevel level = player.serverLevel();
        PrisonData data = PrisonData.get(player.server.overworld());
        long now = level.getGameTime();

        // 剥夺政治权利期间禁止发言
        Long muteEnd = data.mutedUntil.get(player.getUUID());
        if (muteEnd != null && now < muteEnd) {
            event.setCanceled(true);
            long remainMinutes = Math.max(1, (muteEnd - now) / 20 / 60);
            player.sendSystemMessage(Component.literal(
                    "§c【法院】你正处于剥夺政治权利期间，剩余禁言约 " + remainMinutes + " 分钟。"));
            return;
        }

        // 刷屏检测：8 秒内发送 5 条以上 -> 寻衅滋事
        Deque<Long> q = CHAT_TIMES.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        q.addLast(now);
        while (!q.isEmpty() && now - q.peekFirst() > 160) q.pollFirst();
        if (q.size() >= 5) {
            q.clear();
            CourtSystem.prosecute(player, CrimeType.PICKING_QUARRELS, "短时间内连续刷屏扰乱秩序");
        }
    }

    // ==================== 反越狱：禁用末影珍珠 / 紫颂果 ====================

    @SubscribeEvent
    public static void onEnderPearl(EntityTeleportEvent.EnderPearl event) {
        if (event.getEntity() instanceof ServerPlayer player && isPrisoner(player)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("§c【狱警】监狱内禁止使用末影珍珠越狱！"));
        }
    }

    @SubscribeEvent
    public static void onChorusFruit(EntityTeleportEvent.ChorusFruit event) {
        if (event.getEntity() instanceof ServerPlayer player && isPrisoner(player)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("§c【狱警】监狱内禁止使用紫颂果越狱！"));
        }
    }

    // ==================== 登录 / 重生处理 ====================

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.sendSystemMessage(Component.literal(
                "§6【法治服务器】§f本世界已启用《刑法》《宪法》模组：杀人、纵火、爆炸、盗窃、伤害、"
                        + "滥杀动物、刷屏等行为将被审判入狱（刑期按游戏时间计，最长 10 天）。输入 §e/law §f查看法律。"));

        // 离线期间刑满释放但人还留在监狱里 -> 送回出生点
        PrisonData data = PrisonData.get(player.server.overworld());
        if (!data.prisoners.containsKey(player.getUUID())
                && PrisonManager.insidePrison(player.blockPosition())) {
            ServerLevel level = player.server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            player.teleportTo(level, spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5, 0f, 0f);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PrisonData data = PrisonData.get(player.server.overworld());
        PrisonData.Prisoner rec = data.prisoners.get(player.getUUID());
        if (rec != null) {
            BlockPos pos = PrisonManager.cellSpawn(rec.cell);
            player.teleportTo(player.server.overworld(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        }
    }

    // ==================== 服务器启动 / 主循环 / 命令注册 ====================

    /** 存档加载后自动生成基岩监狱（仅第一次） */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        PrisonData data = PrisonData.get(level);
        PrisonManager.ensureGenerated(level, data);
    }

    /** 每秒驱动一次监狱管理逻辑 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % 20 != 0) return;
        PrisonManager.tick(server);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LawCommands.register(event.getDispatcher());
    }

    private static boolean isPrisoner(ServerPlayer player) {
        return PrisonData.get(player.server.overworld()).prisoners.containsKey(player.getUUID());
    }
        }
