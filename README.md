# 真实置物 (RealPlace)

## 简介
**真实置物**允许你把手中的物品直接放进世界，作为可见的放置物来摆放。进入放置模式后，可以对预览物体进行水平旋转、竖直旋转、缩放，并在可用模型之间切换；已经放下的置物也可以重新拾取。

安装了 Jade 时，悬浮查看真实置物会显示对应物品提示。

## 特性
- 将手持物品放置为世界中的放置物。
- 在放置模式下调整旋转、缩放和模型切换。
- 可拾取已放置的放置物。
- 不会与方块或已有放置物重叠放置。
- 放置物拥有真实碰撞箱。
- 提供可选 Jade 联动悬浮提示。

## 多版本维护与打包
- 本项目采用分支维护多版本。每个 Minecraft / Mod Loader 组合使用独立分支，例如 `1.20.1-forge` 与 `1.21.1-neoforge`。
- 每个版本分支独立维护 `gradle.properties` 中的 `mod_version_base`、`minecraft_version` 与 `loader_label`。
- 完整版本号由构建脚本生成为 `mod_version_base-minecraft_versionloader_label`，例如 `1.0.1-1.20.1forge`。
- 在当前版本分支运行 `.\gradlew.bat clean build` 即可单独打包该版本，产物位于 `build/libs/`。
- 调整版本号时只修改当前分支的 `mod_version_base`，避免影响其它版本分支。

## Overview
**RealPlace** lets you place the item in your hand directly into the world as a placed object. After entering placement mode, you can rotate the preview horizontally and vertically, scale it, and switch between available models. Objects that have already been placed can also be picked up again.

With Jade installed, hovering over a RealPlace object shows the corresponding item tooltip.

## Features
- Place the held item into the world as a placed object.
- Adjust rotation, scale, and model switching in placement mode.
- Pick up objects that have already been placed.
- Prevent placement overlap with blocks or existing placed objects.
- Give placed objects real collision boxes.
- Provide optional compatibility with Jade hover tooltips.

## Multi-Version Maintenance and Packaging
- This project uses branch-based multi-version maintenance. Each Minecraft / Mod Loader combination has its own branch, such as `1.20.1-forge` and `1.21.1-neoforge`.
- Each version branch maintains its own `mod_version_base`, `minecraft_version`, and `loader_label` in `gradle.properties`.
- The build script generates the full version as `mod_version_base-minecraft_versionloader_label`, for example `1.0.1-1.20.1forge`.
- Run `.\gradlew.bat clean build` on the current version branch to package that version separately. Artifacts are written to `build/libs/`.
- When bumping versions, change only `mod_version_base` on the current branch so other version branches remain independent.
