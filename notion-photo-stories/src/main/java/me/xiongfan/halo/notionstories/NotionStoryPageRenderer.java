package me.xiongfan.halo.notionstories;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

@Component
public class NotionStoryPageRenderer {

    private static final String BASE_PATH = "/photo-stories";

    public String renderList(NotionStoryListPage page) {
        var title = NotionBlockRenderer.escape(page.properties().getPageTitle());
        StringBuilder body = new StringBuilder();
        body.append("<section class=\"nps-heading\">")
            .append("<a class=\"nps-back\" href=\"/\">返回首页</a>")
            .append("<h1>").append(title).append("</h1>");
        if (!page.configMissing()) {
            body.append("<p class=\"nps-count\">共 ")
                .append(page.stories().size())
                .append(" 篇</p>");
        }
        body.append("</section>");

        if (page.configMissing()) {
            body.append("<section class=\"nps-empty\">")
                .append("<h2>插件尚未完成配置</h2>")
                .append("<p>请在 Halo 后台的插件设置中填写 Notion Integration Token 和数据库 ID。</p>")
                .append("</section>");
            return shell(page.properties().getPageTitle(), body.toString());
        }

        if (page.stories().isEmpty()) {
            body.append("<section class=\"nps-empty\">")
                .append("<h2>还没有可展示的图片故事</h2>")
                .append("<p>请确认 Notion 数据库里的发布字段已经勾选，且 Integration 已连接到该数据库。</p>")
                .append("</section>");
            return shell(page.properties().getPageTitle(), body.toString());
        }

        body.append("<section class=\"nps-grid\">");
        for (NotionStory story : page.stories()) {
            body.append(renderStoryCard(story));
        }
        body.append("</section>");
        return shell(page.properties().getPageTitle(), body.toString());
    }

    public String renderDetail(NotionStoryDetailPage page) {
        var story = page.story();
        StringBuilder body = new StringBuilder();
        body.append("<article class=\"nps-article\">")
            .append("<a class=\"nps-back\" href=\"").append(BASE_PATH).append("\">返回图片故事</a>")
            .append("<header class=\"nps-article-header\">")
            .append(date(story))
            .append("<h1>").append(NotionBlockRenderer.escape(story.title())).append("</h1>");
        if (StringUtils.hasText(story.summary())) {
            body.append("<p class=\"nps-summary\">")
                .append(NotionBlockRenderer.escape(story.summary()))
                .append("</p>");
        }
        body.append(tags(story.tags())).append("</header>");
        if (StringUtils.hasText(story.coverUrl())) {
            body.append("<figure class=\"nps-cover\"><img src=\"")
                .append(NotionBlockRenderer.escapeAttribute(story.coverUrl()))
                .append("\" alt=\"\" loading=\"eager\"></figure>");
        }
        body.append("<div class=\"nps-content\">")
            .append(story.contentHtml())
            .append("</div>")
            .append("</article>");
        return shell(story.title(), body.toString());
    }

    public String renderError(String title, String message) {
        String body = "<section class=\"nps-heading\"><a class=\"nps-back\" href=\""
            + BASE_PATH + "\">返回图片故事</a><h1>"
            + NotionBlockRenderer.escape(title)
            + "</h1></section><section class=\"nps-empty\"><p>"
            + NotionBlockRenderer.escape(message)
            + "</p></section>";
        return shell(title, body);
    }

    private String renderStoryCard(NotionStory story) {
        String href = BASE_PATH + "/" + UriUtils.encodePathSegment(story.slug(), "UTF-8");
        StringBuilder card = new StringBuilder();
        card.append("<article class=\"nps-card\">")
            .append("<a class=\"nps-card-link\" href=\"").append(href).append("\">");
        if (StringUtils.hasText(story.coverUrl())) {
            card.append("<span class=\"nps-thumb\"><img src=\"")
                .append(NotionBlockRenderer.escapeAttribute(story.coverUrl()))
                .append("\" alt=\"\" loading=\"lazy\"></span>");
        } else {
            card.append("<span class=\"nps-thumb nps-thumb-empty\"></span>");
        }
        card.append("<span class=\"nps-card-body\">")
            .append(date(story))
            .append("<strong>").append(NotionBlockRenderer.escape(story.title())).append("</strong>");
        if (StringUtils.hasText(story.summary())) {
            card.append("<span class=\"nps-card-summary\">")
                .append(NotionBlockRenderer.escape(story.summary()))
                .append("</span>");
        }
        card.append(tags(story.tags()))
            .append("</span></a></article>");
        return card.toString();
    }

    private static String date(NotionStory story) {
        if (!StringUtils.hasText(story.date())) {
            return "";
        }
        return "<time datetime=\"" + NotionBlockRenderer.escapeAttribute(story.date()) + "\">"
            + NotionBlockRenderer.escape(story.date()) + "</time>";
    }

