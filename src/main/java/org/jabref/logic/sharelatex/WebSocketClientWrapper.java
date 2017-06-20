package org.jabref.logic.sharelatex;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
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
import org.jabref.logic.exporter.BibtexDatabaseWriter;
import org.jabref.logic.exporter.SaveException;
import org.jabref.logic.exporter.SavePreferences;
import org.jabref.logic.exporter.StringSaveSession;
import org.jabref.logic.importer.ImportFormatPreferences;
import org.jabref.logic.importer.ParseException;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.database.event.BibDatabaseContextChangedEvent;
import org.jabref.model.entry.BibEntry;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.glassfish.tyrus.client.ClientManager;

public class WebSocketClientWrapper {

    private Session session;
    private String oldContent;
    private int version;
    private int commandCounter;
    private BibDatabaseContext newDb;
    private final ImportFormatPreferences prefs;
    private String docId;
    private String projectId;
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
            this.newDb = database;

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

            database.getDatabase().registerListener(this);

            //TODO: Send/Receive with CountDownLatch -- Find alternative
            //TODO: Change Dialog
            //TODO: Keep old database string which last came in + version
            //TODO: On database change event or on save event send new version
            //TODO: When new db content arrived run merge dialog
            //TODO: Find out how to best increment the numbers (see python script from vim)
            //TODO: Identfiy active database/Name of database/doc Id (partly done)
            //TODO: Switch back to anymous class to have all in one class?
            //TODO:

            //If message starts with [null,[ we have an entry content
            //If message contains rootDoc or so then we have gotten the initial joinProject result
            /*
             * Received message: 1::
            Received message: 5:::{"name":"connectionAccepted"}
            Received message: 6:::1+[null,{"_id":"5936d96b1bd5906b0082f53c","name":"Example","rootDoc_id":"5936d96b1bd5906b0082f53d","rootFolder":[{"_id":"5936d96b1bd5906b0082f53b","name":"rootFolder","folders":[],"fileRefs":[{"_id":"5936d96b1bd5906b0082f53f","name":"universe.jpg"}],"docs":[{"_id":"5936d96b1bd5906b0082f53d","name":"main.tex"},{"_id":"5936d96b1bd5906b0082f53e","name":"references.bib"}]}],"publicAccesLevel":"private","dropboxEnabled":false,"compiler":"pdflatex","description":"","spellCheckLanguage":"en","deletedByExternalDataSource":false,"deletedDocs":[],"members":[{"_id":"5912e195a303b468002eaad0","first_name":"jim","last_name":"","email":"jim@example.com","privileges":"readAndWrite","signUpDate":"2017-05-10T09:47:01.325Z"}],"invites":[],"owner":{"_id":"5909ed80761dc10a01f7abc0","first_name":"joe","last_name":"","email":"joe@example.com","privileges":"owner","signUpDate":"2017-05-03T14:47:28.665Z"},"features":{"trackChanges":true,"references":true,"templates":true,"compileGroup":"standard","compileTimeout":180,"github":false,"dropbox":true,"versioning":true,"collaborators":-1,"trackChangesVisible":false}},"owner",2]
            Received message: 6:::7+[null,["@book{adams1996hitchhiker,","  author = {Adams, D.}","}@book{adams1995hitchhiker,       ","   title={The Hitchhiker's Guide to the Galaxy},","  author={Adams, D.},","  isbn={9781417642595},","  url={http://books.google.com/books?id=W-xMPgAACAAJ},","  year={199},","  publisher={San Val}","}",""],73,[],{}]
            Message could be an entry

            //We need a command counter which updates the part after :

             * if message_type == "update":
            self.ipc_session.send("5:::"+message_content)
            elif message_type == "cmd":
                self.command_counter += 1
            self.ipc_session.send("5:" + str(self.command_counter) + "+::" + message_content)
                elif message_type == "alive":
            self.ipc_session.send("2::")

             */

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void joinProject(String projectId) throws IOException {
        incrementCommandCounter();
        String text = "5:" + commandCounter + "+::{\"name\":\"joinProject\",\"args\":[{\"project_id\":\"" + projectId + "\"}]}";
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

    public void sendHeartBeat() throws IOException {
        session.getBasicRemote().sendText("2::");
    }

    public void sendUpdateAsDeleteAndInsert(String docId, int position, int version, String oldContent, String newContent) throws IOException {
        ShareLatexJsonMessage message = new ShareLatexJsonMessage();
        String str = message.createDeleteInsertMessage(docId, position, version, oldContent, newContent);
        System.out.println("Send new update Message");

        session.getBasicRemote().sendText("5:::" + str);
    }

    @Subscribe
    public synchronized void listenToBibDatabase(BibDatabaseContextChangedEvent event) {
        try {
            System.out.println("Event called" + event.getClass());
            BibtexDatabaseWriter<StringSaveSession> databaseWriter = new BibtexDatabaseWriter<>(StringSaveSession::new);
            StringSaveSession saveSession = databaseWriter.saveDatabase(newDb, new SavePreferences());
            String updatedcontent = saveSession.getStringValue().replace("\r\n", "\n");

            queue.put(updatedcontent);
        } catch (SaveException | InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        //TODO: We need to create a new event or add some parameters

        // return saveSession.getStringValue();

    }

    @Subscribe
    public synchronized void listenToSharelatexEntryMessage(ShareLatexEntryMessageEvent event) {

        JabRefExecutorService.INSTANCE.executeInterruptableTask(()->{
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
                sendHeartBeat();
                eventBus.post(new ShareLatexEntryMessageEvent());
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
                setLeftDoc(false);
                System.out.println("Message could be an entry ");

                int version = parser.getVersionFromBibTexJsonString(message);
                setVersion(version);

                String bibtexString = parser.getBibTexStringFromJsonMessage(message);
                setBibTexString(bibtexString);
                List<BibEntry> entries = parser.parseBibEntryFromJsonMessageString(message, prefs);

                System.out.println("Got new entries");
                eventBus.post(new ShareLatexEntryMessageEvent());

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
