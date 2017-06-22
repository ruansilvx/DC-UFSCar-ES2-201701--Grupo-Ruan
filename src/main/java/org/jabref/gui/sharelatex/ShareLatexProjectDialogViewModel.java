package org.jabref.gui.sharelatex;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;

import org.jabref.gui.AbstractViewModel;
import org.jabref.gui.StateManager;
import org.jabref.logic.bibtex.comparator.EntryComparator;
import org.jabref.logic.sharelatex.ShareLatexEntryMessageEvent;
import org.jabref.logic.sharelatex.ShareLatexManager;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.FieldName;
import org.jabref.model.sharelatex.ShareLatexProject;

import com.google.common.eventbus.Subscribe;

public class ShareLatexProjectDialogViewModel extends AbstractViewModel {

    private final StateManager stateManager;
    private final SimpleListProperty<ShareLatexProjectViewModel> projects = new SimpleListProperty<>(
            FXCollections.observableArrayList());

    public ShareLatexProjectDialogViewModel(StateManager stateManager, ShareLatexManager manager) {
        this.stateManager = stateManager;
        manager.registerListener(this);
        //todo currently unused
    }

    public void addProjects(List<ShareLatexProject> projectsToAdd) {
        this.projects.clear();
        this.projects.addAll(projectsToAdd.stream().map(ShareLatexProjectViewModel::new).collect(Collectors.toList()));
    }

    public SimpleListProperty<ShareLatexProjectViewModel> projectsProperty() {
        return this.projects;
    }


    @Subscribe
    public void listenToSharelatexEntryMessage(ShareLatexEntryMessageEvent event) {

        List<BibEntry> entries = event.getEntries();
        Collections.sort(entries, new EntryComparator(false, true, FieldName.AUTHOR));
        List<BibEntry> entriesInDB = stateManager.getEntriesInCurrentDatabase();
        Collections.sort(entriesInDB, new EntryComparator(false, true, FieldName.AUTHOR));

    }

}
