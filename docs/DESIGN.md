# Mind-Robot 设计文档

目标版本：Minecraft **1.7.10** / Forge **10.13.4.1614**（MCP stable/12），Java 8 字节码（Jabel 现代语法）。
本文只记录**已确认**的规则与由规则推导出的技术决策；未确认项统一列在第 6 节。

---

## 1. 玩法规则（已确认）

| 编号 | 规则 |
|---|---|
| R1 | 机器人是**独立实体**，有自己的位置、碰撞箱与生命值。 |
| R2 | 玩家戴上**遥控头盔**（头部位护甲）后，打开**转轮面板**，选择「操作机器人」进入驾驶。 |
| R3 | 转轮面板是**通用交互面板**，后续功能都挂同一个面板，不另做新界面。 |
| R4 | 驾驶期间，**摄像机与操作全部作用于机器人**。 |
| R5 | 玩家本体**留在原地的进入点**，不跟随移动。 |
| R6 | 本体仍受世界影响：怪物能攻击、饥饿会掉、跌落会受伤。**驾驶期间你背后是敞开的**。 |
| R7 | 本体血量归零（任何原因，含饥饿）→ **立即中断连接**。 |
| R8 | 机器人被摧毁 → **强制退出，玩家本体存活**。 |
| R9 | 控制距离**不设上限**。 |
| R10 | 驾驶期间**玩家无法操作自己的物品栏**，头盔也**摘不下来**（护甲槽锁定）。 |
| R11 | **退出通道**：机器人视角里同样能打开转轮面板，从面板选择退出。 |
| R12 | 转轮面板**双上下文**：戴头盔的玩家看到「操作机器人」；驾驶中的玩家看到「退出」与后续功能项。 |
| R13 | 头盔与机器人通过**频道芯片**配对：两边芯片的频道号完全相同即可配对；一台机器人同一时刻只服务一个驾驶员。 |
| R14 | 机器人来源：先造**机器人装配台**方块，在装配台里组装，组装完成后放出实体。 |
| R15 | 机器人能力面：移动、挖掘方块、攻击、交互/使用物品。 |
| R16 | 机器人有**独立的机体结构耐久**，远高于玩家生命值；上限 = 身上所有零件耐久之和；**不可恢复**；归零即机体损毁。 |
| R17 | 结构耐久**不是** `EntityLivingBase` 的生命值：不能被治疗、不参与原版死亡流程、不能被第三方模组当作生命值读取或榨取。 |
| R18 | 机器人会被怪物**主动攻击**（与玩家同等对待）。 |
| R19 | 免疫水下窒息与摔落伤害；`inWall`（卡在方块里的挤压/窒息）伤害 **×4**。 |
| R20 | 机体损毁后按 R8 强制退出驾驶，玩家本体存活。 |
| R21 | 配对靠**频道芯片**：芯片由玩家设置频道号（数字），装进头盔；机器人同样装芯片。两边号码完全相同即可建立控制关系。 |
| R22 | 频道分**私人**与**公共**：私人频道只有所有者能用，且 owner **跟随持有者**——安装芯片时把 owner 刷新成执行安装的玩家，所以抢来的芯片装到自己头盔上也开不动别人的私人机器人；公共频道的机器人任何持有同号头盔的人都能连接。 |
| R23 | 驾驶期间机器人所在区块**强制保持加载**（服务器强制 ticket），避免区块卸载把远处的机器人"睡掉"。 |
| R24 | 机器人所在区块**卸载不判定为损毁**：连接保持，按最后已知位置把区块重新拉回来，实体加载后继续驾驶。 |
| R25 | 频道被占用时**先到先得**：后来者收到"频道被占用"，既不抢占也不排队。 |
| R26 | 机器人没有芯片或频道未设置时**不可连接**。 |

R6/R7 是核心张力：遥控本身不强，代价是身体无人看守。任何"顺手让本体无敌"的实现都违背设计意图。

---

## 2. 技术决策

### D1 · 摄像机必须由客户端接管
`Minecraft.renderViewEntity` 的声明类型是 `EntityLivingBase`（`net/minecraft/client/Minecraft.java:212`），不是 `Entity`。

推论：**机器人实体必须继承 `EntityLivingBase`**，否则不能当视角载体。

- 连接建立后客户端把 `mc.renderViewEntity` 指向机器人；断开时归还 `mc.thePlayer`。
- 必须同时屏蔽本体第一人称渲染（手部、视角模型），否则画面会叠着本体的视角。
- 服务端不参与视角，只负责告诉客户端"你现在连的是哪个实体"。

### D2 · 输入权威在服务端
客户端只发**输入意图**（前后/左右/朝向/跳跃/潜行/攻击/使用），服务端按 tick 校验并驱动机器人。客户端不允许直接改坐标，否则飞行、加速、穿墙都能伪造。

