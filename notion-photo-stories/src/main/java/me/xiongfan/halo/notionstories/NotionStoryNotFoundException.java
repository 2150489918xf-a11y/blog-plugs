package me.xiongfan.halo.notionstories;

public class NotionStoryNotFoundException extends RuntimeException {

    private final String slug;

    public NotionStoryNotFoundException(String slug) {
        super("Notion story not found: " + slug);
        this.slug = slug;
    }

    public String getSlug() {
        return slug;
    }
}
