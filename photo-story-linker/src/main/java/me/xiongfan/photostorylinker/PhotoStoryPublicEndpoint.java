package me.xiongfan.photostorylinker;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.extension.ReactiveExtensionClient;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Component
public class PhotoStoryPublicEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;

    public PhotoStoryPublicEndpoint(ReactiveExtensionClient client) {
        this.client = client;
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return route(GET("/bindings"), request -> listPublicBindings())
            .andRoute(GET("/assets/story.js"),
                request -> asset("assets/story.js", "application/javascript"))
            .andRoute(GET("/assets/story.css"),
                request -> asset("assets/story.css", "text/css"));
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion("api." + PhotoStoryLinker.GROUP, PhotoStoryLinker.VERSION);
    }

    private Mono<ServerResponse> listPublicBindings() {
        return client.list(PhotoStoryBinding.class, this::isEnabledBinding, byCreationTime())
            .flatMap(binding -> {
                var spec = binding.getSpec();
                if (spec == null || StringUtils.isBlank(spec.getPostName())) {
                    return Mono.empty();
                }
                return client.fetch(Post.class, spec.getPostName())
                    .filter(this::isPublicVisiblePost)
                    .map(post -> PublicBinding.from(binding, post));
            })
            .collectList()
            .flatMap(items -> ServerResponse.ok().bodyValue(items));
    }

    private Mono<ServerResponse> asset(String path, String contentType) {
        try {
            var resource = new ClassPathResource(path, PhotoStoryPublicEndpoint.class.getClassLoader());
            var body = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            return ServerResponse.ok()
                .header("Content-Type", contentType + "; charset=utf-8")
                .bodyValue(body);
        } catch (Exception e) {
            return ServerResponse.notFound().build();
        }
    }

    private boolean isEnabledBinding(PhotoStoryBinding binding) {
        var spec = binding.getSpec();
        return spec != null
            && Boolean.TRUE.equals(spec.getEnabled())
            && StringUtils.isNotBlank(spec.getPhotoName())
            && StringUtils.isNotBlank(spec.getPostName());
    }

    private boolean isPublicVisiblePost(Post post) {
        var spec = post.getSpec();
        return spec != null
            && Boolean.TRUE.equals(spec.getPublish())
            && !Boolean.TRUE.equals(spec.getDeleted())
            && Post.isPublic(spec)
            && post.getStatus() != null
            && StringUtils.isNotBlank(post.getStatus().getPermalink());
    }

    private Comparator<PhotoStoryBinding> byCreationTime() {
        return Comparator.comparing(binding -> {
            MetadataOperator metadata = binding.getMetadata();
            return metadata == null ? null : metadata.getCreationTimestamp();
        }, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    public record PublicBinding(
        String bindingName,
        String photoName,
        String postName,
        String postUrl,
        String title,
        String teaser,
        String badgeText,
        String openMode
    ) {
        static PublicBinding from(PhotoStoryBinding binding, Post post) {
            var bindingSpec = binding.getSpec();
            var postStatus = post.getStatus();
            var postSpec = post.getSpec();
            var fallbackTeaser = postStatus == null ? "" : postStatus.getExcerpt();
            var teaser = StringUtils.defaultIfBlank(bindingSpec.getTeaser(), fallbackTeaser);
            var badgeText = StringUtils.defaultIfBlank(bindingSpec.getBadgeText(), "Read story");
            return new PublicBinding(
                binding.getMetadata().getName(),
                bindingSpec.getPhotoName(),
                bindingSpec.getPostName(),
                postStatus.getPermalink(),
                postSpec.getTitle(),
                teaser,
                badgeText,
                Objects.toString(bindingSpec.getOpenMode(), PhotoStoryBinding.OpenMode.SAME_TAB.name())
            );
        }
    }
}
