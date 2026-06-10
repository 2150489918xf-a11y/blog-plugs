# Notion 图片故事

一个 Halo 2 插件，用 Notion 数据库作为图片故事/回忆内容源，并在站点中生成公开页面。

## 默认页面

- 列表页：`/photo-stories`
- 详情页：`/photo-stories/{slug}`

## Notion 数据库字段

默认匹配以下字段：

| 字段 | Notion 类型 | 说明 |
| --- | --- | --- |
| 标题 | Title | 故事标题 |
| Slug | Text/Rich text | 详情页路径片段 |
| 日期 | Date | 排序和展示时间 |
| 状态 | Checkbox | 勾选后展示到网站 |
| 摘要 | Text/Rich text | 列表页摘要 |
| 标签 | Multi-select | 列表页标签 |
| 封面图 | Files & media 或 URL | 可选，留空则读取页面封面 |

## 配置

在 Halo 后台插件设置中填写：

- Notion Integration Token
- Notion 数据库 ID
- 字段映射
- 缓存时间
