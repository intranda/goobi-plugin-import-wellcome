package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import net.xeoh.plugins.base.annotations.PluginImplementation;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.goobi.production.importer.DocstructElement;
import org.goobi.production.importer.ImportObject;
import org.goobi.production.importer.Record;
import org.goobi.production.enums.ImportReturnValue;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IImportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.properties.ImportProperty;
import org.goobi.production.properties.Type;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;
import org.jdom2.transform.XSLTransformer;

import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.WriteException;
import ugh.fileformats.mets.MetsMods;
import de.intranda.goobi.plugins.utils.WellcomeDocstructElement;
import de.intranda.goobi.plugins.utils.WellcomeUtils;

import org.goobi.beans.Processproperty;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.enums.PropertyType;
import de.sub.goobi.helper.exceptions.ImportPluginException;

@PluginImplementation
public class AutomaticMMOImportPlugin implements IImportPlugin, IPlugin {

    /** Logger for this class. */
    private static final Logger logger = Logger.getLogger(MultipleManifestationMillenniumImport.class);
    private static final String COLLECTION_NAME = "19th century";

    private static final String NAME = "AutomaticMMOImportPlugin";
    //	private static final String ID = "MultipleManifestationMillenniumImport";
    // private static final String VERSION = "0.1";
    private static final String XSLT = ConfigurationHelper.getInstance().getXsltFolder() + "MARC21slim2MODS3.xsl";
    private static final String MODS_MAPPING_FILE = ConfigurationHelper.getInstance().getXsltFolder() + "mods_map_multi.xml";
    private static final Namespace MARC = Namespace.getNamespace("marc", "http://www.loc.gov/MARC21/slim");

    private Prefs prefs;
    private Record currentRecord;
    //    private File importFile = null;
    private String importFolder = "C:/Goobi/";
    //    private String currentIdentifier;
    // private String currentTitle;
    private String currentWellcomeIdentifier;
    // private String currentWellcomeLeader6;
    // private String currentAuthor;
    private List<ImportProperty> properties = new ArrayList<ImportProperty>();

    //    private List<WellcomeDocstructElement> currentDocStructs = new ArrayList<WellcomeDocstructElement>();
    //    private WellcomeDocstructElement docstruct;
    //    private HashMap<String, String> structType = new HashMap<String, String>();;