### D3 · 本体冻结的正确边界
"屏蔽对身体的操作" ≠ "免疫"：

| 类别 | 驾驶期间 |
|---|---|
| 玩家输入（移动/转向/跳跃/攻击/使用/开背包） | 丢弃，不作用于本体 |
| 本体位置 | 每 tick 归位到进入点（防活塞、水流、爆炸推动） |
| 世界伤害（怪、摔落、火、岩浆） | **照常生效** |
| 饥饿与自然回血 | 照常（R6），血量到 0 走 R7 |
| 护甲槽 | 锁定，头盔不可摘下（R10） |

### D4 · 断线只有一个入口
所有结束路径（本体死亡、机器人死亡、玩家在转轮面板点退出、玩家断线、维度切换、服务器停止）都走 `RobotLinkManager.disconnect(player, reason)`：解绑 → 归还摄像机 → 恢复输入 → 清空 anchor。分散处理会留下"幽灵连接"。

### D5 · 频道数据模型（取代早期的 UUID 配对）
配对不再绑定 UUID，而是绑定**频道号**：

| 数据 | 位置 | 内容 |
|---|---|---|
| 芯片 | `ItemFrequencyChip` 的 NBT | `channel`（int）、`mode`（PRIVATE / PUBLIC）、`owner`（仅 PRIVATE 有意义的 UUID） |
| 头盔 | 头盔 NBT（安装芯片后写入） | 同上三项的快照 |
| 机器人 | 实体 NBT | 同上三项的快照 |

判定顺序（服务端唯一入口）：
1. 头盔没有芯片 → 面板只有禁用项，不给连接选项。
2. 头盔频道 ≠ 机器人频道 → 拒绝（提示"频道不符"）。
3. 机器人是 **PRIVATE** → 头盔 `owner` 必须等于机器人 `owner`，否则拒绝（同号的其他人也进不来）。
4. 机器人是 **PUBLIC** → 任何同号者都可以连；但同一时刻只允许一个驾驶员（R13），已被占用则拒绝。
5. 机器人已在别的连接里 → 拒绝。

**为什么不用 UUID 绑定**：频道是"可被玩家操作、可被分享、可被抢占"的一等公民，玩法上能做出公共机器人平台、私人载具、频道被扫号的对抗；UUID 绑定只能一对一，且玩家无法在游戏内理解自己的配对状态。

### D10 · 强制加载走 ForgeChunkManager
已核对 1.7.10 的 API 存在：`ForgeChunkManager.requestTicket(Object mod, World world, Type type)`、`forceChunk(Ticket, ChunkCoordIntPair)`、`unforceChunk(...)`、`releaseTicket(Ticket)`。

- 连接建立时申请一张 `Type.NORMAL` ticket；机器人每跨区块就 `forceChunk` 新区块 + `unforceChunk` 旧区块（ticket 位置跟着走，不是无限扩大加载范围）。
- 断开（`RobotLinkManager.disconnect` 的任意原因）必须 `releaseTicket`，否则 ticket 泄漏会永久占用区块加载。
- 机器人跨维度时先释放再在新世界重建 ticket。

### D6 · 转轮面板是纯客户端自绘 + 服务端选项裁剪
菜单项由服务端按上下文下发（是否戴头盔、是否在驾驶），客户端只负责画轮盘与命中检测。新增功能 = 服务端多一条选项 + 客户端多一个扇区，不动渲染框架。

### D7 · 结构耐久必须与生命值物理隔离
摄像机要求实体是 `EntityLivingBase`（D1），但如果直接拿它的 `health` 当耐久，会同时踩两个坑：

1. **变成材料源**：血魔法一类模组通过击杀/献祭把生物生命值换成资源。机器人一旦有可被击杀的 health，玩家就能刷它。
2. **崩溃高发**：第三方模组会读 `getMaxHealth()` / `getHealth()` 做伤害计算、状态条、AI 判定。一个"血量几万"的 `EntityLivingBase` 会让这些逻辑溢出、除零，或在它身上挂装备/掉落表时炸掉。

实现方式：

| 层 | 做法 |
|---|---|
| 生命值 | 伪装层。`applyEntityAttributes()` 注册 `maxHealth` 与 `movementSpeed`（防 NPE），`setHealth` 覆写成空操作，构造函数里用 `super.setHealth()` 写一次初值，于是对外恒定读到 20/20、永不进入死亡流程。**注意：`getHealth()` 与 `getMaxHealth()` 在 1.7.10 是 `final`（`EntityLivingBase.java:807` / `:1289`），无法覆写**；`setEntityHealth` 在 1.7.10 也不存在。 |
| 伤害入口 | 覆写 `attackEntityFrom` 且**不调 super**，转进自己的管线。这样 `LivingAttackEvent` / `LivingHurtEvent` / `LivingDeathEvent` 都不会为它触发，第三方伤害修正与击杀回调完全看不到它。`onDeath` 也重定向到 `destroyRobot()` 作为兜底。 |
| 真耐久 | 独立字段 `structuralIntegrity`，NBT 持久化，只由自己的破坏路径扣减，没有任何恢复路径。 |
| 销毁 | 归零走 `destroyRobot()`：播效果 → 通知连接管理器断开 → `setDead()`，**不经过原版死亡流程**。 |
| 兼容 | 公开 `IRobotCore` 接口（`getStructuralIntegrity` / `getMaxStructuralIntegrity` / `applyStructuralDamage` / `isRobot`），愿意适配的模组显式对接。 |

