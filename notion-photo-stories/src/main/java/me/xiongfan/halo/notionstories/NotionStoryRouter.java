package me.xiongfan.halo.notionstories;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.HEAD;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class NotionStoryRouter {

    private final NotionStoryService storyService;
    private final NotionStoryPageRenderer renderer;

    public NotionStoryRouter(NotionStoryService storyService, NotionStoryPageRenderer renderer) {
        this.storyService = storyService;
        this.renderer = renderer;
    }

    @Bean
    RouterFunction<ServerResponse> notionPhotoStoriesRoutes() {
        return route(GET("/photo-stories"), listHandler())
            .andRoute(HEAD("/photo-stories"), headHandler())
            .andRoute(GET("/photo-stories/"), redirectToListHandler())
            .andRoute(GET("/photo-stories/{slug}"), detailHandler())
            .andRoute(HEAD("/photo-stories/{slug}"), headHandler());
    }

    private HandlerFunction<ServerResponse> listHandler() {
        return request -> storyService.listPage()
            .flatMap(page -> html(renderer.renderList(page)))
            .onErrorResume(this::renderFailure);
    }

    private HandlerFunction<ServerResponse> detailHandler() {
        return request -> {
            String slug = request.pathVariable("slug");
            return storyService.detailPage(slug)
                .flatMap(page -> html(renderer.renderDetail(page)))
                .onErrorResume(NotionStoryNotFoundException.class,
                    error -> html(HttpStatus.NOT_FOUND,
                        renderer.renderError("没有找到这篇图片故事", "请检查访问地址，或确认这篇内容仍然处于公开状态。")))
                .onErrorResume(NotionConfigMissingException.class,
                    error -> html(HttpStatus.SERVICE_UNAVAILABLE,
                        renderer.renderError("插件尚未完成配置", "请先在 Halo 后台配置 Notion Token 和数据库 ID。")))
                .onErrorResume(this::renderFailure);
        };
    }

    private HandlerFunction<ServerResponse> redirectToListHandler() {
        return request -> ServerResponse.status(HttpStatus.MOVED_PERMANENTLY)
            .location(URI.create("/photo-stories"))
            .build();
    }

    private HandlerFunction<ServerResponse> headHandler() {
        return request -> ServerResponse.ok().build();
    }

    private Mono<ServerResponse> renderFailure(Throwable error) {
        String message = error instanceof NotionApiException
            ? "Notion API 请求失败，请检查 Token、数据库权限、字段映射和发布字段类型。"
            : "页面加载失败，请稍后再试。";
        HttpStatus status = error instanceof NotionApiException
            ? HttpStatus.BAD_GATEWAY
            : HttpStatus.INTERNAL_SERVER_ERROR;
        return html(status, renderer.renderError("图片故事加载失败", message));
    }

    private Mono<ServerResponse> html(String html) {
        return html(HttpStatus.OK, html);
    }

    private Mono<ServerResponse> html(HttpStatus status, String html) {
        return ServerResponse.status(status)
            .contentType(MediaType.TEXT_HTML)
            .bodyValue(html);
    }
}
