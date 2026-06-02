package me.xiongfan.halo.cfbed;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.attachment.Policy.PolicySpec;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.utils.JsonUtils;

@Component
public class CfbedPolicySynchronizer {

    private static final Logger log = LoggerFactory.getLogger(CfbedPolicySynchronizer.class);

    static final String PLUGIN_CONFIG_MAP = "cfbed-config";
    static final String POLICY_CONFIG_SUFFIX = "-config";
    static final String SYSTEM_CONFIG_MAP = "system";

    private final ReactiveExtensionClient client;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Disposable loop;

    public CfbedPolicySynchronizer(ReactiveExtensionClient client) {
        this.client = client;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        syncOnce().subscribeOn(Schedulers.boundedElastic())
            .subscribe(null, error -> log.warn("Failed to sync CFBed policy on startup", error));
        loop = Mono.delay(Duration.ofSeconds(30))
            .repeat()
            .flatMap(ignored -> syncOnce())
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(null, error -> log.warn("CFBed policy sync loop failed", error));
    }

    public void stop() {
        running.set(false);
        if (loop != null) {
            loop.dispose();
        }
    }

    Mono<Void> syncOnce() {
        return loadProperties()
            .flatMap(properties -> {
                if (!Boolean.TRUE.equals(properties.getSyncPolicy())) {
                    return Mono.empty();
                }
                if (!properties.hasRequiredUploadConfig()) {
                    log.debug("Skip CFBed policy sync because baseUrl or token is empty.");
                    return Mono.empty();
                }
                return upsertPolicyConfig(properties)
                    .then(upsertPolicy(properties))
                    .then(syncSystemDefaults(properties))
                    .then();
            })
            .onErrorResume(error -> {
                log.warn("Failed to sync CFBed storage policy", error);
                return Mono.empty();
            });
    }

    private Mono<CfbedProperties> loadProperties() {
        return client.fetch(ConfigMap.class, PLUGIN_CONFIG_MAP)
            .map(CfbedProperties::convertFrom)
            .switchIfEmpty(Mono.just(CfbedProperties.defaults()));
    }

    private Mono<ConfigMap> upsertPolicyConfig(CfbedProperties properties) {
        var configName = policyConfigName(properties);
        return client.fetch(ConfigMap.class, configName)
            .flatMap(existing -> {
                existing.setData(Map.of("default", properties.toPolicyConfigJson()));
                return client.update(existing);
            })
            .switchIfEmpty(Mono.defer(() -> client.create(newConfigMap(configName, properties))));
    }

    private ConfigMap newConfigMap(String name, CfbedProperties properties) {
        var configMap = new ConfigMap();
        var metadata = new Metadata();
        metadata.setName(name);
        configMap.setMetadata(metadata);
        configMap.setData(Map.of("default", properties.toPolicyConfigJson()));
        return configMap;
    }

    private Mono<Policy> upsertPolicy(CfbedProperties properties) {
        return client.fetch(Policy.class, properties.getPolicyName())
            .flatMap(existing -> {
                existing.setSpec(policySpec(properties));
                return client.update(existing);
            })
            .switchIfEmpty(Mono.defer(() -> client.create(newPolicy(properties))));
    }

    private Policy newPolicy(CfbedProperties properties) {
        var policy = new Policy();
        var metadata = new Metadata();
        metadata.setName(properties.getPolicyName());
        policy.setMetadata(metadata);
        policy.setSpec(policySpec(properties));
        return policy;
    }

    private PolicySpec policySpec(CfbedProperties properties) {
        var spec = new PolicySpec();
        spec.setDisplayName(properties.getPolicyDisplayName());
        spec.setTemplateName(CfbedAttachmentHandler.TEMPLATE_NAME);
        spec.setConfigMapName(policyConfigName(properties));
        return spec;
    }

    private String policyConfigName(CfbedProperties properties) {
        return properties.getPolicyName() + POLICY_CONFIG_SUFFIX;
    }

    private Mono<ConfigMap> syncSystemDefaults(CfbedProperties properties) {
        if (!Boolean.TRUE.equals(properties.getSetAsDefaultConsolePolicy())
            && !Boolean.TRUE.equals(properties.getSetAsDefaultUcPolicy())
            && !Boolean.TRUE.equals(properties.getSetAsDefaultAvatarPolicy())) {
            return Mono.empty();
        }
        return client.fetch(ConfigMap.class, SYSTEM_CONFIG_MAP)
            .flatMap(configMap -> {
                var data = new HashMap<>(Optional.ofNullable(configMap.getData()).orElse(Map.of()));
                syncAttachmentConfig(data, properties);
                syncPostConfig(data, properties);
                configMap.setData(data);
                return client.update(configMap);
            });
    }

    @SuppressWarnings("unchecked")
    private void syncAttachmentConfig(Map<String, String> data, CfbedProperties properties) {
        var attachment = JsonUtils.jsonToObject(data.getOrDefault("attachment", "{}"), Map.class);
        var mutable = new HashMap<String, Object>(attachment == null ? Map.of() : attachment);

        if (Boolean.TRUE.equals(properties.getSetAsDefaultConsolePolicy())) {
            mutable.put("console", policyGroup(properties.getPolicyName(),
                nestedGroupName(mutable.get("console"))));
        }
        if (Boolean.TRUE.equals(properties.getSetAsDefaultUcPolicy())) {
            mutable.put("uc", policyGroup(properties.getPolicyName(), nestedGroupName(mutable.get("uc"))));
        }
        if (Boolean.TRUE.equals(properties.getSetAsDefaultAvatarPolicy())) {
            mutable.put("avatar", policyGroup(properties.getPolicyName(),
                nestedGroupName(mutable.get("avatar"))));
        }
        data.put("attachment", JsonUtils.objectToJson(mutable));
    }

    @SuppressWarnings("unchecked")
    private String nestedGroupName(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        var groupName = map.get("groupName");
        return groupName instanceof String text ? text : null;
    }

    private Map<String, String> policyGroup(String policyName, String groupName) {
        var group = new HashMap<String, String>();
        group.put("policyName", policyName);
        if (groupName != null) {
            group.put("groupName", groupName);
        }
        return group;
    }

    @SuppressWarnings("unchecked")
    private void syncPostConfig(Map<String, String> data, CfbedProperties properties) {
        if (!Boolean.TRUE.equals(properties.getSetAsDefaultConsolePolicy())) {
            return;
        }
        var post = JsonUtils.jsonToObject(data.getOrDefault("post", "{}"), Map.class);
        var mutable = new HashMap<String, Object>(post == null ? Map.of() : post);
        mutable.put("attachmentPolicyName", properties.getPolicyName());
        data.put("post", JsonUtils.objectToJson(mutable));
    }
}
