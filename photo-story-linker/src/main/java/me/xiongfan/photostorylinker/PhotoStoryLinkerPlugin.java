package me.xiongfan.photostorylinker;

import org.springframework.stereotype.Component;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

@Component
public class PhotoStoryLinkerPlugin extends BasePlugin {

    private final SchemeManager schemeManager;

    public PhotoStoryLinkerPlugin(PluginContext pluginContext, SchemeManager schemeManager) {
        super(pluginContext);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        schemeManager.register(PhotoStoryBinding.class);
    }

    @Override
    public void stop() {
        schemeManager.unregister(schemeManager.get(PhotoStoryBinding.class));
    }
}
