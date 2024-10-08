package de.intranda.goobi.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
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
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;
import org.jdom2.transform.XSLTransformer;

import de.intranda.goobi.plugins.utils.WellcomeUtils;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.forms.MassImportForm;
import de.sub.goobi.helper.enums.PropertyType;
import de.sub.goobi.helper.exceptions.ImportPluginException;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
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

@PluginImplementation
@Log4j2
public class WellcomeMillenniumImport implements IImportPlugin, IPlugin {

    private static final long serialVersionUID = -5051232101802382789L;

    private String title = "Millennium Import";

    private static final String XSLT = ConfigurationHelper.getInstance().getXsltFolder() + "MARC21slim2MODS3.xsl";
    private static final String MODS_MAPPING_FILE = ConfigurationHelper.getInstance().getXsltFolder() + "mods_map.xml";
    private static final Namespace MARC = Namespace.getNamespace("marc", "http://www.loc.gov/MARC21/slim");

    private Prefs prefs;
    private String data = "";
    private File importFile = null;
    private String importFolder = "C:/Goobi/";
    private Map<String, String> map = new HashMap<>();
    private String currentIdentifier;

    private String currentWellcomeIdentifier;

    private List<String> currentCollectionList;

    // add IA download identifier
    private String currentIADownloadIdentifier;

    private MassImportForm form;

    public WellcomeMillenniumImport() {

        this.map.put("?Monographic", "Monograph");
        this.map.put("?continuing", "Periodical"); // not mapped
        this.map.put("?Notated music", "Monograph");
        this.map.put("?Manuscript notated music", "Monograph");
        this.map.put("?Cartographic material", "SingleMap");
        this.map.put("?Manuscript cartographic material", "SingleMap");
        this.map.put("?Projected medium", "Video");
        this.map.put("?Nonmusical sound recording", "Audio");
        this.map.put("?Musical sound recording", "Audio");
        this.map.put("?Two-dimensional nonprojectable graphic", "Artwork");
        this.map.put("?Computer file", "Monograph");
        this.map.put("?Kit", "Monograph");
        this.map.put("?Mixed materials", "Monograph");
        this.map.put("?Three-dimensional artefact or naturally occurring object", "3DObject");
        this.map.put("?Manuscript language material", "Archive");
        this.map.put("?BoundManuscript", "BoundManuscript");
    }

    public String getId() {
        return getTitle();
    }

    @Override
    public PluginType getType() {
        return PluginType.Import;
    }

