package org.jabref.logic.sharelatex;

import java.util.ArrayList;
import java.util.List;

import org.jabref.model.entry.BibEntry;

public class ShareLatexEntryMessageEvent {

    List<BibEntry> entries = new ArrayList<>();

    public ShareLatexEntryMessageEvent(List<BibEntry> entries) {
        this.entries = entries;
    }

    public List<BibEntry> getEntries() {
        return this.entries;
    }
}
