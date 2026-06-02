package me.xiongfan.halo.cfbed;

import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

@Component
public class CfbedStoragePlugin extends BasePlugin {

    private final CfbedPolicySynchronizer synchronizer;

    public CfbedStoragePlugin(PluginContext pluginContext, CfbedPolicySynchronizer synchronizer) {
        super(pluginContext);
        this.synchronizer = synchronizer;
    }

    @Override
    public void start() {
        synchronizer.start();
    }

    @Override
    public void stop() {
        synchronizer.stop();
    }
}
