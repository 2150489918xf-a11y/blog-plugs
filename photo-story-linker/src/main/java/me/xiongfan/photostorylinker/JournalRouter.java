package me.xiongfan.photostorylinker;

import java.util.ArrayList;
import java.util.Comparator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.content.SinglePage;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.extension.ReactiveExtensionClient;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.HEAD;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Component
public class JournalRouter {

    private final ReactiveExtensionClient client;

    public JournalRouter(ReactiveExtensionClient client) {
        this.client = client;
    }

    @Bean
    RouterFunction<ServerResponse> journalRouterFunction() {
        return route(GET("/journals"), this::journals)
            .andRoute(HEAD("/journals"), this::journals);
    }

    private Mono<ServerResponse> journals(ServerRequest request) {
        return client.list(JournalEntry.class, this::isVisibleJournalEntry, byCreationTime())
            .flatMap(entry -> client.fetch(SinglePage.class, entry.getSpec().getSinglePageName())
                .filter(this::isPublicVisibleSinglePage)
                .map(singlePage -> PhotoStoryPublicEndpoint.PublicJournal.from(entry, singlePage)))
            .collectList()
            .map(items -> {
                var sorted = new ArrayList<>(items);
                sorted.sort(Comparator.comparing(PhotoStoryPublicEndpoint.PublicJournal::journalDate,
                    Comparator.nullsLast(Comparator.reverseOrder())));
                return sorted;
            })
            .map(this::renderHtml)
            .flatMap(html -> ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML)
                .bodyValue(html));
    }

    private boolean isVisibleJournalEntry(JournalEntry entry) {
        var spec = entry.getSpec();
        return spec != null
            && Boolean.TRUE.equals(spec.getEnabled())
            && Boolean.TRUE.equals(spec.getShowInJournalList())
            && StringUtils.isNotBlank(spec.getSinglePageName());
    }

    private boolean isPublicVisibleSinglePage(SinglePage singlePage) {
        var spec = singlePage.getSpec();
        return spec != null
            && Boolean.TRUE.equals(spec.getPublish())
            && !Boolean.TRUE.equals(spec.getDeleted())
            && (spec.getVisible() == null || spec.getVisible() == Post.VisibleEnum.PUBLIC)
            && singlePage.getStatus() != null
            && StringUtils.isNotBlank(singlePage.getStatus().getPermalink());
    }

    private Comparator<JournalEntry> byCreationTime() {
        return Comparator.comparing(entry -> {
            MetadataOperator metadata = entry.getMetadata();
            return metadata == null ? null : metadata.getCreationTimestamp();
        }, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private String renderHtml(Iterable<PhotoStoryPublicEndpoint.PublicJournal> journals) {
        var cards = new StringBuilder();
        for (var journal : journals) {
            cards.append("""
                <article class="journal-card">
                  %s
                  <div class="journal-card-body">
                    <time>%s</time>
                    <h2><a href="%s">%s</a></h2>
                    %s
                    %s
                  </div>
                </article>
                """.formatted(
                    image(journal.cover(), journal.title()),
                    escape(StringUtils.defaultIfBlank(journal.journalDate(), "")),
                    escape(journal.url()),
                    escape(journal.title()),
                    StringUtils.isBlank(journal.teaser())
                        ? "" : "<p>" + escape(journal.teaser()) + "</p>",
                    meta(journal)
                ));
        }
        if (cards.isEmpty()) {
            cards.append("""
                <div class="empty">
                  <h2>还没有公开日记</h2>
                  <p>发布公开日记后，它们会显示在这里。</p>
                </div>
                """);
        }
        return """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>日记</title>
              <style>
                :root { color-scheme: light; }
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                  color: #111827;
                  background: #f8fafc;
                }
                .journal-shell {
                  width: min(68.75rem, calc(100% - 2rem));
                  margin: 0 auto;
                  padding: 2rem 0 4rem;
                }
                .journal-top {
                  display: flex;
                  flex-direction: column;
                  gap: 1rem;
                  margin-bottom: 1.75rem;
                }
                .journal-top h1 {
                  margin: 0;
                  font-size: 2.5rem;
                  line-height: 1.05;
                }
                .journal-top p {
                  margin: 0.625rem 0 0;
                  max-width: 52ch;
                  color: #64748b;
                  line-height: 1.7;
                }
                .home-link {
                  min-height: 2.75rem;
                  display: inline-flex;
                  align-items: center;
                  color: #111827;
                  text-decoration: none;
                  font-weight: 700;
                }
                .journal-grid {
                  display: grid;
                  grid-template-columns: 1fr;
                  gap: 1rem;
                }
                .journal-card {
                  overflow: hidden;
                  border: 1px solid #e5e7eb;
                  border-radius: 0.5rem;
                  background: #fff;
                }
                .journal-card img {
                  width: 100%;
                  aspect-ratio: 16 / 10;
                  object-fit: cover;
                  display: block;
                  background: #e5e7eb;
                }
                .journal-card-body { padding: 1.125rem; }
                time {
                  color: #64748b;
                  font-size: 0.875rem;
                }
                h2 {
                  margin: 0.5rem 0 0.625rem;
                  font-size: 1.25rem;
                  line-height: 1.35;
                }
                h2 a {
                  color: inherit;
                  text-decoration: none;
                }
                .journal-card p {
                  margin: 0;
                  color: #475569;
                  line-height: 1.7;
                }
                .journal-meta {
                  display: flex;
                  flex-wrap: wrap;
                  gap: 0.5rem;
                  margin-top: 0.875rem;
                  color: #475569;
                  font-size: 0.875rem;
                }
                .journal-meta span {
                  padding: 0.25rem 0.5rem;
                  border-radius: 999px;
                  background: #f1f5f9;
                }
                .empty {
                  padding: 2rem 1rem;
                  border: 1px dashed #cbd5e1;
                  border-radius: 0.5rem;
                  text-align: center;
                  background: #fff;
                }
                .empty h2 { margin-top: 0; }
                .empty p { margin-bottom: 0; color: #64748b; }
                @media (min-width: 640px) {
                  .journal-grid {
                    grid-template-columns: repeat(2, minmax(0, 1fr));
                  }
                }
                @media (min-width: 768px) {
                  .journal-shell {
                    width: min(68.75rem, calc(100% - 3rem));
                    padding-top: 3rem;
                  }
                  .journal-top {
                    flex-direction: row;
                    justify-content: space-between;
                    align-items: end;
                  }
                  .journal-top h1 { font-size: 4rem; }
                }
                @media (min-width: 1024px) {
                  .journal-grid {
                    grid-template-columns: repeat(3, minmax(0, 1fr));
                  }
                }
              </style>
            </head>
            <body>
              <main class="journal-shell">
                <header class="journal-top">
                  <div>
                    <h1>日记</h1>
                    <p>这里收纳照片背后的日常记录、故事和片段。</p>
                  </div>
                  <a class="home-link" href="/">返回首页</a>
                </header>
                <section class="journal-grid">
                  {{cards}}
                </section>
              </main>
            </body>
            </html>
            """.replace("{{cards}}", cards.toString());
    }

    private static String image(String cover, String title) {
        if (StringUtils.isBlank(cover)) {
            return "";
        }
        return "<img src=\"" + escape(cover) + "\" alt=\"" + escape(title) + "\" loading=\"lazy\">";
    }

    private static String meta(PhotoStoryPublicEndpoint.PublicJournal journal) {
        var meta = new StringBuilder();
        if (StringUtils.isNotBlank(journal.mood())) {
            meta.append("<span>").append(escape(journal.mood())).append("</span>");
        }
        if (StringUtils.isNotBlank(journal.location())) {
            meta.append("<span>").append(escape(journal.location())).append("</span>");
        }
        if (StringUtils.isNotBlank(journal.weather())) {
            meta.append("<span>").append(escape(journal.weather())).append("</span>");
        }
        if (meta.isEmpty()) {
            return "";
        }
        return "<div class=\"journal-meta\">" + meta + "</div>";
    }

    private static String escape(String value) {
        return String.valueOf(value == null ? "" : value)
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#039;");
    }
}
