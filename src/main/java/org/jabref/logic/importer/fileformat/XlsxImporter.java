package org.jabref.logic.importer.fileformat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.jabref.logic.importer.Importer;
import org.jabref.logic.importer.ParserResult;
import org.jabref.logic.util.FileExtensions;
import org.jabref.model.entry.BibEntry;

import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/*
 * Classe responsável pelo parse do arquivo .xlsx em entradas para a database
 * Importante: o arquivo .xlsx deve ter o seguinte formato:
 * - Na primeira linha, há apenas a primeira célula contendo o identificador ":jabref:"
 * - Na primeira célula de cada linha há o identificador "type" seguido pelo tipo da entrada (ex. type=article)
 * - Em cada célula restante na linha há um identificador do campo e seu valor associado (ex. author=Joao da Silva)
 */
public class XlsxImporter extends Importer {

    /**
     * A biblioteca usada para o parse não aceita o tipo BufferedReader, portanto
     * esse método não será utilzado na prática
     */
    @Override
    public boolean isRecognizedFormat(BufferedReader reader) throws IOException {
        Objects.requireNonNull(reader);
        throw new UnsupportedOperationException(
                "XlsxImporter não suporta isRecognizedFormat(BufferedReader reader)."
                        + "Ao invés disso, use isRecognizedFormat(Path filePath, Charset defaultEncoding).");
    }

    /**
     * Retorna true se o arquivo é do tipo .xlsx e se possui a primeira célula
     * igual a :jabref:
     */
    @Override
    public boolean isRecognizedFormat(Path filePath, Charset defaultEncoding) {
        Objects.requireNonNull(filePath);
        File file = new File(filePath.toString());

        try (FileInputStream is = new FileInputStream(file);
                XSSFWorkbook myWorkBook = new XSSFWorkbook(is)) {

            //        Retorna a primeira planilha do arquivo
            XSSFSheet mySheet = myWorkBook.getSheetAt(0);

            //        Cria um iterator para as linhas da planilha
            Iterator<Row> rowIterator = mySheet.iterator();
            Row row = rowIterator.next();
            //            Pega a primeira célula da primeira linha
            Cell cell = row.getCell(row.getFirstCellNum());

            if (cell.getStringCellValue().equals(":jabref:")) {
                return true;
            }

            return false;

        } catch (NotOfficeXmlFileException ex) {
            return false;
        } catch (IOException exception) {
            return false;
        }
    }

    /**
     * A biblioteca usada para o parse não aceita o tipo BufferedReader, portanto
     * esse método não será utilzado na prática
     */
    @Override
    public ParserResult importDatabase(BufferedReader input) throws IOException {
        Objects.requireNonNull(input);
        throw new UnsupportedOperationException(
                "XlsxImporter não suporta importDatabase(BufferedReader reader)."
                        + "Ao invés disso, usar importDatabase(Path filePath, Charset defaultEncoding).");
    }

    //
    /**
     * Realiza o parse do arquivo para a database.
     * Uma biblioteca adicional é utilizada para trabalhar com arquivos .xlsx
     * Apache POI (Versão utlizada: 3.16)
     * Mais informações, visite: http://www.java67.com/2014/09/how-to-read-write-xlsx-file-in-java-apache-poi-example.html
     */
    @Override
    public ParserResult importDatabase(Path filePath, Charset encoding) {
        List<BibEntry> entries = new ArrayList<>();
        File file = new File(filePath.toString());

        try (FileInputStream is = new FileInputStream(file);
                XSSFWorkbook myWorkBook = new XSSFWorkbook(is)) {

            //            Retorna a primeira planilha do arquivo
            XSSFSheet mySheet = myWorkBook.getSheetAt(0);
            //            Cria um iterator para as linhas da planilha
            Iterator<Row> rowIterator = mySheet.iterator();

            Row row = rowIterator.next();
            //            Linha
            while (rowIterator.hasNext()) {
                row = rowIterator.next();
                BibEntry entry = new BibEntry();
                //                Cria um iterator para as celulas de cada linha
                Iterator<Cell> cellIterator = row.cellIterator();
                //                Coluna
                while (cellIterator.hasNext()) {
                    Cell cell = cellIterator.next();
                    int i = cell.getStringCellValue().indexOf("=");
                    //                    Cria as entradas
                    if (i != -1) {
                        String cellStr = cell.getStringCellValue();
                        if (cellStr.substring(0, i + 1).equals("type=")) {
                            entry.setType(cell.getStringCellValue().substring(i + 1));
                        } else {
                            entry.setField(cellStr.substring(0, i), cellStr.substring(i + 1));
                        }
                    }
                }
                entries.add(entry);
            }
        } catch (IOException exception) {
            return ParserResult.fromError(exception);
        }
        return new ParserResult(entries);
    }

    @Override
    public String getName() {
        return "XLSX";
    }

    @Override
    public FileExtensions getExtensions() {
        return FileExtensions.XLSX;
    }

    @Override
    public String getDescription() {
        return "Imports a XLSX exported file";
    }

    @Override
    public String getId() {
        return "xlsx";
    }

}
