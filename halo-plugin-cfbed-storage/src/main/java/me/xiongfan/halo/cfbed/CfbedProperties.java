package me.xiongfan.halo.cfbed;

import org.springframework.util.StringUtils;
import run.halo.app.extension.ConfigMap;
import run.halo.app.infra.utils.JsonUtils;

public class CfbedProperties {

    private String baseUrl;
    private String token;
    private String uploadChannel = "cfr2";
    private String uploadFolder = "halo";
    private String returnFormat = "full";
    private String publicUrl;
    private Boolean deleteRemote = false;
    private Integer connectTimeoutSeconds = 15;
    private Integer requestTimeoutSeconds = 120;
    private String policyName = "cfbed-policy";
    private String policyDisplayName = "CloudFlare ImgBed";
    private Boolean syncPolicy = true;
    private Boolean setAsDefaultConsolePolicy = true;
    private Boolean setAsDefaultUcPolicy = false;
    private Boolean setAsDefaultAvatarPolicy = false;

    public static CfbedProperties convertFrom(ConfigMap configMap) {
        var settingJson = configMap.getData().getOrDefault("default", "{}");
        var properties = JsonUtils.jsonToObject(settingJson, CfbedProperties.class);
        properties.normalize();
        return properties;
    }

    public static CfbedProperties defaults() {
        var properties = new CfbedProperties();
        properties.normalize();
        return properties;
    }

    public void normalize() {
        baseUrl = trimTrailingSlash(baseUrl);
        publicUrl = StringUtils.hasText(publicUrl) ? trimTrailingSlash(publicUrl) : baseUrl;
        if (!StringUtils.hasText(uploadChannel)) {
            uploadChannel = "cfr2";
        }
        if (!StringUtils.hasText(uploadFolder)) {
            uploadFolder = "halo";
        }
        if (!StringUtils.hasText(returnFormat)) {
            returnFormat = "full";
        }
        if (!StringUtils.hasText(policyName)) {
            policyName = "cfbed-policy";
        }
        if (!StringUtils.hasText(policyDisplayName)) {
            policyDisplayName = "CloudFlare ImgBed";
        }
        if (syncPolicy == null) {
            syncPolicy = true;
        }
        if (setAsDefaultConsolePolicy == null) {
            setAsDefaultConsolePolicy = true;
        }
        if (setAsDefaultUcPolicy == null) {
            setAsDefaultUcPolicy = false;
        }
        if (setAsDefaultAvatarPolicy == null) {
            setAsDefaultAvatarPolicy = false;
        }
        if (deleteRemote == null) {
            deleteRemote = false;
        }
        if (connectTimeoutSeconds == null || connectTimeoutSeconds < 1) {
            connectTimeoutSeconds = 15;
        }
        if (requestTimeoutSeconds == null || requestTimeoutSeconds < 5) {
            requestTimeoutSeconds = 120;
        }
    }

    public boolean hasRequiredUploadConfig() {
        return StringUtils.hasText(baseUrl) && StringUtils.hasText(token);
    }

    public String toPolicyConfigJson() {
        return JsonUtils.objectToJson(new PolicyConfig(this));
    }

    static class PolicyConfig {
        private final String baseUrl;
        private final String token;
        private final String uploadChannel;
        private final String uploadFolder;
        private final String returnFormat;
        private final String publicUrl;
        private final Boolean deleteRemote;
        private final Integer connectTimeoutSeconds;
        private final Integer requestTimeoutSeconds;

        PolicyConfig(CfbedProperties properties) {
            this.baseUrl = properties.baseUrl;
            this.token = properties.token;
            this.uploadChannel = properties.uploadChannel;
            this.uploadFolder = properties.uploadFolder;
            this.returnFormat = properties.returnFormat;
            this.publicUrl = properties.publicUrl;
            this.deleteRemote = properties.deleteRemote;
            this.connectTimeoutSeconds = properties.connectTimeoutSeconds;
            this.requestTimeoutSeconds = properties.requestTimeoutSeconds;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public String getToken() {
            return token;
        }

        public String getUploadChannel() {
            return uploadChannel;
        }

        public String getUploadFolder() {
            return uploadFolder;
        }

        public String getReturnFormat() {
            return returnFormat;
        }

        public String getPublicUrl() {
            return publicUrl;
        }

        public Boolean getDeleteRemote() {
            return deleteRemote;
        }

        public Integer getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public Integer getRequestTimeoutSeconds() {
            return requestTimeoutSeconds;
        }
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        var trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUploadChannel() {
        return uploadChannel;
    }

    public void setUploadChannel(String uploadChannel) {
        this.uploadChannel = uploadChannel;
    }

    public String getUploadFolder() {
        return uploadFolder;
    }

    public void setUploadFolder(String uploadFolder) {
        this.uploadFolder = uploadFolder;
    }

    public String getReturnFormat() {
        return returnFormat;
    }

    public void setReturnFormat(String returnFormat) {
        this.returnFormat = returnFormat;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public Boolean getDeleteRemote() {
        return deleteRemote;
    }

    public void setDeleteRemote(Boolean deleteRemote) {
        this.deleteRemote = deleteRemote;
    }

    public Integer getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(Integer connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public Integer getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(Integer requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    public String getPolicyDisplayName() {
        return policyDisplayName;
    }

    public void setPolicyDisplayName(String policyDisplayName) {
        this.policyDisplayName = policyDisplayName;
    }

    public Boolean getSyncPolicy() {
        return syncPolicy;
    }

    public void setSyncPolicy(Boolean syncPolicy) {
        this.syncPolicy = syncPolicy;
    }

    public Boolean getSetAsDefaultConsolePolicy() {
        return setAsDefaultConsolePolicy;
    }

    public void setSetAsDefaultConsolePolicy(Boolean setAsDefaultConsolePolicy) {
        this.setAsDefaultConsolePolicy = setAsDefaultConsolePolicy;
    }

    public Boolean getSetAsDefaultUcPolicy() {
        return setAsDefaultUcPolicy;
    }

    public void setSetAsDefaultUcPolicy(Boolean setAsDefaultUcPolicy) {
        this.setAsDefaultUcPolicy = setAsDefaultUcPolicy;
    }

    public Boolean getSetAsDefaultAvatarPolicy() {
        return setAsDefaultAvatarPolicy;
    }

    public void setSetAsDefaultAvatarPolicy(Boolean setAsDefaultAvatarPolicy) {
        this.setAsDefaultAvatarPolicy = setAsDefaultAvatarPolicy;
    }
}