    private static String tags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder("<span class=\"nps-tags\">");
        for (String tag : tags) {
            html.append("<span>").append(NotionBlockRenderer.escape(tag)).append("</span>");
        }
        html.append("</span>");
        return html.toString();
    }

    private static String shell(String title, String body) {
        return "<!doctype html><html lang=\"zh-CN\"><head>"
            + "<meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
            + "<title>" + NotionBlockRenderer.escape(title) + "</title>"
            + "<style>" + css() + "</style>"
            + "</head><body><main class=\"nps-wrap\">" + body + "</main></body></html>";
    }

    private static String css() {
        return """
            :root{color-scheme:light;--nps-text:#171717;--nps-muted:#6b7280;--nps-line:#e5e7eb;--nps-bg:#fafafa;--nps-card:#ffffff;--nps-accent:#0f766e}
            *{box-sizing:border-box}
            body{margin:0;background:var(--nps-bg);color:var(--nps-text);font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"Helvetica Neue",Arial,"Noto Sans SC",sans-serif;line-height:1.7;letter-spacing:0}
            a{color:inherit}
            img{display:block;max-width:100%}
            .nps-wrap{width:min(1080px,calc(100% - 32px));margin:0 auto;padding:44px 0 72px}
            .nps-back{display:inline-flex;align-items:center;margin-bottom:18px;color:var(--nps-muted);font-size:14px;text-decoration:none}
            .nps-back:hover{color:var(--nps-accent)}
            .nps-heading{margin-bottom:28px}
            .nps-heading h1,.nps-article-header h1{margin:0;font-size:clamp(30px,4vw,48px);line-height:1.18;font-weight:760;letter-spacing:0}
            .nps-count{margin:10px 0 0;color:var(--nps-muted)}
            .nps-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:18px}
            .nps-card{background:var(--nps-card);border:1px solid var(--nps-line);border-radius:8px;overflow:hidden}
            .nps-card-link{display:flex;flex-direction:column;min-height:100%;text-decoration:none}
            .nps-card-link:hover strong{color:var(--nps-accent)}
            .nps-thumb{display:block;aspect-ratio:16/10;background:#eef2f7;overflow:hidden}
            .nps-thumb img{width:100%;height:100%;object-fit:cover}
            .nps-thumb-empty{background:linear-gradient(135deg,#edf2f7,#f8fafc)}
            .nps-card-body{display:flex;flex-direction:column;gap:8px;padding:16px}
            .nps-card strong{font-size:19px;line-height:1.35}
            time{font-size:13px;color:var(--nps-muted)}
            .nps-card-summary,.nps-summary{color:#4b5563}
            .nps-card-summary{display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden;font-size:14px}
            .nps-tags{display:flex;flex-wrap:wrap;gap:6px;margin-top:2px}
            .nps-tags span{border:1px solid var(--nps-line);border-radius:999px;padding:2px 8px;color:#4b5563;font-size:12px;background:#fff}
            .nps-empty{padding:28px;background:#fff;border:1px solid var(--nps-line);border-radius:8px;color:#4b5563}
            .nps-empty h2{margin:0 0 8px;font-size:20px;color:var(--nps-text)}
            .nps-empty p{margin:0}
            .nps-article{width:min(820px,100%);margin:0 auto}
            .nps-article-header{margin-bottom:22px}
            .nps-article-header h1{margin-top:8px}
            .nps-summary{font-size:18px;margin:14px 0 0}
            .nps-cover{margin:28px 0;border-radius:8px;overflow:hidden;background:#eef2f7}
            .nps-cover img{width:100%;max-height:560px;object-fit:cover}
            .nps-content{font-size:17px}
            .nps-content p{margin:1em 0}
            .nps-content h1,.nps-content h2,.nps-content h3{line-height:1.3;margin:1.8em 0 .55em}
            .nps-content h1{font-size:32px}
            .nps-content h2{font-size:26px}
            .nps-content h3{font-size:21px}
            .nps-content blockquote{margin:1.2em 0;padding:2px 0 2px 18px;border-left:3px solid var(--nps-accent);color:#374151}
            .nps-content figure{margin:1.4em 0}
            .nps-content figure img{border-radius:8px}
            .nps-content figcaption{margin-top:8px;color:var(--nps-muted);font-size:13px;text-align:center}
            .nps-content pre{overflow:auto;border-radius:8px;background:#111827;color:#f9fafb;padding:16px}
            .nps-content code{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace}
            .nps-content :not(pre)>code{border-radius:5px;background:#eef2f7;padding:1px 5px;color:#0f172a}
            .nps-content hr{border:0;border-top:1px solid var(--nps-line);margin:28px 0}
            .nps-content ul,.nps-content ol{padding-left:1.35em}
            .nps-callout{display:flex;gap:10px;margin:1.2em 0;padding:14px;border:1px solid var(--nps-line);border-radius:8px;background:#fff}
            .nps-callout-icon{width:24px;flex:0 0 auto}
            .nps-todo{display:flex;gap:10px;align-items:flex-start;margin:.65em 0}
            .nps-link-card{border:1px solid var(--nps-line);border-radius:8px;padding:12px;background:#fff;word-break:break-all}
            @media (max-width:640px){.nps-wrap{width:min(100% - 24px,1080px);padding-top:28px}.nps-grid{grid-template-columns:1fr}.nps-heading h1,.nps-article-header h1{font-size:30px}.nps-summary{font-size:16px}.nps-content{font-size:16px}}
            """;
    }
}
