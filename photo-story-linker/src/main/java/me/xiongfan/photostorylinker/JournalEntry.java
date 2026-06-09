package me.xiongfan.photostorylinker;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = PhotoStoryLinker.GROUP, version = PhotoStoryLinker.VERSION,
    kind = "JournalEntry", plural = "journalentries", singular = "journalentry")
public class JournalEntry extends AbstractExtension {

    private Spec spec;

    @Data
    public static class Spec {
        private String singlePageName;
        private String coverPhotoName;
        private List<String> photoNames;
        private String journalDate;
        private String teaser;
        private Boolean enabled = true;
        private Boolean showInJournalList = true;
        private String mood;
        private String location;
        private String weather;
    }
}
