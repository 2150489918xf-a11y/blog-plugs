package me.xiongfan.halo.notionstories;

import java.util.List;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

public final class NotionBlockRenderer {

    private NotionBlockRenderer() {
    }

    public static String render(List<JsonNode> blocks) {
        StringBuilder html = new StringBuilder();
        String openList = null;
        for (JsonNode block : blocks) {
            String type = block.path("type").asText("");
            String currentList = listTagFor(type);
            if (!same(openList, currentList)) {
                if (openList != null) {
                    html.append("</").append(openList).append(">");
                }
                if (currentList != null) {
                    html.append("<").append(currentList).append(">");
                }
                openList = currentList;
            }
            html.append(renderBlock(block, type));
        }
        if (openList != null) {
            html.append("</").append(openList).append(">");
        }
        return html.toString();
    }

    private static String renderBlock(JsonNode block, String type) {
        JsonNode data = block.path(type);
        return switch (type) {
            case "paragraph" -> wrap("p", richText(data.path("rich_text")));
            case "heading_1" -> wrap("h1", richText(data.path("rich_text")));
            case "heading_2" -> wrap("h2", richText(data.path("rich_text")));
            case "heading_3" -> wrap("h3", richText(data.path("rich_text")));
            case "bulleted_list_item", "numbered_list_item" ->
                "<li>" + richText(data.path("rich_text")) + "</li>";
            case "to_do" -> renderToDo(data);
            case "quote" -> wrap("blockquote", richText(data.path("rich_text")));
            case "callout" -> renderCallout(data);
            case "code" -> renderCode(data);
            case "image" -> renderImage(data);
            case "video", "file", "pdf" -> renderFile(data);
            case "bookmark", "embed", "link_preview" -> renderLinkCard(data, type);
            case "divider" -> "<hr>";
            default -> "";
        };
    }

    private static String renderToDo(JsonNode data) {
        String checked = data.path("checked").asBoolean(false) ? " checked" : "";
        return "<label class=\"nps-todo\"><input type=\"checkbox\" disabled" + checked + ">"
            + "<span>" + richText(data.path("rich_text")) + "</span></label>";
    }

    private static String renderCallout(JsonNode data) {
        String icon = "";
        if ("emoji".equals(data.path("icon").path("type").asText())) {
            icon = escape(data.path("icon").path("emoji").asText(""));
        }
        return "<aside class=\"nps-callout\"><span class=\"nps-callout-icon\">" + icon
            + "</span><div>" + richText(data.path("rich_text")) + "</div></aside>";
    }

    private static String renderCode(JsonNode data) {
        String language = data.path("language").asText("");
        String text = plainText(data.path("rich_text"));
        return "<pre><code data-language=\"" + escapeAttribute(language) + "\">"
            + escape(text) + "</code></pre>";
    }

    private static String renderImage(JsonNode data) {
        String url = fileUrl(data);
        if (!StringUtils.hasText(url)) {
            return "";
        }
        String caption = richText(data.path("caption"));
        return "<figure><img src=\"" + escapeAttribute(url) + "\" loading=\"lazy\" alt=\"\">"
            + (StringUtils.hasText(caption) ? "<figcaption>" + caption + "</figcaption>" : "")
            + "</figure>";
    }

    private static String renderFile(JsonNode data) {
        String url = fileUrl(data);
        if (!StringUtils.hasText(url)) {
            return "";
        }
        String label = plainText(data.path("caption"));
        if (!StringUtils.hasText(label)) {
            label = url;
        }
        return "<p><a href=\"" + escapeAttribute(url) + "\" target=\"_blank\" rel=\"noreferrer\">"
            + escape(label) + "</a></p>";
    }

    private static String renderLinkCard(JsonNode data, String type) {
        String url = data.path("url").asText("");
        if ("link_preview".equals(type)) {
            url = data.path("url").asText("");
        }
        if (!StringUtils.hasText(url)) {
            return "";
        }
        return "<p class=\"nps-link-card\"><a href=\"" + escapeAttribute(url)
            + "\" target=\"_blank\" rel=\"noreferrer\">" + escape(url) + "</a></p>";
    }

    private static String richText(JsonNode array) {
        if (array == null || !array.isArray()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode node : array) {
            builder.append(richTextNode(node));
        }
        return builder.toString();
    }

    private static String richTextNode(JsonNode node) {
        String text = escape(node.path("plain_text").asText(""));
        JsonNode annotations = node.path("annotations");
        if (annotations.path("code").asBoolean(false)) {
            text = "<code>" + text + "</code>";
        }
        if (annotations.path("bold").asBoolean(false)) {
            text = "<strong>" + text + "</strong>";
        }
        if (annotations.path("italic").asBoolean(false)) {
            text = "<em>" + text + "</em>";
        }
        if (annotations.path("strikethrough").asBoolean(false)) {
            text = "<s>" + text + "</s>";
        }
        if (annotations.path("underline").asBoolean(false)) {
            text = "<u>" + text + "</u>";
        }
        String href = node.path("href").asText("");
        if (StringUtils.hasText(href)) {
            text = "<a href=\"" + escapeAttribute(href)
                + "\" target=\"_blank\" rel=\"noreferrer\">" + text + "</a>";
        }
        return text;
    }

    private static String plainText(JsonNode array) {
        if (array == null || !array.isArray()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode node : array) {
            builder.append(node.path("plain_text").asText(""));
        }
        return builder.toString();
    }

    private static String fileUrl(JsonNode data) {
        String type = data.path("type").asText("");
        return switch (type) {
            case "external" -> data.path("external").path("url").asText("");
            case "file" -> data.path("file").path("url").asText("");
            default -> "";
        };
    }

    private static String wrap(String tag, String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return "<" + tag + ">" + content + "</" + tag + ">";
    }

    private static String listTagFor(String type) {
        return switch (type) {
            case "bulleted_list_item" -> "ul";
            case "numbered_list_item" -> "ol";
            default -> null;
        };
    }

    private static boolean same(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    static String escapeAttribute(String value) {
        return escape(value);
    }
}
