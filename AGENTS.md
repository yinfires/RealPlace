# RealPlace Workspace Notes

## 沟通偏好

- 除非用户明确要求使用其它语言，否则在此工作区默认用简体中文回复用户。

## Highest Priority

- 根目录 `NOTICE` 必须同时使用英文和简体中文。
- `README.md` 与 `CHANGELOG.md` 默认保持中英双语。
- 涉及 Jade 的文案统一写为“可选联动 / optional compatibility”；RealPlace 仅在编译期使用 Jade API，发布包不内置 Jade。
- 所有玩家可见文本必须使用翻译键，不直接写死英文或中文 UI 文案。

## Working Rhythm

- 避免长时间静默阅读代码。快速定位后，先说明相关文件和计划改动。
- 优先做小而完整的补丁，再运行最小必要验证。
- 如果探索超过几次工具调用，先给出简短进度更新。
- 当中文文本会影响判断时，先用显式 UTF-8 读取相关文件或行范围，再判断是否真的乱码。
- 在这个 Windows PowerShell 工作区里，`rg`、`git` 和 `node` 可能不在 `PATH`。如果命令提示 `not recognized`，立即改用 `Get-ChildItem`、`Select-String`、`[System.IO.File]` UTF-8 读取、`ConvertFrom-Json`、`gradlew.bat` 等可用替代方案。
- 保持验证输出精简，只保留关键结论或关键报错行。

## Release Notes

- 编写更新日志前先检查 `gradle.properties` 中的当前版本。
- 更新日志只根据该版本边界内的提交、代码和资源差异编写，不从长期累积文档直接改写。
- 如果用户明确排除某个主题，不要在当前更新日志里换个说法重新写回去。
- 更新日志默认先写中文，再写英文；同一版本中中英文分类和含义必须对应。
- 每个版本默认包含版本号、发布日期、Minecraft 版本、Mod Loader、License 和 Release Type。
- 分类默认按 `新增内容` / `Added`、`调整内容` / `Changed`、`修复内容` / `Fixed`、`兼容性` / `Compatibility` 顺序使用；没有对应内容时省略空分类。
- 发布说明应保持客观、面向玩家和结果导向；除非涉及外部兼容，否则避免写内部类名、网络载荷名或其他实现细节。
- 发布说明不写 `Release Summary` / `发布摘要`。

## Language

- 所有玩家可见字符串必须使用翻译键。
- 不要对名称、提示、按钮、说明或 HUD 文案直接使用 `Component.literal(...)` 或写死英文/中文文本。
- 修改语言文件时保持 `en_us.json` 与 `zh_cn.json` 同步；同一改动内删除已无有效引用的废弃键。
- 内部 NBT 键、注册表 ID、payload ID、资源路径和代码注释不属于语言内容，应保持稳定的实现字符串。
