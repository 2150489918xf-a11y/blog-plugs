# Notion Stories Service

轻量的 Notion 图片故事展示服务：

- 列表页读取 Notion 数据库。
- 详情页使用 `react-notion-x` 渲染公开 Notion 页面。
- Halo 主站只需要放一个外链入口到 `https://stories.xiongfan.me`。

## 本地开发

```bash
cp .env.example .env
pnpm install
pnpm dev
```

访问：

```text
http://localhost:3010
```

## 环境变量

必须配置：

```env
NOTION_TOKEN=secret_xxx
NOTION_DATABASE_ID=37bbedc8681180199bbce653a8bf2325
SITE_URL=https://stories.xiongfan.me
```

默认字段：

| 环境变量 | 默认值 | Notion 类型 |
| --- | --- | --- |
| `NOTION_TITLE_PROPERTY` | `标题` | Title |
| `NOTION_SLUG_PROPERTY` | `Slug` | Rich text |
| `NOTION_DATE_PROPERTY` | `日期` | Date |
| `NOTION_PUBLISHED_PROPERTY` | `状态` | Checkbox |
| `NOTION_SUMMARY_PROPERTY` | `摘要` | Rich text |
| `NOTION_TAGS_PROPERTY` | `标签` | Multi-select |
| `NOTION_COVER_PROPERTY` | `封面图` | Files & media 或 URL |

## Notion 页面要求

`react-notion-x` 最适合渲染公开分享的 Notion 页面。建议图片故事数据库和文章页面开启公开访问，数据库列表仍通过服务端 `NOTION_TOKEN` 查询。

如果页面没有公开，详情页可能无法渲染。

## 服务器部署

```bash
mkdir -p /opt/notion-stories
cd /opt/notion-stories
git clone <repo-url> .
cp .env.example .env
docker compose up -d --build
```

反代：

```nginx
server {
    listen 80;
    server_name stories.xiongfan.me;

    location / {
        proxy_pass http://127.0.0.1:3010;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## 迁移服务器

服务本身不保存正文数据。迁移时只需要：

1. 新服务器安装 Docker 和 Docker Compose。
2. 拉取项目代码。
3. 拷贝 `.env`。
4. DNS 指向新服务器。
5. 执行 `docker compose up -d --build`。

文章、图片和视频仍保存在 Notion。
