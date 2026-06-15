# RealPlace Changelog / 真实置物更新日志

This file records official release notes for RealPlace. Future releases should be added above older releases and should keep the same bilingual structure.

本文件用于记录真实置物的正式发布更新日志。后续版本应追加在旧版本上方，并沿用同一套中英文双语结构。

## Format For Future Releases / 后续版本记录格式

Each release should include:

- Version, release date, Minecraft version, mod loader, license, and release type.
- A Chinese changelog first.
- An English changelog with the same categories and matching meaning.
- Categories in this order when applicable: `Added`, `Changed`, `Fixed`, `Compatibility`.
- If a category has no meaningful entry for a release, omit that category instead of leaving an empty section.
- Write release entries in objective, player-facing language. Prefer result-oriented phrasing and avoid internal implementation names unless they are needed for external compatibility.

每个版本应包含：

- 版本号、发布日期、Minecraft 版本、加载器、License 和 Release Type。
- 先写中文更新日志。
- 再写英文更新日志，分类和含义应与中文对应。
- 分类建议按以下顺序使用：`新增内容`、`调整内容`、`修复内容`、`兼容性`。
- 如果某个版本没有对应内容，应省略该分类，不要保留空标题。
- 更新描述应保持客观、面向玩家和结果导向；除非涉及外部兼容，否则避免写入内部实现细节。

---

## 1.0.1-1.20.1forge - 2026-06-15

- Minecraft: 1.20.1
- Mod Loader: Forge 47.4.20
- License: Apache-2.0
- Release Type: Port release

### 中文更新日志

#### 调整内容

- 移植到 Minecraft 1.20.1 Forge，并使用带版本后缀的打包版本号。
- 调整构建配置，使该版本分支可以单独调整版本号并单独打包。

#### 兼容性

- 保持 Jade 可选联动；RealPlace 仅在编译期使用 Jade API，发布包不内置 Jade。

### English Changelog

#### Changed

- Ported to Minecraft 1.20.1 Forge and adopted package versions with a version-specific suffix.
- Adjusted the build configuration so this version branch can bump versions and package independently.

#### Compatibility

- Kept Jade optional compatibility; RealPlace uses the Jade API only at compile time and does not bundle Jade in distribution jars.

---

## 1.0.0 - 2026-06-10

- Minecraft: 1.21.1
- Mod Loader: NeoForge 21.1.228
- License: Apache-2.0
- Release Type: Initial RealPlace release

### 中文更新日志

#### 新增内容

- 新增真实置物系统，可将手持物品放置到世界中，并重新拾取已放置的真实置物。
- 新增放置模式操作与界面提示，支持水平旋转、竖直旋转、缩放和模型切换。
- 新增放置冲突处理，避免真实置物与方块或已有真实置物重叠。
- 新增可选 Jade 联动，悬浮查看真实置物时可显示对应物品提示。

#### 修复内容

- 修复部分模型显示异常。
- 修复平面物品的附魔发光显示异常。

### English Changelog

#### Added

- Added the RealPlace system for placing held items into the world and picking placed objects back up.
- Added placement mode controls and on-screen hints for horizontal rotation, vertical rotation, scaling, and model switching.
- Added placement conflict handling to prevent RealPlace objects from overlapping blocks or existing RealPlace objects.
- Added optional Jade compatibility so hovering a RealPlace object can show its item tooltip.

#### Fixed

- Fixed display issues for some models.
- Fixed enchanted glint rendering for flat item models.
