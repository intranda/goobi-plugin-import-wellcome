package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.goobi.beans.Processproperty;
import org.goobi.production.enums.ImportReturnValue;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.importer.DocstructElement;
import org.goobi.production.importer.ImportObject;
import org.goobi.production.importer.Record;
import org.goobi.production.plugin.interfaces.IImportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.properties.ImportProperty;
import org.goobi.production.properties.Type;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;

import de.intranda.goobi.plugins.utils.WellcomeUtils;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.forms.MassImportForm;
import de.sub.goobi.helper.enums.PropertyType;
import jakarta.faces.model.SelectItem;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.WriteException;
import ugh.fileformats.mets.MetsMods;

//@PluginImplementation
public class WellcomeImagesImport implements IImportPlugin, IPlugin {

    /** Logger for this class. */
    private static final Logger logger = Logger.getLogger(WellcomeImagesImport.class);

    private static final String NAME = "Wellcome Images Import";
    // private static final String VERSION = "0.0";

    private String data = "";
    private String importFolder = "";
    private File importFile;
    private Prefs prefs;

    private String currentTitle;

    //    private String currentAuthor;

    private String currentIdentifier;

    private List<String> currentCollectionList;

    private List<ImportProperty> properties = new ArrayList<>();

    public WellcomeImagesImport() {
        {
            ImportProperty ip = new ImportProperty();
            ip.setName("CollectionName1");
            ip.setType(Type.LIST);
            List<String> values = new ArrayList<>();
            values.add("Digitised");
            values.add("Born digital");
            ip.setPossibleValues(values.stream()
                    .map(v -> new SelectItem(v, v))
                    .toList());
            ip.setRequired(true);
            this.properties.add(ip);
        }
        {
            ImportProperty ip = new ImportProperty();
            ip.setName("CollectionName2");
            ip.setType(Type.TEXT);
            ip.setRequired(false);
            this.properties.add(ip);
        }
        {
            ImportProperty ip = new ImportProperty();
            ip.setName("securityTag");
            ip.setType(Type.LIST);
            List<String> values = new ArrayList<>();
            values.add("open");
            values.add("closed");
            ip.setPossibleValues(values.stream()
                    .map(v -> new SelectItem(v, v))
                    .toList());
            ip.setRequired(true);
            this.properties.add(ip);
        }
        {
            ImportProperty ip = new ImportProperty();
            ip.setName("schemaName");
            ip.setType(Type.LIST);
            List<String> values = new ArrayList<>();
            values.add("MIRO");
            ip.setPossibleValues(values.stream()
                    .map(v -> new SelectItem(v, v))
                    .toList());
            ip.setRequired(true);
            this.properties.add(ip);
        }
    }

    public String getId() {
        return NAME;
    }

    @Override
    public PluginType getType() {
        return PluginType.Import;
    }

    @Override
    public String getTitle() {
        return NAME;
    }

    public String getDescription() {
        return NAME;
    }

    @Override
    public void setPrefs(Prefs prefs) {
        this.prefs = prefs;

    }

    @Override
    public void setData(Record r) {
        this.data = r.getData();

    }

