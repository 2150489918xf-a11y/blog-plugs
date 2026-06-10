package me.xiongfan.halo.notionstories;

import tools.jackson.databind.JsonNode;
import java.util.Objects;
import org.springframework.util.StringUtils;

public class NotionStoryProperties {

    static final String DEFAULT_DATABASE_ID = "37bbedc8681180199bbce653a8bf2325";

    private String notionToken;
    private String databaseId = DEFAULT_DATABASE_ID;
    private String pageTitle = "图片故事";
    private int cacheTtlMinutes = 10;
    private String titleProperty = "标题";
    private String slugProperty = "Slug";
    private String dateProperty = "日期";
    private String publishedProperty = "状态";
    private String publishedPropertyType = "checkbox";
    private String publishedValue = "公开";
    private String summaryProperty = "摘要";
    private String tagsProperty = "标签";
    private String coverProperty = "封面图";
    private boolean filterPermission;
    private String permissionProperty = "权限";
    private String publicPermissionValue = "公开";

    public static NotionStoryProperties from(JsonNode notion, JsonNode fields, JsonNode privacy) {
        var properties = new NotionStoryProperties();
        properties.notionToken = text(notion, "notionToken", null);
        properties.databaseId = normalizedDatabaseId(text(notion, "databaseId", DEFAULT_DATABASE_ID));
        properties.pageTitle = text(notion, "pageTitle", "图片故事");
        properties.cacheTtlMinutes = positiveInt(notion, "cacheTtlMinutes", 10);
        properties.titleProperty = text(fields, "titleProperty", "标题");
        properties.slugProperty = text(fields, "slugProperty", "Slug");
        properties.dateProperty = text(fields, "dateProperty", "日期");
        properties.publishedProperty = text(fields, "publishedProperty", "状态");
        properties.publishedPropertyType = text(fields, "publishedPropertyType", "checkbox");
        properties.publishedValue = text(fields, "publishedValue", "公开");
        properties.summaryProperty = text(fields, "summaryProperty", "摘要");
        properties.tagsProperty = text(fields, "tagsProperty", "标签");
        properties.coverProperty = text(fields, "coverProperty", "封面图");
        properties.filterPermission = bool(privacy, "filterPermission", false);
        properties.permissionProperty = text(privacy, "permissionProperty", "权限");
        properties.publicPermissionValue = text(privacy, "publicPermissionValue", "公开");
        properties.normalize();
        return properties;
    }

    private void normalize() {
        databaseId = normalizedDatabaseId(databaseId);
        pageTitle = textOrDefault(pageTitle, "图片故事");
        cacheTtlMinutes = cacheTtlMinutes <= 0 ? 10 : cacheTtlMinutes;
        titleProperty = textOrDefault(titleProperty, "标题");
        slugProperty = textOrDefault(slugProperty, "Slug");
        dateProperty = clean(dateProperty);
        publishedProperty = clean(publishedProperty);
        publishedPropertyType = textOrDefault(publishedPropertyType, "checkbox").toLowerCase();
        publishedValue = textOrDefault(publishedValue, "公开");
        summaryProperty = clean(summaryProperty);
        tagsProperty = clean(tagsProperty);
        coverProperty = clean(coverProperty);
        permissionProperty = textOrDefault(permissionProperty, "权限");
        publicPermissionValue = textOrDefault(publicPermissionValue, "公开");
    }

    public boolean hasRequiredConfig() {
        return StringUtils.hasText(notionToken) && StringUtils.hasText(databaseId);
    }

    public String cacheFingerprint() {
        return Integer.toHexString(Objects.hash(
            databaseId,
            tokenHash(),
            titleProperty,
            slugProperty,
            dateProperty,
            publishedProperty,
            publishedPropertyType,
            publishedValue,
            summaryProperty,
            tagsProperty,
            coverProperty,
            filterPermission,
            permissionProperty,
            publicPermissionValue
        ));
    }

    private int tokenHash() {
        return notionToken == null ? 0 : notionToken.hashCode();
    }

    private static String normalizedDatabaseId(String value) {
        var cleaned = clean(value);
        if (!StringUtils.hasText(cleaned)) {
            return DEFAULT_DATABASE_ID;
        }
        return cleaned.replace("-", "");
    }

    private static String text(JsonNode node, String name, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        var value = node.path(name);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        return textOrDefault(value.asText(), fallback);
    }

    private static boolean bool(JsonNode node, String name, boolean fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        var value = node.path(name);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        return Boolean.parseBoolean(value.asText(String.valueOf(fallback)));
    }

    private static int positiveInt(JsonNode node, String name, int fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        int value = node.path(name).asInt(fallback);
        return value > 0 ? value : fallback;
    }

    private static String textOrDefault(String value, String fallback) {
        var cleaned = clean(value);
        return StringUtils.hasText(cleaned) ? cleaned : fallback;
    }

    private static String clean(String value) {
        return value == null ? null : value.trim();
    }

    public String getNotionToken() {
        return notionToken;
    }

    public String getDatabaseId() {
        return databaseId;
    }

    public String getPageTitle() {
        return pageTitle;
    }

    public int getCacheTtlMinutes() {
        return cacheTtlMinutes;
    }

    public String getTitleProperty() {
        return titleProperty;
    }

    public String getSlugProperty() {
        return slugProperty;
    }

    public String getDateProperty() {
        return dateProperty;
    }

    public String getPublishedProperty() {
        return publishedProperty;
    }

    public String getPublishedPropertyType() {
        return publishedPropertyType;
    }

    public String getPublishedValue() {
        return publishedValue;
    }

    public String getSummaryProperty() {
        return summaryProperty;
    }

    public String getTagsProperty() {
        return tagsProperty;
    }

    public String getCoverProperty() {
        return coverProperty;
    }

    public boolean isFilterPermission() {
        return filterPermission;
    }

    public String getPermissionProperty() {
        return permissionProperty;
    }

    public String getPublicPermissionValue() {
        return publicPermissionValue;
    }
}
