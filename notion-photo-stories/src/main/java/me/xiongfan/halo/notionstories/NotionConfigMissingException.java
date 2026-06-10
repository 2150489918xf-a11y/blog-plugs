package me.xiongfan.halo.notionstories;

public class NotionConfigMissingException extends RuntimeException {

    public NotionConfigMissingException() {
        super("Notion photo stories plugin is not configured.");
    }
}
