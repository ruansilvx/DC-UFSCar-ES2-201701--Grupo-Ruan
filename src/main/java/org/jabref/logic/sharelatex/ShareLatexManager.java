package org.jabref.logic.sharelatex;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.jabref.JabRefExecutorService;
import org.jabref.logic.exporter.BibtexDatabaseWriter;
import org.jabref.logic.exporter.SaveException;
import org.jabref.logic.exporter.SavePreferences;
import org.jabref.logic.exporter.StringSaveSession;
import org.jabref.logic.importer.ImportFormatPreferences;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.sharelatex.ShareLatexProject;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

public class ShareLatexManager {

    private final SharelatexConnector connector = new SharelatexConnector();
    private final List<ShareLatexProject> projects = new ArrayList<>();

    public String login(String server, String username, String password) throws IOException {
        return connector.connectToServer(server, username, password);
    }

    public List<ShareLatexProject> getProjects() throws IOException {

        connector.getProjects().ifPresent(jsonResponse -> {
            if (jsonResponse.has("projects")) {
                JsonArray projectArray = jsonResponse.get("projects").getAsJsonArray();
                System.out.println(projectArray);
                for (JsonElement elem : projectArray) {

                    String id = elem.getAsJsonObject().get("id").getAsString();
                    String name = elem.getAsJsonObject().get("name").getAsString();
                    String lastUpdated = elem.getAsJsonObject().get("lastUpdated").getAsString();
                    String owner = elem.getAsJsonObject().get("owner_ref").getAsString();
                    System.out.println("ID " + id);
                    System.out.println("Name " + name);
                    System.out.println("LastUpdated " + lastUpdated);
                    System.out.println("Owner" + owner);

                    ShareLatexProject project = new ShareLatexProject(id, name, owner, lastUpdated);
                    projects.add(project);
                }

            }
        });
        return projects;
    }

    public void startWebSocketHandler(String projectID, BibDatabaseContext database, ImportFormatPreferences preferences) {
        JabRefExecutorService.INSTANCE.executeAndWait(() -> {

            try {
                connector.startWebsocketListener(projectID, database, preferences);
                registerListener(ShareLatexManager.this);
            } catch (URISyntaxException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });
    }

    //Send new changes to the server
    //At best when we do a save operation
    public void sendNewDataseContent(BibDatabaseContext database) {
        try {
            BibtexDatabaseWriter<StringSaveSession> databaseWriter = new BibtexDatabaseWriter<>(StringSaveSession::new);
            StringSaveSession saveSession = databaseWriter.saveDatabase(database, new SavePreferences());
            String updatedcontent = saveSession.getStringValue().replace("\r\n", "\n");

            connector.sendNewDatabaseContent(updatedcontent);
        } catch (InterruptedException | SaveException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    public void registerListener(Object listener) {
        connector.registerListener(listener);
    }

    public void unregisterListener(Object listener) {
        connector.unregisterListener(listener);
    }

    public void disconnectAndCloseConnection() {
        connector.disconnectAndCloseConn();
    }
}
