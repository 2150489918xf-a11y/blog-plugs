package me.xiongfan.halo.cfbed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerErrorException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Attachment.AttachmentSpec;
import run.halo.app.core.extension.attachment.Attachment.AttachmentStatus;
import run.halo.app.core.extension.attachment.Constant;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.attachment.endpoint.AttachmentHandler;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.MetadataUtil;
import run.halo.app.infra.utils.JsonUtils;

@Extension
public class CfbedAttachmentHandler implements AttachmentHandler {

    private static final Logger log = LoggerFactory.getLogger(CfbedAttachmentHandler.class);

    public static final String TEMPLATE_NAME = "cfbed";
    public static final String REMOTE_SRC = "cfbed.storage.halo.run/src";
    public static final String DELETE_PATH = "cfbed.storage.halo.run/delete-path";

    @Override
    public Mono<Attachment> upload(UploadContext uploadContext) {
        return Mono.just(uploadContext)
            .filter(context -> shouldHandle(context.policy()))
            .flatMap(context -> {
                var properties = CfbedProperties.convertFrom(context.configMap());
                return DataBufferUtils.join(context.file().content())
                    .map(buffer -> {
                        try {
                            var bytes = new byte[buffer.readableByteCount()];
                            buffer.read(bytes);
                            return bytes;
                        } finally {
                            DataBufferUtils.release(buffer);
                        }
                    })
                    .flatMap(bytes -> uploadToCfbed(properties, context.file().filename(), bytes)
                        .map(result -> buildAttachment(context.policy(), properties,
                            context.file().filename(), bytes.length, result)))
                    .subscribeOn(Schedulers.boundedElastic());
            });
    }

    @Override
    public Mono<Attachment> delete(DeleteContext deleteContext) {
        return Mono.just(deleteContext)
            .filter(context -> shouldHandle(context.policy()))
            .flatMap(context -> {
                var attachment = context.attachment();
                var properties = CfbedProperties.convertFrom(context.configMap());
                if (!Boolean.TRUE.equals(properties.getDeleteRemote())) {
                    return Mono.just(attachment);
                }
                var deletePath = getAnnotation(attachment, DELETE_PATH);
                if (!StringUtils.hasText(deletePath)) {
                    log.warn("Cannot find CFBed delete path for attachment {}",
                        attachment.getMetadata().getName());
                    return Mono.just(attachment);
                }
                return deleteFromCfbed(properties, deletePath).thenReturn(attachment)
                    .subscribeOn(Schedulers.boundedElastic());
            });
    }

    @Override
    public Mono<URI> getPermalink(Attachment attachment, Policy policy, ConfigMap configMap) {
        if (!shouldHandle(policy)) {
            return Mono.empty();
        }
        return Optional.ofNullable(getAnnotation(attachment, Constant.EXTERNAL_LINK_ANNO_KEY))
            .filter(StringUtils::hasText)
            .map(URI::create)
            .map(Mono::just)
            .orElseGet(Mono::empty);
    }

