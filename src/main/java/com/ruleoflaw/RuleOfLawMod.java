package com.ruleoflaw;

import net.minecraftforge.fml.common.Mod;

/**
 * 法治服务器（Rule of Law）模组主类。
 * 所有事件监听均由 @Mod.EventBusSubscriber 静态订阅（见 monitor 包），
 * 因此主类无需额外注册逻辑。
 */
@Mod(RuleOfLawMod.MOD_ID)
public class RuleOfLawMod {
    public static final String MOD_ID = "ruleoflaw";

    /** 一个游戏日的 tick 数（20 分钟现实时间） */
    public static final long TICKS_PER_DAY = 24000L;

    /** 最长刑期：10 个游戏日（按你的要求封顶） */
    public static final int MAX_PRISON_DAYS = 10;

    public RuleOfLawMod() {
    }
}
