package org.jabref.es2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.jabref.Globals;
import org.jabref.logic.importer.ImportFormatReader;
import org.jabref.logic.importer.ParserResult;
import org.jabref.logic.importer.fileformat.XlsxImporter;
import org.jabref.logic.util.FileExtensions;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.preferences.JabRefPreferences;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class XlsxImporterTest {

    XlsxImporter importer;

    @Before
    public void setUp() throws Exception {
        Globals.prefs = JabRefPreferences.getInstance();
        importer = new XlsxImporter();
    }

    @Test
    public void testeImportDatabase() throws URISyntaxException {
        //        Cria uma nova database
        BibDatabase database = new BibDatabase();
        //        Prepara o arquivo para importar
        Path path = Paths.get(XlsxImporterTest.class.getResource("testeXlsxImporter.xlsx").toURI());
        //        Importa o arquivo para a database
        ParserResult pr = importer.importDatabase(path, Globals.prefs.getDefaultEncoding());
        ImportFormatReader.UnknownFormatImport importResult = new ImportFormatReader.UnknownFormatImport(importer.getName(), pr);
        ParserResult pares = importResult.parserResult;
        database = pares.getDatabase();
        //        Pega a lista de entradas da database
        List<BibEntry> entries = database.getEntries();

        //        Verificação rápida se há apenas duas entradas (como consta no arquivo importado)
        assertEquals(2, entries.size());

        //        Verifica as duas entradas do arquivo importado estão de acordo após a importação
        for (BibEntry entry : entries) {
            if (entry.getCiteKey().equals("teste2")) {
                assertEquals(Optional.of("title_teste"), entry.getField("title"));
                assertEquals(Optional.of("publisher_teste"), entry.getField("publisher"));
                assertEquals(Optional.of("2017"), entry.getField("year"));
                assertEquals(Optional.of("author_teste"), entry.getField("author"));
                assertEquals(Optional.of("editor_teste"), entry.getField("editor"));
            } else if (entry.getCiteKey().equals("teste1")) {
                assertEquals(Optional.of("author_teste"), entry.getField("author"));
                assertEquals(Optional.of("title_teste"), entry.getField("title"));
                assertEquals(Optional.of("journal_teste"), entry.getField("journal"));
                assertEquals(Optional.of("2017"), entry.getField("year"));
            }
        }

    }

    @Test(expected = UnsupportedOperationException.class)
    public void testeImportDatabaseReader() throws IOException {
        InputStream stream = XlsxImporterTest.class.getResourceAsStream("testeXlsxImporter.xlsx");
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Globals.prefs.getDefaultEncoding()));
        importer.importDatabase(reader);
    }

    @Test
    public void testeIsRecognizedFormat() throws URISyntaxException {
        Path path = Paths.get(XlsxImporterTest.class.getResource("testeXlsxImporter.xlsx").toURI());
        assertTrue(importer.isRecognizedFormat(path, Globals.prefs.getDefaultEncoding()));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testeIsRecognizedFormatReader() throws IOException {
        InputStream stream = XlsxImporterTest.class.getResourceAsStream("testeXlsxImporter.xlsx");
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Globals.prefs.getDefaultEncoding()));
        importer.isRecognizedFormat(reader);
    }

    @Test
    public void testeGetExtensions() {
        assertEquals(FileExtensions.XLSX, importer.getExtensions());
    }

    @Test
    public void testeGetDescription() {
        assertEquals("Imports a XLSX exported file", importer.getDescription());
    }

    @Test
    public void testeGetId() {
        assertEquals("xlsx", importer.getId());
    }


}
