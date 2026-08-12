package com.ruleoflaw.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.ruleoflaw.RuleOfLawMod;
import com.ruleoflaw.crime.CrimeType;
import com.ruleoflaw.prison.PrisonData;
import com.ruleoflaw.prison.PrisonManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * /law 系列命令：
 * /law                - 帮助
 * /law crimes         - 查看全部罪名与法条
 * /law prisoners      - 查看在押人员与剩余刑期
 * /law pardon <玩家>  - 发布特赦令（需 OP 权限；宪法第六十七条、第八十条）
 */
public final class LawCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("law")
                .executes(ctx -> help(ctx.getSource()))
                .then(Commands.literal("crimes")
                        .executes(ctx -> listCrimes(ctx.getSource())))
                .then(Commands.literal("prisoners")
                        .executes(ctx -> listPrisoners(ctx.getSource())))
                .then(Commands.literal("pardon")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> pardon(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"))))));
    }

    private static int help(CommandSourceStack src) {
        src.sendSystemMessage(Component.literal("§6§l===== 法治服务器（Rule of Law） ====="));
        src.sendSystemMessage(Component.literal("§f/law crimes §7- 查看罪名与对应法条"));
        src.sendSystemMessage(Component.literal("§f/law prisoners §7- 查看在押人员及剩余刑期"));
        src.sendSystemMessage(Component.literal("§f/law pardon <玩家> §7- 发布特赦令（管理员）"));
        src.sendSystemMessage(Component.literal("§7刑期按游戏时间计算，1 游戏日 = 现实 20 分钟，最长 10 天。"));
        return 1;
    }

    private static int listCrimes(CommandSourceStack src) {
        src.sendSystemMessage(Component.literal("§6§l===== 罪名与法条（刑法） ====="));
        for (CrimeType c : CrimeType.values()) {
            String range = c.maxDays > 0
                    ? "量刑：" + c.minDays + "~" + c.maxDays + " 天"
                    : "量刑：警告 / 罚金 / 管制（累犯可处监禁）";
            src.sendSystemMessage(Component.literal("§e" + c.displayName
                    + " §7(" + c.article + ") §f" + range));
        }
        return 1;
    }

    private static int listPrisoners(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        ServerLevel level = server.overworld();
        PrisonData data = PrisonData.get(level);
        long now = level.getGameTime();

        if (data.prisoners.isEmpty()) {
            src.sendSystemMessage(Component.literal("§a当前监狱无人服刑，天下太平。"));
            return 1;
        }

        src.sendSystemMessage(Component.literal("§6§l===== 在押人员 ====="));
        for (Map.Entry<UUID, PrisonData.Prisoner> e : data.prisoners.entrySet()) {
            Optional<GameProfile> profile = server.getProfileCache() != null
                    ? server.getProfileCache().get(e.getKey())
                    : Optional.empty();
            String name = profile.map(GameProfile::getName).orElse(e.getKey().toString());
            double remain = Math.max(0, (e.getValue().releaseTime - now) / (double) RuleOfLawMod.TICKS_PER_DAY);
            src.sendSystemMessage(Component.literal("§e" + name
                    + " §7| 罪名：" + e.getValue().crime
                    + " | 牢房：" + (e.getValue().cell + 1) + " 号"
                    + " | 剩余刑期：" + String.format("%.1f", remain) + " 天"));
        }
        return 1;
    }

    private static int pardon(CommandSourceStack src, ServerPlayer target) {
        ServerLevel level = src.getServer().overworld();
        PrisonData data = PrisonData.get(level);
        if (!data.prisoners.containsKey(target.getUUID())) {
            src.sendFailure(Component.literal("该玩家不在服刑期间，无需特赦。"));
            return 0;
        }
        PrisonManager.release(level, target.getUUID(), true);
        src.sendSuccess(() -> Component.literal("§6已依据《宪法》第六十七条、第八十条对 "
                + target.getGameProfile().getName() + " 发布特赦令。"), true);
        return 1;
    }
}