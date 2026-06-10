package me.xiongfan.halo.notionstories;

import java.util.List;

public record NotionStory(
    String pageId,
    String title,
    String slug,
    String date,
    String summary,
    List<String> tags,
    String coverUrl,
    String notionUrl,
    String lastEditedTime,
    String contentHtml
) {

    public NotionStory withContentHtml(String html) {
        return new NotionStory(
            pageId,
            title,
            slug,
            date,
            summary,
            tags,
            coverUrl,
            notionUrl,
            lastEditedTime,
            html
        );
    }
}