附带收益：实体只继承 `EntityLivingBase` 而不继承 `EntityLiving`，没有装备栏、掉落表和 AI 任务表，对第三方模组的暴露面更小。

### D8 · 仇恨：必须注入"目标 + 攻击"两条 AI，再加周期扫描
1.7.10 的僵尸/骷髅只注册了针对 `EntityPlayer` 的目标 AI（`EntityZombie.java:75`：`new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, true)`），机器人不是玩家，天然不会被主动攻击。

**只注入目标 AI 是不够的**：`EntityAIAttackOnCollide` 有 `classTarget` 字段，`shouldExecute()` 里会检查 `!classTarget.isAssignableFrom(target.getClass())`（`EntityAIAttackOnCollide.java:61`）。僵尸的攻击任务是 `EntityAIAttackOnCollide(this, EntityPlayer.class, ...)`，所以即使我们强行把它的 target 设成机器人，攻击任务也会判定类不匹配而拒绝执行——怪只会站着发呆，不会追也不会打。

因此要注入**两条**：

| 注入对象 | 内容 | 缺失后果 |
|---|---|---|
| `mob.targetTasks` | `new EntityAINearestAttackableTarget(mob, EntityRobot.class, 0, true)` | 怪根本不锁定机器人 |
| `mob.tasks` | `new EntityAIAttackOnCollide(mob, EntityRobot.class, speed, false)` | 锁定了也不打、不追（classTarget 不匹配） |

执行方式：
- `EntityJoinWorldEvent`（服务端）时为每个 `EntityCreature` 注入两条任务，注入前去重（避免重复叠加导致 AI 抖动与内存膨胀）；只对 `EntityMob` 注入，别给牛羊塞。
- 机器人进入世界时，对已加载范围内的怪补注入一次（它们早就在世界里，不会触发 join 事件）。
- 追加**周期仇恨扫描**（节流执行，例如每 20 tick）：对已加载的 `EntityCreature`，若 `getAttackTarget() == null` 且机器人在其追踪范围内且可见，则 `setAttackTarget(robot)`。这条覆盖"没有注册 vanilla 目标 AI、但攻击逻辑读 `getAttackTarget()`"的第三方怪。
- 机器人攻击某只怪后，原版 `EntityAIHurtByTarget` 会接管反击。

**为什么不干脆把机器人伪装成玩家**：`EntityPlayer` 子体会引出比解决的问题更多的崩溃点。
- 服务端模组普遍 `entity instanceof EntityPlayerMP` 后直接取 `playerNetServerHandler` / `getServerForPlayer()` / 权限系统，我们拿不出现实 handler → NPE。
- 只继承 `EntityPlayer` 时服务端 `instanceof EntityPlayerMP` 为假，客户端与服务端认的"玩家"不是同一个类型，判定分裂。
- 玩家身份会触发死亡掉落、经验、计分板、Boss 血条、灵魂绑定/吸血类模组的目标扫描，与 R17「不能被当作生命值榨取」直接冲突。
- 结论：伪装成玩家的覆盖率是 100%，但崩溃面也是 100%。不采用。

**诚实说明覆盖率**：走 vanilla AI 的模组怪（1.7.10 绝大多数）全覆盖；完全自研 AI、不读 `getAttackTarget()`、不经过 `EntityAIAttackOnCollide` 的特殊 Boss 覆盖不到，需要按模组单独适配。

### D9 · 伤害策略表

| `DamageSource` | 系数 | 说明 |
|---|---|---|
| `drown` | 0 | 免疫水下窒息 |
| `fall` | 0 | 免疫摔落 |
| `inWall` | ×4 | 卡在方块里的挤压/窒息，翻四倍 |
| `outOfWorld` | 直接销毁 | 掉虚空就机毁，不留幽灵连接 |
| 其它 | ×1 | 普通结构伤害 |

注意：1.7.10 的 `DamageSource` 只有 `inWall`/`drown`/`fall`/`outOfWorld`/`generic`/`magic`/`anvil`/`fallingBlock`，**没有 `cramming`**。"生物互相挤压造成伤害"是 1.8 之后才有的机制；要实现那种"被一群怪挤爆"的规则得自己写（重叠实体数持续超过阈值 N tick 后扣耐久），属于新增机制，见第 6 节 Q5。

