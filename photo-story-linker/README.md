# 日记故事

一个 Halo 2 插件，用 `SinglePage` 管理日记内容，并可把图库图片绑定到对应日记或已有文章。

## 功能

- 创建 Halo 单页面日记草稿，创建后自动跳转到 Halo 原生单页面编辑器。
- 日记默认不会进入主题首页文章列表，避免和技术文章混在一起。
- 可将图库图片绑定到日记页面，图库悬停层会展示日记预览并跳转到对应页面。
- 兼容旧版绑定到 `Post` 的图片故事数据。
- 支持多张图片指向同一篇日记。
- 提供 `/journals` 公开日记列表，只展示已发布、公开、启用且允许展示的日记。
- 支持 `PUBLIC`、`INTERNAL`、`PRIVATE` 可见性；首版公开 API 只暴露公开日记。
- 关闭插件不会删除图片、文章或单页面，只会停用插件提供的绑定、列表和悬停增强。

## 依赖

- Halo 2.24+
- Java 21+
- Node.js 18+
- pnpm
- 如需图库图片绑定，需要同时启用 Halo 官方图库插件 `plugin-photos`

## 开发

```bash
./gradlew haloServer
```

Console UI 开发：

```bash
cd ui
pnpm install
pnpm dev
```

## 构建

```bash
./gradlew build
```

插件 Jar 会生成在 `build/libs`。

## License

[GPL-3.0](./LICENSE)
