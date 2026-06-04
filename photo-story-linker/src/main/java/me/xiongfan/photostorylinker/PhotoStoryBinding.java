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
        private String postName;
        private String teaser;
        private String badgeText = "Read story";
        private Boolean enabled = true;
        private OpenMode openMode = OpenMode.SAME_TAB;
    }

    public enum OpenMode {
        SAME_TAB,
        NEW_TAB
    }
}