    @Override
    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return getTitle();
    }

    @Override
    public Fileformat convertData() throws ImportPluginException {
        Fileformat ff = null;
        Document doc;
        try {

            doc = new SAXBuilder().build(new StringReader(this.data));
            if (doc != null && doc.hasRootElement()) {
                Element record = null;
                Element root = doc.getRootElement();
                if ("record".equals(root.getName())) {
                    record = root;
                } else {
                    record = doc.getRootElement().getChild("record", MARC);
                }
                List<Element> controlfields = record.getChildren("controlfield", MARC);
                List<Element> datafields = record.getChildren("datafield", MARC);
                String value907a = "";

                for (Element e907 : datafields) {
                    if ("907".equals(e907.getAttributeValue("tag"))) {
                        List<Element> subfields = e907.getChildren("subfield", MARC);
                        for (Element subfield : subfields) {
                            if ("a".equals(subfield.getAttributeValue("code"))) {
                                value907a = subfield.getText().replace(".", "");
                            }
                        }
                    }
                }
                boolean control001 = false;
                for (Element e : controlfields) {
                    if ("001".equals(e.getAttributeValue("tag"))) {
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
                log.debug(new XMLOutputter().outputString(docMods));

                ff = new MetsMods(this.prefs);
                DigitalDocument dd = new DigitalDocument();
                ff.setDigitalDocument(dd);

                Element eleMods = docMods.getRootElement();
                if ("modsCollection".equals(eleMods.getName())) {
                    eleMods = eleMods.getChild("mods", null);
                }

                // Determine the root docstruct type
                String dsType = "Monograph";
                if (eleMods.getChild("originInfo", null) != null) {
                    Element eleIssuance = eleMods.getChild("originInfo", null).getChild("issuance", null);
                    if (eleIssuance != null && this.map.get("?" + eleIssuance.getTextTrim()) != null) {
                        dsType = this.map.get("?" + eleIssuance.getTextTrim());
                    }
                }
                Element eleTypeOfResource = eleMods.getChild("typeOfResource", null);
                if (eleTypeOfResource != null && this.map.get("?" + eleTypeOfResource.getTextTrim()) != null) {
                    dsType = this.map.get("?" + eleTypeOfResource.getTextTrim());
                }
                log.debug("Docstruct type: " + dsType);

                DocStruct dsRoot = dd.createDocStruct(this.prefs.getDocStrctTypeByName(dsType));
                dd.setLogicalDocStruct(dsRoot);

                DocStruct dsBoundBook = dd.createDocStruct(this.prefs.getDocStrctTypeByName("BoundBook"));
                dd.setPhysicalDocStruct(dsBoundBook);

                // Collect MODS metadata
                WellcomeUtils.parseModsSection(MODS_MAPPING_FILE, this.prefs, dsRoot, dsBoundBook, eleMods);
                this.currentIdentifier = WellcomeUtils.getIdentifier(this.prefs, dsRoot);
                this.currentWellcomeIdentifier = WellcomeUtils.getWellcomeIdentifier(this.prefs, dsRoot);
                currentIADownloadIdentifier = WellcomeUtils.getAIDownloadIdentifier(prefs, dsRoot);
                // Add dummy volume to anchors
                if ("Periodical".equals(dsRoot.getType().getName()) || "MultiVolumeWork".equals(dsRoot.getType().getName())) {
                    DocStruct dsVolume = null;
                    if ("Periodical".equals(dsRoot.getType().getName())) {
                        dsVolume = dd.createDocStruct(this.prefs.getDocStrctTypeByName("PeriodicalVolume"));
                    } else if ("MultiVolumeWork".equals(dsRoot.getType().getName())) {
                        dsVolume = dd.createDocStruct(this.prefs.getDocStrctTypeByName("Volume"));
                    }
                    dsRoot.addChild(dsVolume);
                    Metadata mdId = new Metadata(this.prefs.getMetadataTypeByName("CatalogIDDigital"));
                    mdId.setValue(this.currentIdentifier + "_0001");
                    dsVolume.addMetadata(mdId);
                }

                // Add 'pathimagefiles'
                try {
                    Metadata mdForPath = new Metadata(this.prefs.getMetadataTypeByName("pathimagefiles"));
                    mdForPath.setValue("./" + this.currentIdentifier);
                    dsBoundBook.addMetadata(mdForPath);
                } catch (MetadataTypeNotAllowedException e1) {
                    log.error("MetadataTypeNotAllowedException while reading images", e1);
                } catch (DocStructHasNoTypeException e1) {
                    log.error("DocStructHasNoTypeException while reading images", e1);
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
                Metadata electronicEdition = new Metadata(this.prefs.getMetadataTypeByName("_electronicEdition"));
                electronicEdition.setValue("[Electronic ed.]");
                Metadata electronicPublisher = new Metadata(this.prefs.getMetadataTypeByName("_electronicPublisher"));
                electronicPublisher.setValue("Wellcome Trust");
                Metadata digitalOrigin = new Metadata(this.prefs.getMetadataTypeByName("_digitalOrigin"));
                digitalOrigin.setValue("reformatted digital");
                if (dsRoot.getType().isAnchor()) {
                    DocStruct ds = dsRoot.getAllChildren().get(0);
                    ds.addMetadata(dateDigitization);
                    ds.addMetadata(electronicEdition);

                } else {
                    dsRoot.addMetadata(dateDigitization);
                    dsRoot.addMetadata(electronicEdition);
                }
                dsRoot.addMetadata(placeOfElectronicOrigin);
                dsRoot.addMetadata(electronicPublisher);
                dsRoot.addMetadata(digitalOrigin);

                Metadata physicalLocation = new Metadata(this.prefs.getMetadataTypeByName("_digitalOrigin"));
                physicalLocation.setValue("Wellcome Trust");
                dsBoundBook.addMetadata(physicalLocation);
                File folderForImport = new File(getImportFolder() + File.separator + getProcessTitle() + File.separator + "import" + File.separator);
                WellcomeUtils.writeXmlToFile(folderForImport.getAbsolutePath(), getProcessTitle() + "_mrc.xml", doc);
            }
        } catch (JDOMException | IOException | PreferencesException | TypeNotAllowedForParentException | MetadataTypeNotAllowedException
                | TypeNotAllowedAsChildException e) {
            log.error(this.currentIdentifier + ": " + e.getMessage(), e);
            throw new ImportPluginException(e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new ImportPluginException(e);
        }
        return ff;
    }

    private void generateProperties(ImportObject io) {

        Processproperty pe = new Processproperty();
        pe.setTitel("importPlugin");
        pe.setWert(getTitle());
        pe.setType(PropertyType.STRING);
        io.getProcessProperties().add(pe);

        Processproperty pe2 = new Processproperty();
        pe2.setTitel("b-number");
        pe2.setWert(this.currentIdentifier);
        pe2.setType(PropertyType.STRING);
        io.getProcessProperties().add(pe2);

    }

    @Override
    public List<ImportObject> generateFiles(List<Record> records) {
        List<ImportObject> answer = new ArrayList<>();

        for (Record r : records) {
            form.addProcessToProgressBar();
            this.data = r.getData();
            this.currentCollectionList = r.getCollections();
            ImportObject io = new ImportObject();
            Fileformat ff = null;
            try {
                ff = convertData();
            } catch (ImportPluginException e1) {
                io.setErrorMessage(e1.getMessage());
            }

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
                    log.debug("Writing '" + fileName + "' into given folder...");
                    mm.write(fileName);
                    io.setMetsFilename(fileName);
                    io.setImportReturnValue(ImportReturnValue.ExportFinished);
                } catch (PreferencesException e) {
                    log.error(e.getMessage(), e);
                    io.setErrorMessage(e.getMessage());
                    io.setImportReturnValue(ImportReturnValue.InvalidData);
                } catch (WriteException e) {
                    log.error(e.getMessage(), e);
                    io.setImportReturnValue(ImportReturnValue.WriteError);
                    io.setErrorMessage(e.getMessage());
                }
            } else {
                io.setImportReturnValue(ImportReturnValue.InvalidData);
            }
            answer.add(io);
        }

        return answer;
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
                log.error("Could not parse '" + this.importFile + "'.");
            }
        } catch (JDOMException | IOException e) {
            log.error(e.getMessage(), e);
        }
        return ret;
    }

    @Override
    public List<Record> splitRecords(String records) {
        List<Record> ret = new ArrayList<>();

        // Split strings
        List<String> recordStrings = new ArrayList<>();
        BufferedReader inputStream = new BufferedReader(new StringReader(records));

        StringBuilder sb = new StringBuilder();
        String l;
        try {
            while ((l = inputStream.readLine()) != null) {
                if (l.length() > 0) {
                    if (l.startsWith("=LDR")) {
                        if (sb.length() > 0) {
                            recordStrings.add(sb.toString());
                        }
                        sb = new StringBuilder();
                    }
                    sb.append(l + "\n");
                }
            }
            if (sb.length() > 0) {
                recordStrings.add(sb.toString());
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

        // Convert strings to MARCXML records and add them to Record objects
        for (String s : recordStrings) {
            try {
                String data = convertTextToMarcXml(s);
                if (data != null) {
                    Record rec = new Record();
                    rec.setData(data);
                    ret.add(rec);
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }

        return ret;
    }

    @Override
    public List<String> splitIds(String ids) {
        return new ArrayList<>();
    }

    @Override
    public String getProcessTitle() {

        String returnvalue = "";
        if (currentIADownloadIdentifier != null) {
            returnvalue = currentIADownloadIdentifier.replaceAll("\\W", "_") + "_";
        }

        if (this.currentWellcomeIdentifier != null) {
            String temp = this.currentWellcomeIdentifier.replaceAll("\\W", "_");
            if (StringUtils.isNotBlank(temp)) {
                returnvalue = returnvalue + temp.toLowerCase() + "_";
            }
        }
        returnvalue = returnvalue + this.currentIdentifier;
        return returnvalue;
    }

    @Override
    public void setData(Record r) {
        this.data = r.getData();
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
        this.importFile = importFile;

    }

    @Override
    public void setPrefs(Prefs prefs) {
        this.prefs = prefs;

    }

    @Override
    public List<ImportType> getImportTypes() {
        List<ImportType> answer = new ArrayList<>();
        answer.add(ImportType.Record);
        answer.add(ImportType.FILE);
        answer.add(ImportType.FOLDER);

        return answer;
    }

    /**
     * 
     * @param text
     * @return
     * @throws IOException
     */
    private String convertTextToMarcXml(String text) throws IOException {
        if (StringUtils.isNotEmpty(text)) {
            Document doc = new Document();
            text = text.replace((char) 0x1E, ' ');
            BufferedReader reader = new BufferedReader(new StringReader(text));
            Element eleRoot = new Element("collection");
            doc.setRootElement(eleRoot);
            Element eleRecord = new Element("record");
            eleRoot.addContent(eleRecord);
            String str;
            while ((str = reader.readLine()) != null) {
                if (str.toUpperCase().startsWith("=LDR")) {
                    // Leader
                    Element eleLeader = new Element("leader");
                    eleLeader.setText(str.substring(7));
                    eleRecord.addContent(eleLeader);
                } else if (str.length() > 2) {
                    String tag = str.substring(1, 4);
                    if (tag.startsWith("00") && str.length() > 6) {
                        // Control field
                        str = str.substring(6);
                        Element eleControlField = new Element("controlfield");
                        eleControlField.setAttribute("tag", tag);
                        eleControlField.addContent(str);
                        eleRecord.addContent(eleControlField);
                    } else if (str.length() > 6) {
                        // Data field
                        String ind1 = str.substring(6, 7);
                        String ind2 = str.substring(7, 8);
                        str = str.substring(8);
                        Element eleDataField = new Element("datafield");
                        eleDataField.setAttribute("tag", tag);
                        eleDataField.setAttribute("ind1", !"\\".equals(ind1) ? ind1 : "");
                        eleDataField.setAttribute("ind2", !"\\".equals(ind2) ? ind2 : "");
                        Pattern p = Pattern.compile("[$]+[^$]+");
                        Matcher m = p.matcher(str);
                        while (m.find()) {
                            String sub = str.substring(m.start(), m.end());
                            Element eleSubField = new Element("subfield");
                            eleSubField.setAttribute("code", sub.substring(1, 2));
                            eleSubField.addContent(sub.substring(2));
                            eleDataField.addContent(eleSubField);
                        }
                        eleRecord.addContent(eleDataField);
                    }
                }
            }
            return new XMLOutputter().outputString(doc);
        }

        return null;
    }

    @Override
    public List<ImportProperty> getProperties() {
        return null;
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
                    record.setId(filename);
                    record.setData(new XMLOutputter().outputString(doc));
                    records.add(record);
                } else {
                    log.error("Could not parse '" + filename + "'.");
                }
            } catch (JDOMException | IOException e) {
                log.error(e.getMessage(), e);
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
        //  Auto-generated method stub
        return "";
    }

    @Override
    public String deleteDocstruct() {
        //  Auto-generated method stub
        return "";
    }

    @Override
    public List<DocstructElement> getCurrentDocStructs() {
        //  Auto-generated method stub
        return null;
    }

    @Override
    public List<String> getPossibleDocstructs() {
        //  Auto-generated method stub
        return null;
    }

    @Override
    public DocstructElement getDocstruct() {
        //  Auto-generated method stub
        return null;
    }

    @Override
    public void setDocstruct(DocstructElement dse) {
        //  Auto-generated method stub

    }

    @Override
    public void setForm(MassImportForm form) {
        this.form = form;

    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((title == null) ? 0 : title.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        WellcomeMillenniumImport other = (WellcomeMillenniumImport) obj;
        if (title == null) {
            if (other.title != null) {
                return false;
            }
        } else if (!title.equals(other.title)) {
            return false;
        }
        return true;
    }

}
