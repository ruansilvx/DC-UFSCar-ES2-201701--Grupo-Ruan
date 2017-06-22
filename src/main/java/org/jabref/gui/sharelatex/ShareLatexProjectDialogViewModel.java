package org.jabref.gui.sharelatex;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;

import org.jabref.gui.AbstractViewModel;
import org.jabref.gui.StateManager;
import org.jabref.logic.sharelatex.ShareLatexEntryMessageEvent;
import org.jabref.logic.sharelatex.ShareLatexManager;
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

        Path p = stateManager.getActiveDatabase().get().getDatabasePath().get();

        try (BufferedWriter writer = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            writer.write(event.getNewDatabaseContent());
            writer.close();

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

}
