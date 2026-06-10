package me.xiongfan.halo.notionstories;

import java.util.List;

public record NotionStoryListPage(
    NotionStoryProperties properties,
    List<NotionStory> stories,
    boolean configMissing
) {

    public static NotionStoryListPage configMissing(NotionStoryProperties properties) {
        return new NotionStoryListPage(properties, List.of(), true);
    }

    public static NotionStoryListPage configured(NotionStoryProperties properties,
        List<NotionStory> stories) {
        return new NotionStoryListPage(properties, stories, false);
    }
}
