package org.jabref.logic.sharelatex;

import java.net.URISyntaxException;

import org.jabref.JabRefExecutorService;
import org.jabref.logic.importer.ImportFormatPreferences;
import org.jabref.model.database.BibDatabaseContext;

import org.junit.Test;

import static org.mockito.Mockito.mock;

public class SharelatexConnectorTest {

    @Test
    public void test() throws Exception {
        SharelatexConnector connector = new SharelatexConnector();
        connector.connectToServer("http://192.168.1.248", "joe@example.com", "test");
        connector.getProjects();
        //   connector.uploadFile("591188ed98ba55690073c29e",Paths.get("X:\\Users\\CS\\Documents\\_JABREFTEMP\\aaaaaaaaaaaaaa.bib"));
        //   connector.uploadFileWithWebClient("591188ed98ba55690073c29e",
        //         Paths.get("X:\\Users\\CS\\Documents\\_JABREFTEMP\\aaaaaaaaaaaaaa.bib"));

        JabRefExecutorService.INSTANCE.executeAndWait(() -> {

            try {
                connector.startWebsocketListener("5936d96b1bd5906b0082f53c", new BibDatabaseContext(),
                        mock(ImportFormatPreferences.class));
            } catch (URISyntaxException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        });
    }

}
