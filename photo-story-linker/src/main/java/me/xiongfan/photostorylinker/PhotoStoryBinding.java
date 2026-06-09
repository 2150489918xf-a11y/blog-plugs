package me.xiongfan.photostorylinker;

import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = PhotoStoryLinker.GROUP, version = PhotoStoryLinker.VERSION,
    kind = "PhotoStoryBinding", plural = "photostorybindings", singular = "photostorybinding")
public class PhotoStoryBinding extends AbstractExtension {

    private Spec spec;

    @Data
    public static class Spec {
        private String photoName;
        /**
         * Kept for existing bindings created before the journal model was introduced.
         */
        private String postName;
        private TargetKind targetKind = TargetKind.POST;
        private String targetName;
        private String teaser;
        private String badgeText = "阅读日记";
        private Boolean enabled = true;
        private OpenMode openMode = OpenMode.SAME_TAB;

        public TargetKind getResolvedTargetKind() {
            return targetKind == null ? TargetKind.POST : targetKind;
        }

        public String getResolvedTargetName() {
            if (targetName != null && !targetName.isBlank()) {
                return targetName;
            }
            return postName;
        }
    }

    public enum TargetKind {
        POST,
        SINGLE_PAGE
    }

    public enum OpenMode {
        SAME_TAB,
        NEW_TAB
    }
}