    public AutomaticMMOImportPlugin() {

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

    @Override
    public String getDescription() {
        return NAME;
    }

    @Override
    public Fileformat convertData() throws ImportPluginException {
        Fileformat ff = null;
        Document doc;
        try {
            String filename = currentRecord.getId().replace(".xml", "").replace("_marc", "");
            String anchorIdentifier = filename.substring(0, filename.indexOf("_"));
            String order = filename.substring(filename.indexOf("_") + 1);
            doc = new SAXBuilder().build(new StringReader(this.currentRecord.getData()));
            if (doc != null && doc.hasRootElement()) {
                Element root = doc.getRootElement();
                Element record = null;
                if (root.getName().equalsIgnoreCase("record")) {
                    record = root;
                } else {
                    record = doc.getRootElement().getChild("record", MARC);
                }
                List<Element> controlfields = record.getChildren("controlfield", MARC);
                List<Element> datafields = record.getChildren("datafield", MARC);
                String value907a = "";

                for (Element e907 : datafields) {
                    if (e907.getAttributeValue("tag").equals("907")) {
                        List<Element> subfields = e907.getChildren("subfield", MARC);
                        for (Element subfield : subfields) {
                            if (subfield.getAttributeValue("code").equals("a")) {
                                value907a = subfield.getText().replace(".", "");
                            }
                        }
                    }
                }
                boolean control001 = false;
                for (Element e : controlfields) {
                    if (e.getAttributeValue("tag").equals("001")) {
                        e.setText(value907a);
                        control001 = true;
                        break;
                    }
                }
                if (!control001) {
                    Element controlfield001 = new Element("controlfield", MARC);
                    controlfield001.setAttribute("tag", "001");
                    controlfield001.setText(value907a);
                    record.addContent(controlfield001);
                }

                XSLTransformer transformer = new XSLTransformer(XSLT);

                Document docMods = transformer.transform(doc);
                logger.debug(new XMLOutputter().outputString(docMods));

                ff = new MetsMods(this.prefs);
                DigitalDocument dd = new DigitalDocument();
                ff.setDigitalDocument(dd);

                Element eleMods = docMods.getRootElement();
                if (eleMods.getName().equals("modsCollection")) {
                    eleMods = eleMods.getChild("mods", null);
                }

                // Determine the root docstruct type
                String dsType = "MultipleManifestation";
                String volumeStructType = "MonographManifestation";

                DocStruct dsRoot = dd.createDocStruct(this.prefs.getDocStrctTypeByName(dsType));
                dd.setLogicalDocStruct(dsRoot);

                DocStruct dsBoundBook = dd.createDocStruct(this.prefs.getDocStrctTypeByName("BoundBook"));
                dd.setPhysicalDocStruct(dsBoundBook);
                DocStruct dsVolume = dd.createDocStruct(this.prefs.getDocStrctTypeByName(volumeStructType));
                dsRoot.addChild(dsVolume);

                // Collect MODS metadata
                WellcomeUtils.parseModsSectionForMultivolumes(MODS_MAPPING_FILE, this.prefs, dsRoot, dsVolume, dsBoundBook, eleMods);

                Metadata volumeType = new Metadata(this.prefs.getMetadataTypeByName("_volume"));
                volumeType.setValue(order);
                
                // order zweistellig
                int orderNo = Integer.parseInt(order);
                if (orderNo < 10) {
                    order = "0" + orderNo;
                } else {
                    order = "" + orderNo;
                }
                // add publication year to order
                MetadataType yearType = prefs.getMetadataTypeByName("PublicationYear");
                if (dsRoot.getAllMetadataByType(yearType) != null && !dsRoot.getAllMetadataByType(yearType).isEmpty()) {
                    Metadata md = dsRoot.getAllMetadataByType(yearType).get(0);
                    if (md.getValue().matches("\\d\\d\\d\\d")) {
                        order = md.getValue() + order;
                    }
                } else if (dsVolume.getAllMetadataByType(yearType) != null && !dsVolume.getAllMetadataByType(yearType).isEmpty()) {
                    Metadata md = dsVolume.getAllMetadataByType(yearType).get(0);
                    if (md.getValue().matches("\\d\\d\\d\\d")) {
                        order = md.getValue() + order;
                    }
                }

                this.currentWellcomeIdentifier = WellcomeUtils.getWellcomeIdentifier(this.prefs, dsRoot);

                MetadataType mdt = this.prefs.getMetadataTypeByName("CatalogIDDigital");

                if (dsRoot.getAllMetadataByType(mdt) != null && !dsRoot.getAllMetadataByType(mdt).isEmpty()) {
                    Metadata md = dsRoot.getAllMetadataByType(mdt).get(0);
                    md.setValue(anchorIdentifier);
                } else {
                    Metadata mdId = new Metadata(mdt);
                    mdId.setValue(anchorIdentifier);
                    dsRoot.addMetadata(mdId);
                }

                Metadata mdId = new Metadata(mdt);
                mdId.setValue(filename);
                dsVolume.addMetadata(mdId);
                Metadata currentNo = new Metadata(this.prefs.getMetadataTypeByName("CurrentNo"));
                currentNo.setValue(order);
                dsVolume.addMetadata(currentNo);
                Metadata CurrentNoSorting = new Metadata(this.prefs.getMetadataTypeByName("CurrentNoSorting"));
                CurrentNoSorting.setValue(order);
                dsVolume.addMetadata(CurrentNoSorting);

                Metadata manifestationType = new Metadata(this.prefs.getMetadataTypeByName("_ManifestationType"));

                manifestationType.setValue("General");


                dsVolume.addMetadata(volumeType);
                
                dsRoot.addMetadata(manifestationType);

                // Add 'pathimagefiles'
                try {
                    Metadata mdForPath = new Metadata(this.prefs.getMetadataTypeByName("pathimagefiles"));
                    mdForPath.setValue("./" + filename);
                    dsBoundBook.addMetadata(mdForPath);
                } catch (MetadataTypeNotAllowedException e1) {
                    logger.error("MetadataTypeNotAllowedException while reading images", e1);
                } catch (DocStructHasNoTypeException e1) {
                    logger.error("DocStructHasNoTypeException while reading images", e1);
                }

                // Add collection names attached to the current record

                MetadataType mdTypeCollection = this.prefs.getMetadataTypeByName("singleDigCollection");

                Metadata mdCollection = new Metadata(mdTypeCollection);
                mdCollection.setValue(COLLECTION_NAME);
                dsRoot.addMetadata(mdCollection);

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
                File folderForImport = new File(getImportFolder() + File.separator + getProcessTitle() + File.separator + "import" + File.separator);
                WellcomeUtils.writeXmlToFile(folderForImport.getAbsolutePath(), getProcessTitle() + "_mrc.xml", doc);
            }
        } catch (JDOMException | IOException | PreferencesException | TypeNotAllowedForParentException | MetadataTypeNotAllowedException
                | TypeNotAllowedAsChildException e) {
            logger.error(currentRecord.getId() + ": " + e.getMessage(), e);
            throw new ImportPluginException(e);
        }

        return ff;
    }

    private void generateProperties(ImportObject io) {
        for (ImportProperty ip : this.properties) {
            if (!ip.getName().equals("Multiple manifestation type")) {
                Processproperty pe = new Processproperty();
                pe.setTitel(ip.getName());
                pe.setContainer(ip.getContainer());
                pe.setCreationDate(new Date());
                pe.setIstObligatorisch(false);
                if (ip.getType().equals(Type.LIST)) {
                    pe.setType(PropertyType.List);
                } else if (ip.getType().equals(Type.TEXT)) {
                    pe.setType(PropertyType.String);
                }
                pe.setWert(ip.getValue());
                io.getProcessProperties().add(pe);
            }
        }

        {
            Processproperty pe = new Processproperty();
            pe.setTitel("importPlugin");
            pe.setWert(getTitle());
            pe.setType(PropertyType.String);
            io.getProcessProperties().add(pe);
        }
        {
            Processproperty pe = new Processproperty();
            pe.setTitel("b-number");
            pe.setWert(currentRecord.getId().replace(".xml", "").replace("_marc", ""));
            pe.setType(PropertyType.String);
            io.getProcessProperties().add(pe);
        }
        {
            Processproperty pe = new Processproperty();
            pe.setTitel("CollectionName1");
            pe.setWert("Digitised");
            pe.setType(PropertyType.String);
            io.getProcessProperties().add(pe);
        }
        {
            Processproperty pe = new Processproperty();
            pe.setTitel("schemaName");
            pe.setWert("Millennium");
            pe.setType(PropertyType.String);
            io.getProcessProperties().add(pe);
        }


        //		{
        //			Prozesseigenschaft pe = new Prozesseigenschaft();
        //			pe.setTitel("CatalogueURL");
        //			pe.setWert("http://catalogue.example.com/db/3/?id=" + this.currentIdentifier);
        //			pe.setType(PropertyType.String);
        //			io.getProcessProperties().add(pe);
        //		}
    }

    @Override
    public List<ImportObject> generateFiles(List<Record> records) {
        List<ImportObject> answer = new ArrayList<ImportObject>();

        if (records.size() > 0) {
            for (Record r : records) {
                //            Record r = records.get(0);
                this.currentRecord = r;

                ImportObject io = new ImportObject();
                Fileformat ff = null;
                try {
                    ff = convertData();
                } catch (ImportPluginException e1) {
                    io.setErrorMessage(e1.getMessage());
                }
                if (r.getId() != null) {
                    io.setImportFileName(r.getId());
                }
                generateProperties(io);
                io.setProcessTitle(getProcessTitle());
                if (ff != null) {
                    r.setId(currentRecord.getId().replace(".xml", ""));
                    try {
                        MetsMods mm = new MetsMods(this.prefs);
                        mm.setDigitalDocument(ff.getDigitalDocument());
                        String fileName = getImportFolder() + getProcessTitle() + ".xml";
                        logger.debug("Writing '" + fileName + "' into given folder...");
                        mm.write(fileName);
                        io.setMetsFilename(fileName);
                        io.setImportReturnValue(ImportReturnValue.ExportFinished);
                        // ret.put(getProcessTitle(),
                        // ImportReturnValue.ExportFinished);
                    } catch (PreferencesException e) {
                        logger.error(e.getMessage(), e);
                        io.setErrorMessage(e.getMessage());
                        io.setImportReturnValue(ImportReturnValue.InvalidData);
                        // ret.put(getProcessTitle(),
                        // ImportReturnValue.InvalidData);
                    } catch (WriteException e) {
                        logger.error(e.getMessage(), e);
                        io.setImportReturnValue(ImportReturnValue.WriteError);
                        io.setErrorMessage(e.getMessage());
                        // ret.put(getProcessTitle(),
                        // ImportReturnValue.WriteError);
                    }
                } else {
                    io.setImportReturnValue(ImportReturnValue.InvalidData);
                    // ret.put(getProcessTitle(),
                    // ImportReturnValue.InvalidData);
                }
                answer.add(io);
            }
        }
        return answer;
    }

    @Override
    public List<Record> generateRecordsFromFile() {
        //       
        return null;
    }

    @Override
    public List<Record> splitRecords(String records) {

        return null;
    }

    @Override
    public List<String> splitIds(String ids) {
        return new ArrayList<String>();
    }

    @Override
    public String getProcessTitle() {

        if (this.currentWellcomeIdentifier != null) {
            String temp = this.currentWellcomeIdentifier.replaceAll("\\W", "_");
            if (StringUtils.isNotBlank(temp)) {
                return temp.toLowerCase() + "_" + currentRecord.getId().replace(".xml", "").replace("_marc", "");
            }
        }
        return currentRecord.getId().replace(".xml", "").replace("_marc", "");
    }

    @Override
    public void setData(Record r) {
        this.currentRecord = r;
    }

    @Override
    public String getImportFolder() {
        return this.importFolder;
    }

    @Override
    public void setImportFolder(String folder) {
        this.importFolder = folder;
    }

    @Override
    public void setFile(File importFile) {
    }

    @Override
    public void setPrefs(Prefs prefs) {
        this.prefs = prefs;

    }

    @Override
    public List<ImportType> getImportTypes() {
        // TODO  return new ArrayList<ImportType>();
        List<ImportType> answer = new ArrayList<ImportType>();
        answer.add(ImportType.FOLDER);
        return answer;
    }

    @Override
    public List<ImportProperty> getProperties() {
        return this.properties;
    }

    @Override
    public List<String> getAllFilenames() {
        List<String> answer = new ArrayList<String>();
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
        List<Record> records = new ArrayList<Record>();
        for (String filename : filenames) {
            File f = new File(folder, filename);
            try {
                Document doc = new SAXBuilder().build(f);
                if (doc != null && doc.getRootElement() != null) {
                    Record record = new Record();
                    record.setId(filename);
                    record.setData(new XMLOutputter().outputString(doc));
                    records.add(record);
                } else {
                    logger.error("Could not parse '" + filename + "'.");
                }
            } catch (JDOMException e) {
                logger.error(e.getMessage(), e);
            } catch (IOException e) {
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

        return "";
    }

    @Override
    public String deleteDocstruct() {

        return "";
    }

    @Override
    public List<? extends DocstructElement> getCurrentDocStructs() {
        return null;
    }

    @Override
    public List<String> getPossibleDocstructs() {

        return null;
    }

    @Override
    public WellcomeDocstructElement getDocstruct() {
        return null;
    }

    @Override
    public void setDocstruct(DocstructElement dse) {
    }

    public void setDocstruct(WellcomeDocstructElement dse) {
    }
}