    @Override
    public Fileformat convertData() {
        Fileformat ff = null;
        Document doc;
        try {
            doc = new SAXBuilder().build(new StringReader(this.data));
            if (doc != null && doc.hasRootElement()) {
                ff = new MetsMods(this.prefs);
                DigitalDocument dd = new DigitalDocument();
                ff.setDigitalDocument(dd);
                Element image = doc.getRootElement();

                // generating DocStruct

                String dsType = "Monograph";
                DocStruct dsRoot = dd.createDocStruct(this.prefs.getDocStrctTypeByName(dsType));
                dd.setLogicalDocStruct(dsRoot);

                DocStructType dst = this.prefs.getDocStrctTypeByName("BoundBook");
                DocStruct dsBoundBook = dd.createDocStruct(dst);
                dd.setPhysicalDocStruct(dsBoundBook);

                Metadata path = new Metadata(this.prefs.getMetadataTypeByName("pathimagefiles"));
                path.setValue("./");
                dsBoundBook.addMetadata(path);

                // reading import file
                List<String> elementList = WellcomeUtils.getKeys(ConfigPlugins.getPluginConfig(this));
                for (String key : elementList) {
                    Element toTest = null;
                    // finding the right Element
                    if (key.contains(".")) {
                        String[] subElements = key.split(".");
                        Element sub = image;
                        for (String subString : subElements) {
                            if (sub.getChildren(subString).size() > 0) {
                                sub = sub.getChildren(subString).get(0);
                            }
                        }
                        if (!sub.getName().equals(subElements[subElements.length])) {
                            toTest = null;
                        } else {
                            toTest = sub;
                        }
                    } else if (image.getChild(key) != null) {
                        toTest = image.getChild(key);
                    }

                    if (toTest != null) {
                        if (toTest.getChild("_") != null) {
                            toTest = toTest.getChild("_");
                        }
                        String metadataName = WellcomeUtils.getValue(ConfigPlugins.getPluginConfig(this), key);
                        MetadataType mdt = this.prefs.getMetadataTypeByName(metadataName);
                        Metadata md = new Metadata(mdt);
                        md.setValue(toTest.getText());
                        dsRoot.addMetadata(md);
                    }
                }
                // Add collection names attached to the current record
                if (this.currentCollectionList != null) {
                    MetadataType mdTypeCollection = this.prefs.getMetadataTypeByName("singleDigCollection");
                    for (String collection : this.currentCollectionList) {
                        Metadata mdCollection = new Metadata(mdTypeCollection);
                        mdCollection.setValue(collection);
                        dsRoot.addMetadata(mdCollection);
                    }
                }
                Metadata dateDigitization = new Metadata(this.prefs.getMetadataTypeByName("_dateDigitization"));
                dateDigitization.setValue("2012");
                Metadata placeOfElectronicOrigin = new Metadata(this.prefs.getMetadataTypeByName("_placeOfElectronicOrigin"));
                placeOfElectronicOrigin.setValue("Wellcome Trust");
                Metadata _electronicEdition = new Metadata(this.prefs.getMetadataTypeByName("_electronicEdition"));
                _electronicEdition.setValue("[Electronic ed.]");
                Metadata _electronicPublisher = new Metadata(this.prefs.getMetadataTypeByName("_electronicPublisher"));
                _electronicPublisher.setValue("Wellcome Trust");
                Metadata _digitalOrigin = new Metadata(this.prefs.getMetadataTypeByName("_digitalOrigin"));
                _digitalOrigin.setValue("reformatted digital");
                if (dsRoot.getType().isAnchor()) {
                    DocStruct ds = dsRoot.getAllChildren().get(0);
                    ds.addMetadata(dateDigitization);
                    ds.addMetadata(_electronicEdition);

                } else {
                    dsRoot.addMetadata(dateDigitization);
                    dsRoot.addMetadata(_electronicEdition);
                }
                dsRoot.addMetadata(placeOfElectronicOrigin);
                dsRoot.addMetadata(_electronicPublisher);
                dsRoot.addMetadata(_digitalOrigin);

                Metadata physicalLocation = new Metadata(this.prefs.getMetadataTypeByName("_digitalOrigin"));
                physicalLocation.setValue("Wellcome Trust");
                dsBoundBook.addMetadata(physicalLocation);

                this.currentIdentifier = WellcomeUtils.getIdentifier(this.prefs, dsRoot);
                this.currentTitle = WellcomeUtils.getTitle(this.prefs, dsRoot);
                //                this.currentAuthor = WellcomeUtils.getAuthor(this.prefs, dsRoot);
            }
            File folderForImport = new File(getImportFolder() + File.separator + getProcessTitle() + File.separator + "import" + File.separator);
            WellcomeUtils.writeXmlToFile(folderForImport.getAbsolutePath(), getProcessTitle() + "_WellcomeImages", doc);
        } catch (JDOMException | IOException | PreferencesException | TypeNotAllowedForParentException | MetadataTypeNotAllowedException e) {
            logger.error(this.currentIdentifier + ": " + e.getMessage(), e);
            ff = null;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            ff = null;
        }

        return ff;
    }

