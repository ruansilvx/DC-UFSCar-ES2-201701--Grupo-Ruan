package org.jabref.logic.sharelatex;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler.Whole;
import javax.websocket.Session;

import org.jabref.JabRefExecutorService;
import org.jabref.logic.importer.ImportFormatPreferences;
import org.jabref.logic.importer.ParseException;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.glassfish.tyrus.client.ClientManager;

public class WebSocketClientWrapper {

    private Session session;
    private String oldContent;
    private int version;
    private int commandCounter;
    private final ImportFormatPreferences prefs;
    private String docId;
    private String projectId;
    private String databaseName;
    private final EventBus eventBus = new EventBus("SharelatexEventBus");
    private boolean leftDoc = false;

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    private final ShareLatexParser parser = new ShareLatexParser();

    public WebSocketClientWrapper(ImportFormatPreferences prefs) {
        this.prefs = prefs;
        this.eventBus.register(this);
    }

    public void createAndConnect(URI webSocketchannelUri, String projectId, BibDatabaseContext database) {

        try {
            this.projectId = projectId;
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create()
                    .preferredSubprotocols(Arrays.asList("mqttt")).build();
            ClientManager client = ClientManager.createClient();

            this.session = client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    session.addMessageHandler(String.class, (Whole<String>) message -> {
                        System.out.println("Received message: " + message);
                        parseContents(message);

                    });
                }
            }, cec, webSocketchannelUri);

            //TODO: Change Dialog
            //TODO: On database change event or on save event send new version
            //TODO: When new db content arrived run merge dialog
            //TODO: Identfiy active database/Name of database/doc Id (partly done)

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void joinProject(String projectId) throws IOException {
        incrementCommandCounter();
        String text = "5:" + commandCounter + "+::{\"name\":\"joinProject\",\"args\":[{\"project_id\":\"" + projectId
                + "\"}]}";
        session.getBasicRemote().sendText(text);
    }

    public void joinDoc(String documentId) throws IOException {
        incrementCommandCounter();
        String text = "5:" + commandCounter + "+::{\"name\":\"joinDoc\",\"args\":[\"" + documentId + "\"]}";
        session.getBasicRemote().sendText(text);
    }

    public void leaveDocument(String documentId) throws IOException {
        incrementCommandCounter();
        String text = "5:" + commandCounter + "+::{\"name\":\"leaveDoc\",\"args\":[\"" + documentId + "\"]}";
        session.getBasicRemote().sendText(text);

    }

    private void sendHeartBeat() throws IOException {
        session.getBasicRemote().sendText("2::");
    }

    public void sendNewDatabaseContent(String newContent) throws InterruptedException {
        queue.put(newContent);
    }

    private void sendUpdateAsDeleteAndInsert(String docId, int position, int version, String oldContent, String newContent) throws IOException {
        ShareLatexJsonMessage message = new ShareLatexJsonMessage();
        String str = message.createDeleteInsertMessage(docId, position, version, oldContent, newContent);
        System.out.println("Send new update Message");

        session.getBasicRemote().sendText("5:::" + str);
    }

    @Subscribe
    public synchronized void listenToSharelatexEntryMessage(ShareLatexEntryMessageEvent event) {

        JabRefExecutorService.INSTANCE.executeInterruptableTask(() -> {
            try {
                String updatedContent = queue.take();
                if (!leftDoc) {
                    System.out.println("Taken from queue");
                    sendUpdateAsDeleteAndInsert(docId, 0, version, oldContent, updatedContent);

                }
            } catch (IOException | InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });

    }

    //Actual response handling
    private void parseContents(String message) {
        try {

            if (message.contains("2::")) {
                setLeftDoc(false);
                eventBus.post(new ShareLatexEntryMessageEvent(Collections.emptyList()));
                sendHeartBeat();

            }
            if (message.endsWith("[null]")) {
                System.out.println("Received null-> Rejoining doc");
                joinDoc(docId);
            }

            if (message.startsWith("[null,{", ShareLatexParser.JSON_START_OFFSET)) {
                System.out.println("We get a list with all files");
                //We get a list with all files
                Map<String, String> dbWithID = parser.getBibTexDatabasesNameWithId(message);

                setDocID(dbWithID.get("references.bib"));

                System.out.println("DBs with ID " + dbWithID);

                joinDoc(docId);

            }
            if (message.contains("{\"name\":\"connectionAccepted\"}") && (projectId != null)) {

                joinProject(projectId);

            }

            if (message.contains("[null,[")) {
                System.out.println("Message could be an entry ");

                int version = parser.getVersionFromBibTexJsonString(message);
                setVersion(version);

                String bibtexString = parser.getBibTexStringFromJsonMessage(message);
                setBibTexString(bibtexString);
                List<BibEntry> entries = parser.parseBibEntryFromJsonMessageString(message, prefs);

                System.out.println("Got new entries");
                setLeftDoc(false);

                eventBus.post(new ShareLatexEntryMessageEvent(entries));

            }

            if (message.contains("otUpdateApplied")) {
                System.out.println("We got an update");

                leaveDocument(docId);
                setLeftDoc(true);
            }

        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    public void setDatabaseName(String bibFileName) {
        this.databaseName = bibFileName;
    }

    public void leaveDocAndCloseConn() throws IOException {
        leaveDocument(docId);
        queue.clear();
        session.close();

    }
    public void registerListener(Object listener) {
        eventBus.register(listener);
    }

    public void unregisterListener(Object listener) {
        eventBus.unregister(listener);
    }

    private synchronized void setDocID(String docId) {
        this.docId = docId;
    }

    private synchronized void setVersion(int version) {
        this.version = version;
    }

    private synchronized void setBibTexString(String bibtex) {
        this.oldContent = bibtex;
    }

    private synchronized void incrementCommandCounter() {
        this.commandCounter = commandCounter + 1;
    }

    private synchronized void setLeftDoc(boolean leftDoc) {
        this.leftDoc = leftDoc;
    }


}
