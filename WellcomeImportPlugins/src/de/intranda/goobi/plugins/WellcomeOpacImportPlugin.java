package de.intranda.goobi.plugins;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.commons.io.input.BOMInputStream;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.importer.DocstructElement;
import org.goobi.production.importer.ImportObject;
import org.goobi.production.importer.Record;
import org.goobi.production.plugin.interfaces.IImportPluginVersion2;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.properties.ImportProperty;

import de.sub.goobi.forms.MassImportForm;
import de.sub.goobi.helper.exceptions.ImportPluginException;
import lombok.Data;
import lombok.extern.log4j.Log4j;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;

@Data
@Log4j
//@PluginImplementation
public class WellcomeOpacImportPlugin implements IImportPluginVersion2, IPlugin {

    private PluginType type = PluginType.Import;
    private String title = "plugin_intranda_opac_import";

    private Prefs prefs;

    private String importFolder;

    private MassImportForm form;

    private File file;

    private List<ImportType> importTypes = Collections.emptyList();

    private static final String SPLIT_REGEX = "[\\s;,]";

    private static final String BNUMBER_REGEX = "\\.?b\\d+[xX]?";

    @PostConstruct
    public void init() {
        importTypes = new ArrayList<>();
        importTypes.add(ImportType.Record);
        importTypes.add(ImportType.ID);
        importTypes.add(ImportType.FILE);
    }


    @Override
    public List<ImportObject> generateFiles(List<Record> records) {
        List<ImportObject> answer = new ArrayList<>(records.size());
        for (Record record : records) {
            String identifier = record.getId();


        }

        // TODO Auto-generated method stub
        return answer;
    }

    @Override
    public List<Record> splitRecords(String records) {
        List<Record> recordList = new ArrayList<>();
        String[] parts = records.split(SPLIT_REGEX);
        for (String part : parts) {
            Record r = new Record();
            r.setData(part);
            r.setId(part);
            recordList.add(r);
        }
        return recordList;
    }



    @Override
    public Fileformat convertData() throws ImportPluginException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getProcessTitle() {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public List<Record> generateRecordsFromFile() {
        List<Record> records = new ArrayList<>();
        // read excel file, get identifier list from first column
        try (InputStream fos = new FileInputStream(this.file)) {

            BOMInputStream in = new BOMInputStream(fos, false);

            Workbook wb = WorkbookFactory.create(in);

            Sheet sheet = wb.getSheetAt(0);

            Iterator<Row> rowIterator = sheet.rowIterator();

            // TODO remove header row?
            //  rowIterator.next();

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                Iterator<Cell> cellIterator = row.cellIterator();

                while (cellIterator.hasNext()) {
                    Cell cell = cellIterator.next();
                    String value = null;
                    if (cell.getCellType() == CellType.STRING) {
                        value = cell.getStringCellValue();
                    } else if (cell.getCellType() == CellType.NUMERIC) {
                        value = Integer.toString((int) cell.getNumericCellValue());
                    }
                    if (value != null && value.matches(BNUMBER_REGEX)) {
                        // TODO check, if it is a valid b-number
                        Record r = new Record();
                        r.setId(value);
                        r.setData(value);
                        records.add(r);
                    }
                }

            }

        } catch (IOException e) {
            log.error(e);
        } catch (EncryptedDocumentException e) {
            log.error(e);
        }
        return records;
    }

    @Override
    public List<Record> generateRecordsFromFilenames(List<String> filenames) {
        return null;
    }

    @Override
    public List<String> splitIds(String ids) {
        // tokenize  comma, semicolon, space, new line
        String[] parts = ids.split(SPLIT_REGEX);
        return Arrays.asList(parts);
    }


    @Override
    public void setData(Record r) {
    }

    @Override
    public List<ImportProperty> getProperties() {
        return null;
    }

    @Override
    public List<String> getAllFilenames() {
        return null;
    }

    @Override
    public void deleteFiles(List<String> selectedFilenames) {
    }

    @Override
    public List<? extends DocstructElement> getCurrentDocStructs() {
        return null;
    }

    @Override
    public String deleteDocstruct() {
        return null;
    }

    @Override
    public String addDocstruct() {
        return null;
    }

    @Override
    public List<String> getPossibleDocstructs() {
        return null;
    }

    @Override
    public DocstructElement getDocstruct() {
        return null;
    }

    @Override
    public void setDocstruct(DocstructElement dse) {
    }

    @Override
    public boolean isRunnableAsGoobiScript() {
        return true;
    }

}
