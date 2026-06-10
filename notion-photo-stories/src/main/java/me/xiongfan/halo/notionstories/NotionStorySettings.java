package me.xiongfan.halo.notionstories;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

@Component
public class NotionStorySettings {

    private static final JsonNode EMPTY = JsonNodeFactory.instance.objectNode();

    private final ReactiveSettingFetcher settingFetcher;

    public NotionStorySettings(ReactiveSettingFetcher settingFetcher) {
        this.settingFetcher = settingFetcher;
    }

    public Mono<NotionStoryProperties> get() {
        return Mono.zip(
            settingValue("notion"),
            settingValue("fields"),
            settingValue("privacy")
        ).map(tuple -> NotionStoryProperties.from(tuple.getT1(), tuple.getT2(), tuple.getT3()));
    }

    private Mono<JsonNode> settingValue(String group) {
        return settingFetcher.getSettingValue(group).defaultIfEmpty(EMPTY);
    }
}