---

## 3. 包结构

```
com.github.qianwj.mindrobot
├── MindRobot.java            主类 / 注册入口
├── Config.java               配置
├── CommonProxy / ClientProxy 双端代理
├── item/
│   ├── ItemRemoteHelmet.java 遥控头盔（ItemArmor，armorType=0，NBT 存频道快照）
│   └── ItemFrequencyChip.java 频道芯片（设置频道 / 装进头盔）
├── block/
│   ├── BlockAssemblyTable.java   机器人装配台
│   └── TileAssemblyTable.java    组装逻辑与产物暂存
├── entity/
│   └── EntityRobot.java      机器人实体（extends EntityLivingBase）
├── link/
│   ├── RobotLinkManager.java 连接表 + disconnect 统一入口 + D5 校验
│   ├── RobotLink.java        单条连接（玩家 UUID、机器人 UUID、anchor 坐标）
│   ├── ChannelData.java      频道数据模型（channel / mode / owner）
│   ├── ChannelMode.java      PRIVATE / PUBLIC
│   ├── RobotChunkLoader.java 驾驶期强制加载（ForgeChunkManager ticket）
│   └── LinkDenial.java       连接被拒绝的具体原因
├── command/
│   └── CommandMindRobot.java /mindrobot chip|spawn
├── network/                  连接请求、连接状态同步、芯片配置、移动意图、菜单选项
└── client/
    ├── gui/RadialMenuGui.java     转轮面板（自绘 GuiScreen + 扇区命中）
    ├── gui/ChipConfigGui.java     频道芯片设置界面（GuiTextField）
    ├── key/KeyBindings.java       打开转轮面板的键位
    └── render/                    摄像机接管、机器人渲染、本体渲染屏蔽
```

---

## 4. 实施阶段

| 阶段 | 内容 | 验收标准 |
|---|---|---|
| P0 | 头盔、键位、转轮面板、实体注册、连接管理（含配对 NBT）、摄像机接管、输入与物品栏锁定、死亡断开 | 戴头盔 → 开面板 → 连接测试机器人 → 视角变机器人 → 本体原地不动 → 开背包被拦 → 本体血量归零立刻断线 → 面板里能主动退出 |
| P0.5 | 频道芯片（设置界面 / 私人·公共）、头盔与机器人的频道数据、`/mindrobot spawn` 命令、驾驶期强制加载区块 | 设频道 → 装芯片到头盔 → 同号机器人可连、异号拒绝、私人频道他人被拒；断开后区块 ticket 释放 |
| P1 | 机器人移动与朝向的服务端校验驱动 | 能走能转，客户端改坐标无效 |
| P2 | 挖掘、攻击、交互/使用 | 三种意图各自生效且服务端校验距离与冷却 |
| P3 | 装配台方块与容器界面、配对流程、配方 | 组装 → 得到已配对头盔与机器人 → 可正常进入驾驶 |

P0 不依赖第 6 节任何待定项，先落地。

---

## 5. 已排除的方案

- **本体无敌**：违背 R6/R7 的设计张力，不做。
- **客户端直接改机器人坐标**：可被伪造，不做（见 D2）。
- **每个功能单独做界面**：违背 R3，全部挂转轮面板。
- **多头盔同时控制一台机器人**：R13 规定同一时刻只有一个驾驶员，公共频道也按"已被占用"拒绝。
- **把耐久做成 `EntityLivingBase` 的 health**：会被血魔法类模组当材料榨取，也会让读血量的模组把它判定成异常实体，已排除（见 D7）。
- **依赖原版目标 AI 自动打机器人**：原版只针对玩家，不注入就永远不会被怪主动打（见 D8）。

---

## 6. 待确认

| 编号 | 问题 | 影响 |
|---|---|---|
| Q1 | 装配台需要哪些零件（钢铁板/电路/传感器等），是否引入新材料 | P3 的配方与材料链 |
| Q2 | 机器人是否有独立物品栏 / 能量条（耐久已定，见 R16–R17） | 实体 NBT 与转轮面板状态显示 |
| Q3 | 频道号取值范围（建议 0–9999）、设置方式（数字输入界面 / 滚轮 / 命令） | 芯片物品与设置界面 |
| Q4 | 机器人能否跨维度控制（本体在下界、机器人在主世界） | 连接表的维度校验与 ticket 迁移 |
| Q5 | 是否新增"被生物重叠挤压"的自定义伤害规则（1.7.10 原生没有 cramming） | R19 的挤压判定与实现量 |
| Q6 | 同名公共频道被两人同时连接时的处理（先到先得 / 抢占 / 排队） | R13 与 D5 的拒绝提示 |
