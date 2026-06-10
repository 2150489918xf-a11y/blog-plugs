package me.xiongfan.halo.notionstories;

import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class NotionClient {

    private static final String NOTION_VERSION = "2022-06-28";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient;

    public NotionClient() {
        this.webClient = WebClient.builder()
            .baseUrl("https://api.notion.com/v1")
            .defaultHeader("Notion-Version", NOTION_VERSION)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    public Mono<List<NotionStory>> listStories(NotionStoryProperties properties) {
        return queryDatabase(properties, buildListFilter(properties))
            .map(page -> parseStory(properties, page))
            .filter(story -> StringUtils.hasText(story.slug()))
            .collectList();
    }

    public Mono<NotionStory> getStoryBySlug(NotionStoryProperties properties, String slug) {
        return queryDatabase(properties, buildDetailFilter(properties, slug))
            .next()
            .switchIfEmpty(Mono.error(new NotionStoryNotFoundException(slug)))
            .flatMap(page -> {
                var story = parseStory(properties, page);
                return fetchBlockChildren(properties, story.pageId())
                    .collectList()
                    .map(blocks -> story.withContentHtml(NotionBlockRenderer.render(blocks)));
            });
    }

    private Flux<JsonNode> queryDatabase(NotionStoryProperties properties, JsonNode filter) {
        return queryDatabasePage(properties, filter, null)
            .expand(response -> {
                if (!response.path("has_more").asBoolean(false)) {
                    return Mono.empty();
                }
                var cursor = response.path("next_cursor").asText(null);
                if (!StringUtils.hasText(cursor)) {
                    return Mono.empty();
                }
                return queryDatabasePage(properties, filter, cursor);
            })
            .flatMapIterable(response -> nodes(response.path("results")));
    }

    private Mono<JsonNode> queryDatabasePage(NotionStoryProperties properties, JsonNode filter,
        String startCursor) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("page_size", 100);
        if (filter != null && !filter.isEmpty()) {
            body.set("filter", filter);
        }
        if (StringUtils.hasText(properties.getDateProperty())) {
            ArrayNode sorts = body.putArray("sorts");
            ObjectNode sort = sorts.addObject();
            sort.put("property", properties.getDateProperty());
            sort.put("direction", "descending");
        }
        if (StringUtils.hasText(startCursor)) {
            body.put("start_cursor", startCursor);
        }
        return webClient.post()
            .uri("/databases/{databaseId}/query", properties.getDatabaseId())
            .headers(headers -> applyAuth(headers, properties))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .onStatus(status -> status.isError(), response ->
                response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(bodyText -> new NotionApiException(
                        "Notion database query failed: HTTP " + response.statusCode().value()
                            + " " + bodyText
                    ))
            )
            .bodyToMono(JsonNode.class);
    }

    private Flux<JsonNode> fetchBlockChildren(NotionStoryProperties properties, String blockId) {
        return fetchBlockChildrenPage(properties, blockId, null)
            .expand(response -> {
                if (!response.path("has_more").asBoolean(false)) {
                    return Mono.empty();
                }
                var cursor = response.path("next_cursor").asText(null);
                if (!StringUtils.hasText(cursor)) {
                    return Mono.empty();
                }
                return fetchBlockChildrenPage(properties, blockId, cursor);
            })
            .flatMapIterable(response -> nodes(response.path("results")));
    }

    private Mono<JsonNode> fetchBlockChildrenPage(NotionStoryProperties properties, String blockId,
        String startCursor) {
        return webClient.get()
            .uri(uriBuilder -> {
                var builder = uriBuilder.path("/blocks/{blockId}/children")
                    .queryParam("page_size", 100);
                if (StringUtils.hasText(startCursor)) {
                    builder.queryParam("start_cursor", startCursor);
                }
                return builder.build(blockId);
            })
            .headers(headers -> applyAuth(headers, properties))
            .retrieve()
            .onStatus(status -> status.isError(), response ->
                response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(bodyText -> new NotionApiException(
                        "Notion block query failed: HTTP " + response.statusCode().value()
                            + " " + bodyText
                    ))
            )
            .bodyToMono(JsonNode.class);
    }

    private ObjectNode buildListFilter(NotionStoryProperties properties) {
        ArrayNode filters = objectMapper.createArrayNode();
        addVisibilityFilters(properties, filters);
        return combineFilters(filters);
    }

    private ObjectNode buildDetailFilter(NotionStoryProperties properties, String slug) {
        ArrayNode filters = objectMapper.createArrayNode();
        addVisibilityFilters(properties, filters);
        filters.add(textEqualsFilter(properties.getSlugProperty(), "rich_text", slug));
        return combineFilters(filters);
    }

    private void addVisibilityFilters(NotionStoryProperties properties, ArrayNode filters) {
        if (StringUtils.hasText(properties.getPublishedProperty())) {
            filters.add(publishedFilter(properties));
        }
        if (properties.isFilterPermission() && StringUtils.hasText(properties.getPermissionProperty())) {
            filters.add(textEqualsFilter(
                properties.getPermissionProperty(),
                "select",
                properties.getPublicPermissionValue()
            ));
        }
    }

    private ObjectNode publishedFilter(NotionStoryProperties properties) {
        return switch (properties.getPublishedPropertyType()) {
            case "status" -> textEqualsFilter(
                properties.getPublishedProperty(),
                "status",
                properties.getPublishedValue()
            );
            case "select" -> textEqualsFilter(
                properties.getPublishedProperty(),
                "select",
                properties.getPublishedValue()
            );
            default -> checkboxEqualsFilter(properties.getPublishedProperty(), true);
        };
    }

    private ObjectNode checkboxEqualsFilter(String propertyName, boolean value) {
        ObjectNode filter = objectMapper.createObjectNode();
        filter.put("property", propertyName);
        filter.putObject("checkbox").put("equals", value);
        return filter;
    }

    private ObjectNode textEqualsFilter(String propertyName, String type, String value) {
        ObjectNode filter = objectMapper.createObjectNode();
        filter.put("property", propertyName);
        filter.putObject(type).put("equals", value);
        return filter;
    }

    private ObjectNode combineFilters(ArrayNode filters) {
        if (filters.size() == 0) {
            return objectMapper.createObjectNode();
        }
        if (filters.size() == 1) {
            return (ObjectNode) filters.get(0);
        }
        ObjectNode combined = objectMapper.createObjectNode();
        combined.set("and", filters);
        return combined;
    }

    private void applyAuth(HttpHeaders headers, NotionStoryProperties properties) {
        headers.setBearerAuth(properties.getNotionToken());
    }

    private NotionStory parseStory(NotionStoryProperties properties, JsonNode page) {
        var notionProperties = page.path("properties");
        String pageId = page.path("id").asText();
        String title = propertyText(notionProperties.path(properties.getTitleProperty()));
        String slug = propertyText(notionProperties.path(properties.getSlugProperty()));
        if (!StringUtils.hasText(slug)) {
            slug = fallbackSlug(pageId);
        }
        String date = propertyDate(notionProperties.path(properties.getDateProperty()));
        String summary = propertyText(notionProperties.path(properties.getSummaryProperty()));
        List<String> tags = propertyTags(notionProperties.path(properties.getTagsProperty()));
        String coverUrl = propertyFileUrl(notionProperties.path(properties.getCoverProperty()));
        if (!StringUtils.hasText(coverUrl)) {
            coverUrl = pageCoverUrl(page.path("cover"));
        }
        return new NotionStory(
            pageId,
            title,
            slug,
            date,
            summary,
            tags,
            coverUrl,
            page.path("url").asText(""),
            page.path("last_edited_time").asText(""),
            ""
        );
    }

    private static String propertyText(JsonNode property) {
        if (property == null || property.isMissingNode() || property.isNull()) {
            return "";
        }
        String type = property.path("type").asText();
        return switch (type) {
            case "title" -> richTextPlain(property.path("title"));
            case "rich_text" -> richTextPlain(property.path("rich_text"));
            case "url" -> property.path("url").asText("");
            case "email" -> property.path("email").asText("");
            case "phone_number" -> property.path("phone_number").asText("");
            case "number" -> property.path("number").isMissingNode() || property.path("number").isNull()
                ? "" : property.path("number").asText();
            case "select" -> property.path("select").path("name").asText("");
            case "status" -> property.path("status").path("name").asText("");
            case "formula" -> formulaText(property.path("formula"));
            default -> "";
        };
    }

    private static String propertyDate(JsonNode property) {
        if (property == null || property.isMissingNode() || property.isNull()) {
            return "";
        }
        return property.path("date").path("start").asText("");
    }

    private static List<String> propertyTags(JsonNode property) {
        List<String> tags = new ArrayList<>();
        if (property == null || property.isMissingNode() || property.isNull()) {
            return tags;
        }
        if ("multi_select".equals(property.path("type").asText())) {
            for (JsonNode item : property.path("multi_select")) {
                var name = item.path("name").asText("");
                if (StringUtils.hasText(name)) {
                    tags.add(name);
                }
            }
        }
        if ("select".equals(property.path("type").asText())) {
            var name = property.path("select").path("name").asText("");
            if (StringUtils.hasText(name)) {
                tags.add(name);
            }
        }
        return tags;
    }

    private static String propertyFileUrl(JsonNode property) {
        if (property == null || property.isMissingNode() || property.isNull()) {
            return "";
        }
        if ("url".equals(property.path("type").asText())) {
            return property.path("url").asText("");
        }
        if (!"files".equals(property.path("type").asText())) {
            return "";
        }
        var files = property.path("files");
        if (!files.isArray() || files.isEmpty()) {
            return "";
        }
        return notionFileUrl(files.get(0));
    }

    private static String pageCoverUrl(JsonNode cover) {
        if (cover == null || cover.isMissingNode() || cover.isNull()) {
            return "";
        }
        return notionFileUrl(cover);
    }

    private static String notionFileUrl(JsonNode node) {
        String type = node.path("type").asText("");
        return switch (type) {
            case "external" -> node.path("external").path("url").asText("");
            case "file" -> node.path("file").path("url").asText("");
            default -> "";
        };
    }

    private static String richTextPlain(JsonNode array) {
        if (array == null || !array.isArray()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode node : array) {
            builder.append(node.path("plain_text").asText(""));
        }
        return builder.toString().trim();
    }

    private static String formulaText(JsonNode formula) {
        return switch (formula.path("type").asText("")) {
            case "string" -> formula.path("string").asText("");
            case "number" -> formula.path("number").isMissingNode() || formula.path("number").isNull()
                ? "" : formula.path("number").asText();
            case "boolean" -> formula.path("boolean").asText("");
            case "date" -> formula.path("date").path("start").asText("");
            default -> "";
        };
    }

    private static String fallbackSlug(String pageId) {
        if (!StringUtils.hasText(pageId)) {
            return "";
        }
        return pageId.replace("-", "");
    }

    private static Iterable<JsonNode> nodes(JsonNode array) {
        List<JsonNode> nodes = new ArrayList<>();
        if (array != null && array.isArray()) {
            array.forEach(nodes::add);
        }
        return nodes;
    }
}