    private Mono<CfbedUploadResult> uploadToCfbed(CfbedProperties properties, String filename,
        byte[] bytes) {
        return Mono.fromCallable(() -> {
            if (!properties.hasRequiredUploadConfig()) {
                throw new ServerErrorException("CFBed baseUrl and token are required.", null);
            }
            var boundary = "----HaloCfbedBoundary" + UUID.randomUUID();
            var body = multipartBody(boundary, "file", filename, bytes);
            var request = HttpRequest.newBuilder(uploadUri(properties))
                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .header("Authorization", "Bearer " + properties.getToken())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
            var response = buildClient(properties).send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ServerErrorException(
                    "CFBed upload failed with status " + response.statusCode() + ": "
                        + response.body(), null);
            }
            return parseUploadResult(properties, response.body());
        });
    }

    private Mono<Void> deleteFromCfbed(CfbedProperties properties, String deletePath) {
        return Mono.fromRunnable(() -> {
            try {
                var encodedPath = deletePath.startsWith("/") ? deletePath.substring(1) : deletePath;
                var request = HttpRequest.newBuilder(URI.create(
                        properties.getBaseUrl() + "/api/manage/delete/" + encodedPath))
                    .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.getToken())
                    .DELETE()
                    .build();
                var response = buildClient(properties).send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 404) {
                    return;
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new ServerErrorException(
                        "CFBed delete failed with status " + response.statusCode() + ": "
                            + response.body(), null);
                }
            } catch (IOException e) {
                throw new ServerErrorException("Failed to delete CFBed object", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ServerErrorException("Interrupted while deleting CFBed object", e);
            }
        });
    }

    private HttpClient buildClient(CfbedProperties properties) {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    private URI uploadUri(CfbedProperties properties) {
        var query = new StringBuilder();
        appendQuery(query, "returnFormat", properties.getReturnFormat());
        appendQuery(query, "uploadChannel", properties.getUploadChannel());
        appendQuery(query, "uploadFolder", properties.getUploadFolder());
        return URI.create(properties.getBaseUrl() + "/upload" + query);
    }

    private void appendQuery(StringBuilder query, String name, @Nullable String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        query.append(query.isEmpty() ? "?" : "&")
            .append(URLEncoder.encode(name, StandardCharsets.UTF_8))
            .append("=")
            .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private byte[] multipartBody(String boundary, String fieldName, String filename, byte[] content)
        throws IOException {
        var output = new ByteArrayOutputStream();
        var mediaType = MediaTypeFactory.getMediaType(filename)
            .orElse(MediaType.APPLICATION_OCTET_STREAM)
            .toString();
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\""
            + filename.replace("\"", "") + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Type: " + mediaType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(content);
        output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    private CfbedUploadResult parseUploadResult(CfbedProperties properties, String body) {
        var uploadItems = JsonUtils.jsonToObject(body,
            new TypeReference<List<CfbedUploadItem>>() {
            });
        if (uploadItems == null || uploadItems.isEmpty()
            || !StringUtils.hasText(uploadItems.get(0).getSrc())) {
            throw new ServerErrorException("CFBed upload returned no image URL: " + body, null);
        }
        var src = uploadItems.get(0).getSrc();
        var permalink = src.startsWith("http://") || src.startsWith("https://")
            ? src
            : properties.getPublicUrl() + (src.startsWith("/") ? src : "/" + src);
        return new CfbedUploadResult(src, permalink);
    }

    private Attachment buildAttachment(Policy policy, CfbedProperties properties, String filename,
        long size, CfbedUploadResult result) {
        var metadata = new Metadata();
        metadata.setName(UUID.randomUUID().toString());
        metadata.setAnnotations(new HashMap<>(Map.of(
            REMOTE_SRC, result.src(),
            DELETE_PATH, normalizeDeletePath(result.src()),
            Constant.EXTERNAL_LINK_ANNO_KEY, result.permalink()
        )));

        var spec = new AttachmentSpec();
        spec.setSize(size);
        spec.setDisplayName(filename);
        spec.setPolicyName(policy.getMetadata().getName());
        spec.setMediaType(MediaTypeFactory.getMediaType(filename)
            .orElse(MediaType.APPLICATION_OCTET_STREAM)
            .toString());

        var status = new AttachmentStatus();
        status.setPermalink(result.permalink());

        var attachment = new Attachment();
        attachment.setMetadata(metadata);
        attachment.setSpec(spec);
        attachment.setStatus(status);
        log.info("Uploaded attachment {} to CFBed {}", filename, result.permalink());
        return attachment;
    }

    private String normalizeDeletePath(String src) {
        if (src.startsWith("http://") || src.startsWith("https://")) {
            return URI.create(src).getPath();
        }
        return src;
    }

    @Nullable
    private String getAnnotation(Attachment attachment, String key) {
        return MetadataUtil.nullSafeAnnotations(attachment).get(key);
    }

    boolean shouldHandle(Policy policy) {
        return policy != null && policy.getSpec() != null
            && TEMPLATE_NAME.equals(policy.getSpec().getTemplateName());
    }

    record CfbedUploadResult(String src, String permalink) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class CfbedUploadItem {
        private String src;

        public String getSrc() {
            return src;
        }

        public void setSrc(String src) {
            this.src = src;
        }
    }
}