    @Override
    public String getImportFolder() {
        return this.importFolder;
    }

    @Override
    public String getProcessTitle() {
        if (this.currentTitle != null) {
            String temp = this.currentTitle.replaceAll("\\W", "_");
            if (StringUtils.isNotBlank(temp)) {
                return temp.toLowerCase() + "_" + this.currentIdentifier;
            }
        }
        return this.currentIdentifier;
    }

    @Override
    public List<Record> splitRecords(String records) {
        List<Record> ret = new ArrayList<>();
        Record r = new Record();
        r.setData(records);
        ret.add(r);
        return ret;
    }

    private void generateProperties(ImportObject io) {
        for (ImportProperty ip : this.properties) {
            Processproperty pe = new Processproperty();
            pe.setTitel(ip.getName());
            pe.setContainer(ip.getContainer());
            pe.setCreationDate(new Date());
            if (Type.LIST.equals(ip.getType())) {
                pe.setType(PropertyType.LIST);
            } else if (Type.TEXT.equals(ip.getType())) {
                pe.setType(PropertyType.STRING);
            }
            pe.setWert(ip.getValue());
            io.getProcessProperties().add(pe);
        }

        {
            Processproperty pe = new Processproperty();
            pe.setTitel("importPlugin");
            pe.setWert(getTitle());
            pe.setType(PropertyType.STRING);
            io.getProcessProperties().add(pe);
        }
        {
            Processproperty pe = new Processproperty();
            pe.setTitel("b-number");
            pe.setWert(this.currentIdentifier);
            pe.setType(PropertyType.STRING);
            io.getProcessProperties().add(pe);
        }
    }

    @Override
    public List<ImportObject> generateFiles(List<Record> records) {
        List<ImportObject> answer = new ArrayList<>();

        for (Record r : records) {
            this.data = r.getData();
            this.currentCollectionList = r.getCollections();
            Fileformat ff = convertData();
            ImportObject io = new ImportObject();
            generateProperties(io);
            io.setProcessTitle(getProcessTitle());
            if (r.getId() != null) {
                io.setImportFileName(r.getId());
            }
            if (ff != null) {
                r.setId(this.currentIdentifier);
                try {
                    MetsMods mm = new MetsMods(this.prefs);
                    mm.setDigitalDocument(ff.getDigitalDocument());
                    String fileName = getImportFolder() + getProcessTitle() + ".xml";
                    logger.debug("Writing '" + fileName + "' into given folder...");
                    mm.write(fileName);
                    io.setMetsFilename(fileName);
                    io.setImportReturnValue(ImportReturnValue.ExportFinished);
                    // ret.put(getProcessTitle(), ImportReturnValue.ExportFinished);
                } catch (PreferencesException e) {
                    logger.error(e.getMessage(), e);
                    io.setImportReturnValue(ImportReturnValue.InvalidData);
                    // ret.put(getProcessTitle(), ImportReturnValue.InvalidData);
                } catch (WriteException e) {
                    logger.error(e.getMessage(), e);
                    io.setImportReturnValue(ImportReturnValue.WriteError);
                    // ret.put(getProcessTitle(), ImportReturnValue.WriteError);
                }
            } else {
                io.setImportReturnValue(ImportReturnValue.InvalidData);
                // ret.put(getProcessTitle(), ImportReturnValue.InvalidData);
            }
            answer.add(io);
        }

        return answer;

    }

    @Override
    public void setImportFolder(String folder) {
        this.importFolder = folder;

    }

