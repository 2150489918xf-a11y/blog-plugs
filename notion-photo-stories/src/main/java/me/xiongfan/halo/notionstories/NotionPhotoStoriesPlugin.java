package me.xiongfan.halo.notionstories;

import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

@Component
public class NotionPhotoStoriesPlugin extends BasePlugin {

    public NotionPhotoStoriesPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }

    @Override
    public void start() {
        System.out.println("Notion photo stories plugin started.");
    }

    @Override
    public void stop() {
        System.out.println("Notion photo stories plugin stopped.");
    }
}
