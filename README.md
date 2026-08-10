# 法治服务器（Rule of Law）— Minecraft Forge 1.20.1 模组

监测玩家行为，依据《中华人民共和国刑法》与《宪法》进行游戏内审判与惩罚。
加载模组后自动在 (1000, 180, 1000) 高空生成一座**基岩监狱**（8 间牢房，区块强加载）。
所有刑期**按游戏时间计算**（1 游戏日 = 现实 20 分钟），最长不超过 **10 个游戏日**。

---

## 一、罪名与刑罚一览

| 触发行为 | 罪名 | 法条 | 刑罚 |
|---|---|---|---|
| 击杀玩家 | 故意杀人罪 | 刑法第232条 | 8~10 天监禁 + 附加剥夺政治权利（禁言）+ 罚金 |
| 击杀村民 | 故意杀人罪（杀害村民） | 刑法第232条 | 5~8 天监禁 |
| 攻击玩家 | 故意伤害罪 | 刑法第234条 | 第1次警告+罚金 → 第2次管制2天 → 第3次起监禁1~3天 |
| 杀熊猫/美西螈/海豚/海龟/北极熊/嗅探兽 | 危害珍贵、濒危野生动物罪 | 刑法第341条 | 2~5 天监禁 + 罚金 |
| 一天猎杀普通动物超过 10 只 | 非法狩猎罪 | 刑法第341条第2款 | 罚金+管制1天，再犯拘役1天 |
| 放置火焰（打火石点火） | 放火罪 | 刑法第114条 | 3~5 天监禁 |
| 引爆 TNT | 爆炸罪 | 刑法第115条 | 5~8 天监禁（放置 TNT 时先行警告） |
| 在其他玩家 6 格内开箱子 | 盗窃罪 | 刑法第264条 | 第1次警告 → 第2次罚金 → 第3次起监禁1~2天 |
| 在村庄 32 格内破坏方块 | 故意毁坏财物罪 | 刑法第275条 | 第1次警告 → 第2次罚金 → 第3次起监禁1~2天 |
| 8 秒内刷屏 5 条消息 | 寻衅滋事罪 | 刑法第293条 | 禁言1天，再犯禁言2天+管制1天 |

**宪法元素**：逮捕广播援引宪法第37条（非经法院决定不受逮捕）；
特赦命令援引宪法第67、80条。

**刑罚种类**：警告 / 罚金（扣经验等级）/ 管制（缓慢+虚弱的社区矫正）/ 剥夺政治权利（禁言）/ 有期徒刑（基岩监狱服刑）。
**累犯从重**：同一罪名第 3 次触犯直接顶格量刑（前科记录存档持久化）。

**反越狱机制**：基岩结构 + 每秒巡逻检测（离开牢房立即押回）+ 禁用末影珍珠/紫颂果
+ 重生点锁定在牢房 + 服刑期间禁止破坏方块。

## 二、命令

- `/law` — 帮助
- `/law crimes` — 查看全部罪名与法条
- `/law prisoners` — 查看在押人员与剩余刑期
- `/law pardon <玩家>` — 发布特赦令（需 OP 权限）

## 三、如何打包（构建 jar）★

> 需要一台电脑（Windows / macOS / Linux 均可）。手机上没有成熟的 Forge 构建环境。

### 第 1 步：安装 JDK 17
1.20.1 必须用 **Java 17**。下载安装 Temurin 17：
https://adoptium.net/zh-CN/temurin/releases/?version=17

### 第 2 步：下载 Forge 1.20.1 MDK
打开 https://files.minecraftforge.net ，左侧选 **1.20.1**，点击 **MDK** 下载并解压。
（MDK 自带 `gradlew` 启动脚本和 `gradle/wrapper/`，本工程没有包含这两个文件，必须从 MDK 获得。）

### 第 3 步：合并本工程
1. 删除 MDK 里的 `src` 文件夹，把本工程的 `src` 复制进去；
2. 用本工程的 `build.gradle`、`settings.gradle`、`gradle.properties` 覆盖 MDK 的同名文件。

### 第 4 步：构建
在 MDK 目录打开命令行：

```bash
# Windows
gradlew.bat build

# macOS / Linux
chmod +x gradlew && ./gradlew build
```

首次构建会下载 ForgeGradle、Minecraft 官方 jar 与映射表（约几百 MB），请耐心等待。
构建成功后产物在：

```
build/libs/ruleoflaw-1.0.0.jar   ← 这就是你的模组
```

### 第 5 步：安装运行
把 `ruleoflaw-1.0.0.jar` 放入 **Forge 1.20.1** 客户端或服务端的 `mods` 文件夹即可。
进入存档后会自动生成基岩监狱。

### 开发调试（可选）
```bash
./gradlew runClient   # 直接启动带模组的测试客户端
./gradlew runServer   # 启动测试服务端
```

## 四、常见问题

- **构建报 Java 版本错误**：确认 `java -version` 显示 17.x。
- **下载依赖超时**：给 `gradle.properties` 添加代理，或使用国内镜像。
- **想改监狱位置/规模**：修改 `PrisonManager.ORIGIN` 与 `CELLS`。
- **想改最长刑期**：修改 `RuleOfLawMod.MAX_PRISON_DAYS`。
- **想加新罪名**：在 `CrimeType` 加枚举 → `BehaviorMonitor` 加事件监听 → `CourtSystem.decideAndApply` 加量刑分支。

## 五、项目结构

```
RuleOfLawMod/
├── build.gradle / settings.gradle / gradle.properties   # 构建配置（ForgeGradle 6）
└── src/main/
    ├── java/com/ruleoflaw/
    │   ├── RuleOfLawMod.java          # 主类（MOD_ID、全局常量）
    │   ├── crime/CrimeType.java       # 罪名枚举（罪名、法条、量刑范围）
    │   ├── court/CourtSystem.java     # 法院：公诉、判决、执行（罚金/禁言/管制/监禁）
    │   ├── monitor/BehaviorMonitor.java# 行为监控：全部游戏事件入口
    │   ├── prison/PrisonManager.java  # 基岩监狱：生成、收监、释放、防越狱
    │   ├── prison/PrisonData.java     # SavedData 存档：犯人/禁言/管制/前科
    │   └── command/LawCommands.java   # /law 命令（含特赦）
    └── resources/
        ├── META-INF/mods.toml         # 模组元数据
        └── pack.mcmeta
```

> 本模组为娱乐/普法整活性质，法条引用做了游戏化简化，请勿当作真实法律意见。