    @Override
    public List<Record> generateRecordsFromFile() {
        List<Record> ret = new ArrayList<>();
        try {
            Document doc = new SAXBuilder().build(this.importFile);
            if (doc != null && doc.getRootElement() != null) {
                Record record = new Record();
                record.setData(new XMLOutputter().outputString(doc));
                ret.add(record);
            } else {
                logger.error("Could not parse '" + this.importFile + "'.");
            }
        } catch (JDOMException | IOException e) {
            logger.error(e.getMessage(), e);
        }
        return ret;
    }

    @Override
    public void setFile(File importFile) {
        this.importFile = importFile;
    }

    @Override
    public List<String> splitIds(String ids) {
        return new ArrayList<>();
    }

    @Override
    public List<ImportType> getImportTypes() {
        List<ImportType> answer = new ArrayList<>();
        answer.add(ImportType.Record);
        answer.add(ImportType.FILE);
        answer.add(ImportType.FOLDER);
        return answer;
    }

    @Override
    public List<ImportProperty> getProperties() {
        return this.properties;
    }

    public static void main(String[] args) throws PreferencesException, WriteException {
        File[] calms = new File("/home/robert/Downloads/wellcome/millenium/").listFiles();
        WellcomeImagesImport wci = new WellcomeImagesImport();
        Prefs prefs = new Prefs();
        wci.setImportFolder("/opt/digiverso/goobi/hotfolder/");
        wci.setPrefs(prefs);
        wci.prefs.loadPrefs("/opt/digiverso/goobi/rulesets/gdz.xml");
        List<Record> recordList = new ArrayList<>();
        for (File filename : calms) {
            wci.setFile(filename);
            recordList.addAll(wci.generateRecordsFromFile());
        }
        for (Record r : recordList) {
            wci.data = r.getData();
            Fileformat ff = wci.convertData();
            if (ff != null) {
                MetsMods mm = new MetsMods(prefs);
                mm.setDigitalDocument(ff.getDigitalDocument());
                String fileName = wci.getImportFolder() + wci.getProcessTitle() + ".xml";
                logger.debug("Writing '" + fileName + "' into given folder...");
                mm.write(fileName);
            }
        }
    }

    @Override
    public List<String> getAllFilenames() {
        List<String> answer = new ArrayList<>();
        String folder = ConfigPlugins.getPluginConfig(this).getString("importFolder", "/opt/digiverso/goobi/import/");
        File f = new File(folder);
        if (f.exists() && f.isDirectory()) {
            String[] files = f.list();
            for (String file : files) {
                answer.add(file);
            }
            Collections.sort(answer);
        }
        return answer;
    }

    @Override
    public List<Record> generateRecordsFromFilenames(List<String> filenames) {
        String folder = ConfigPlugins.getPluginConfig(this).getString("importFolder", "/opt/digiverso/goobi/import/");
        List<Record> records = new ArrayList<>();
        for (String filename : filenames) {
            File f = new File(folder, filename);
            try {
                Document doc = new SAXBuilder().build(f);
                if (doc != null && doc.getRootElement() != null) {
                    Record record = new Record();
                    record.setData(new XMLOutputter().outputString(doc));
                    record.setId(filename);
                    records.add(record);
                } else {
                    logger.error("Could not parse '" + filename + "'.");
                }
            } catch (JDOMException | IOException e) {
                logger.error(e.getMessage(), e);
            }

        }
        return records;
    }

    @Override
    public void deleteFiles(List<String> selectedFilenames) {
        String folder = ConfigPlugins.getPluginConfig(this).getString("importFolder", "/opt/digiverso/goobi/import/");
        for (String filename : selectedFilenames) {
            File f = new File(folder, filename);
            FileUtils.deleteQuietly(f);
        }
    }

    @Override
    public String addDocstruct() {
        // TODO Auto-generated method stub
        return "";
    }

    @Override
    public String deleteDocstruct() {
        // TODO Auto-generated method stub
        return "";
    }

    @Override
    public List<DocstructElement> getCurrentDocStructs() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<String> getPossibleDocstructs() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DocstructElement getDocstruct() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setDocstruct(DocstructElement dse) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setForm(MassImportForm form) {
        // TODO Auto-generated method stub

    }

}
