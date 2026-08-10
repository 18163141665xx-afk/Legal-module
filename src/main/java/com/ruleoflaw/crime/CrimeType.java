package com.ruleoflaw.crime;

/**
 * 罪名定义。刑罚时间均以“游戏日”为单位（1 游戏日 = 24000 tick = 现实 20 分钟），
 * 最长不超过 10 个游戏日（由 RuleOfLawMod.MAX_PRISON_DAYS 统一封顶）。
 */
public enum CrimeType {
    MURDER_PLAYER("故意杀人罪", "《中华人民共和国刑法》第二百三十二条", 8, 10),
    MURDER_VILLAGER("故意杀人罪（杀害村民）", "《中华人民共和国刑法》第二百三十二条", 5, 8),
    ASSAULT("故意伤害罪", "《中华人民共和国刑法》第二百三十四条", 0, 0),
    ENDANGERED_ANIMAL("危害珍贵、濒危野生动物罪", "《中华人民共和国刑法》第三百四十一条", 2, 5),
    ILLEGAL_HUNTING("非法狩猎罪", "《中华人民共和国刑法》第三百四十一条第二款", 0, 1),
    ARSON("放火罪", "《中华人民共和国刑法》第一百一十四条", 3, 5),
    EXPLOSION("爆炸罪", "《中华人民共和国刑法》第一百一十五条", 5, 8),
    THEFT("盗窃罪", "《中华人民共和国刑法》第二百六十四条", 0, 2),
    VANDALISM("故意毁坏财物罪", "《中华人民共和国刑法》第二百七十五条", 0, 2),
    PICKING_QUARRELS("寻衅滋事罪", "《中华人民共和国刑法》第二百九十三条", 0, 1);

    public final String displayName;
    public final String article;
    public final int minDays;
    public final int maxDays;

    CrimeType(String displayName, String article, int minDays, int maxDays) {
        this.displayName = displayName;
        this.article = article;
        this.minDays = minDays;
        this.maxDays = maxDays;
    }
}
