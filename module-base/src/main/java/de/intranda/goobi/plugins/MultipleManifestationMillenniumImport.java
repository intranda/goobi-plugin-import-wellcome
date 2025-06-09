package de.intranda.goobi.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.goobi.production.properties.Type;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;
import org.jdom2.transform.XSLTransformer;

import de.intranda.goobi.plugins.utils.WellcomeDocstructElement;
import de.intranda.goobi.plugins.utils.WellcomeUtils;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.forms.MassImportForm;
import de.sub.goobi.helper.enums.PropertyType;
import de.sub.goobi.helper.exceptions.ImportPluginException;
import jakarta.faces.model.SelectItem;
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

@Log4j2
@PluginImplementation
public class MultipleManifestationMillenniumImport implements IImportPlugin, IPlugin {

    private static final long serialVersionUID = -7999981700008316227L;
    private String title = "Multiple Manifestation Millennium Import";

    private static final String XSLT = ConfigurationHelper.getInstance().getXsltFolder() + "MARC21slim2MODS3.xsl";
    private static final String MODS_MAPPING_FILE = ConfigurationHelper.getInstance().getXsltFolder() + "mods_map_multi.xml";
    private static final Namespace MARC = Namespace.getNamespace("marc", "http://www.loc.gov/MARC21/slim");

    private Prefs prefs;
    private String data = "";
    private File importFile = null;
    private String importFolder = "C:/Goobi/";
    private String currentIdentifier;

    private String currentWellcomeIdentifier;

    private List<String> currentCollectionList;
    private transient List<ImportProperty> properties = new ArrayList<>();
    private transient List<WellcomeDocstructElement> currentDocStructs = new ArrayList<>();
    private transient WellcomeDocstructElement docstruct;
    private HashMap<String, String> structType = new HashMap<>();

    private MassImportForm form;

    public MultipleManifestationMillenniumImport() {
        structType.put("Archive", "ArchiveManifestation");
        structType.put("Artwork", "ArtworkManifestation");
        structType.put("Audio", "AudioManifestation");
        structType.put("Born digital", "BornDigitalManifestation");
        structType.put("Bound manuscript", "BoundManuscriptManifestation");
        structType.put("Monograph", "MonographManifestation");
        structType.put("Multiple copy", "MultipleCopyManifestation");
        structType.put("Multiple volume", "MultipleVolumeManifestation");
        structType.put("Multiple volume multiple copy", "MultipleVolumeMultipleCopyManifestation");
        structType.put("Poster image", "PosterImageManifestation");
        structType.put("Still images", "StillImageManifestation");
        structType.put("Transcript", "TranscriptManifestation");
        structType.put("Video", "VideoManifestation");
        structType.put("Report", "PDFReport");
        structType.put("Annex", "PDFAnnex");

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

        ImportProperty ip2 = new ImportProperty();
        ip2.setName("CollectionName2");
        ip2.setType(Type.TEXT);
        ip2.setRequired(false);
        this.properties.add(ip2);

        ImportProperty ip3 = new ImportProperty();
        ip3.setName("securityTag");
        ip3.setType(Type.LIST);
        values = new ArrayList<>();
        values.add("open");
        values.add("closed");
        ip3.setPossibleValues(values.stream()
                .map(v -> new SelectItem(v, v))
                .toList());
        ip3.setRequired(true);
        this.properties.add(ip3);

        ImportProperty ip4 = new ImportProperty();
        ip4.setName("schemaName");
        ip4.setType(Type.LIST);
        values = new ArrayList<>();
        values.add("Millennium");
        ip4.setPossibleValues(values.stream()
                .map(v -> new SelectItem(v, v))
                .toList());
        ip4.setRequired(true);
        this.properties.add(ip4);

        ImportProperty ip5 = new ImportProperty();
        ip5.setName("Multiple manifestation type");
        ip5.setType(Type.LIST);
        values = new ArrayList<>();
        values.add("General");
        values.add("Video & transcript & poster image");
        values.add("Video & transcript");
        values.add("Audio & transcript");
        values.add("Audio & transcript & poster image");
        ip5.setPossibleValues(values.stream()
                .map(v -> new SelectItem(v, v))
                .toList());
        ip5.setRequired(true);
        this.properties.add(ip5);

    }

    @Override
    public PluginType getType() {
        return PluginType.Import;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public Fileformat convertData() throws ImportPluginException {
        Fileformat ff = null;
        Document doc;
        try {

            doc = new SAXBuilder().build(new StringReader(this.data));
            if (doc != null && doc.hasRootElement()) {
                Element root = doc.getRootElement();
                Element rec = null;
                if ("record".equalsIgnoreCase(root.getName())) {
                    rec = root;
                } else {
                    rec = doc.getRootElement().getChild("record", MARC);
                }
                List<Element> controlfields = rec.getChildren("controlfield", MARC);
                List<Element> datafields = rec.getChildren("datafield", MARC);
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
                    rec.addContent(controlfield001);
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
                String dsType = "MultipleManifestation";
                String volumeStructType = structType.get(docstruct.getDocStruct());
                log.debug("Docstruct type: " + dsType);

                DocStruct dsRoot = dd.createDocStruct(this.prefs.getDocStrctTypeByName(dsType));
                dd.setLogicalDocStruct(dsRoot);

                DocStruct dsBoundBook = dd.createDocStruct(this.prefs.getDocStrctTypeByName("BoundBook"));
                dd.setPhysicalDocStruct(dsBoundBook);
                DocStruct dsVolume = dd.createDocStruct(this.prefs.getDocStrctTypeByName(volumeStructType));
                dsRoot.addChild(dsVolume);

                // Collect MODS metadata
                WellcomeUtils.parseModsSectionForMultivolumes(MODS_MAPPING_FILE, this.prefs, dsRoot, dsVolume, dsBoundBook, eleMods);
                this.currentIdentifier = WellcomeUtils.getIdentifier(this.prefs, dsRoot);
                this.currentWellcomeIdentifier = WellcomeUtils.getWellcomeIdentifier(this.prefs, dsRoot);

                String strId = String.valueOf(docstruct.getOrder());
                if (docstruct.getOrder() < 10) {
                    strId = "000" + strId;
                } else if (docstruct.getOrder() < 100) {
                    strId = "00" + strId;
                } else if (docstruct.getOrder() < 1000) {
                    strId = "0" + strId;
                }
                Metadata mdId = new Metadata(this.prefs.getMetadataTypeByName("CatalogIDDigital"));
                mdId.setValue(this.currentIdentifier + "_" + strId);
                dsVolume.addMetadata(mdId);
                Metadata currentNo = new Metadata(this.prefs.getMetadataTypeByName("CurrentNo"));
                currentNo.setValue(String.valueOf(docstruct.getOrder()));
                dsVolume.addMetadata(currentNo);
                Metadata currentNoSorting = new Metadata(this.prefs.getMetadataTypeByName("CurrentNoSorting"));
                currentNoSorting.setValue(String.valueOf(docstruct.getOrder()));
                dsVolume.addMetadata(currentNoSorting);

                Metadata manifestationType = new Metadata(this.prefs.getMetadataTypeByName("_ManifestationType"));
                for (ImportProperty ip : this.properties) {
                    if ("Multiple manifestation type".equals(ip.getName())) {
                        manifestationType.setValue(ip.getValue());
                        break;
                    }
                }
                dsRoot.addMetadata(manifestationType);
                String copyvalue = docstruct.getCopyProperty().getValue();
                if (!"N/A".equals(copyvalue)) {
                    Metadata copyType = new Metadata(this.prefs.getMetadataTypeByName("_copy"));
                    copyType.setValue(copyvalue);
                    dsVolume.addMetadata(copyType);
                }

                String volumevalue = docstruct.getVolumeProperty().getValue();
                if (!"N/A".equals(volumevalue)) {
                    Metadata volumeType = new Metadata(this.prefs.getMetadataTypeByName("_volume"));
                    volumeType.setValue(volumevalue);
                    dsVolume.addMetadata(volumeType);

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
        for (ImportProperty ip : this.properties) {
            if (!"Multiple manifestation type".equals(ip.getName())) {
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
        }

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
    public void setForm(MassImportForm form) {
        this.form = form;
    }

    @Override
    public List<ImportObject> generateFiles(List<Record> records) {
        List<ImportObject> answer = new ArrayList<>();

        if (!records.isEmpty()) {
            Record r = records.get(0);
            this.data = r.getData();
            this.currentCollectionList = r.getCollections();
            for (DocstructElement dse : currentDocStructs) {

                form.addProcessToProgressBar();

                docstruct = (WellcomeDocstructElement) dse;
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
        }
        return answer;
    }

    @Override
    public List<Record> generateRecordsFromFile() {
        List<Record> ret = new ArrayList<>();
        try {
            Document doc = new SAXBuilder().build(this.importFile);
            if (doc != null && doc.getRootElement() != null) {
                Record rec = new Record();
                rec.setData(new XMLOutputter().outputString(doc));
                ret.add(rec);

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
                String d = convertTextToMarcXml(s);
                if (d != null) {
                    Record rec = new Record();
                    rec.setData(d);
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

        String strId = String.valueOf(docstruct.getOrder());
        if (docstruct.getOrder() < 10) {
            strId = "000" + strId;
        } else if (docstruct.getOrder() < 100) {
            strId = "00" + strId;
        } else if (docstruct.getOrder() < 1000) {
            strId = "0" + strId;
        }
        if (this.currentWellcomeIdentifier != null) {
            String temp = this.currentWellcomeIdentifier.replaceAll("\\W", "_");
            if (StringUtils.isNotBlank(temp)) {
                return temp.toLowerCase() + "_" + this.currentIdentifier + "_" + strId;
            }
        }
        return this.currentIdentifier + "_" + strId;
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
        return this.properties;
    }

    @Override
    public List<String> getAllFilenames() {
        return null; //NOSONAR
    }

    @Override
    public List<Record> generateRecordsFromFilenames(List<String> filenames) {
        return null; //NOSONAR
    }

    @Override
    public void deleteFiles(List<String> selectedFilenames) {
        // nothing
    }

    @Override
    public String addDocstruct() {

        int order = 1;
        if (!currentDocStructs.isEmpty()) {
            order = currentDocStructs.get(currentDocStructs.size() - 1).getOrder() + 1;
        }
        WellcomeDocstructElement dse = new WellcomeDocstructElement("Monograph", order);
        currentDocStructs.add(dse);

        return "";
    }

    @Override
    public String deleteDocstruct() {
        if (currentDocStructs.contains(docstruct)) {
            currentDocStructs.remove(docstruct);
        }
        return "";
    }

    @Override
    public List<? extends DocstructElement> getCurrentDocStructs() {
        if (currentDocStructs.isEmpty()) {
            WellcomeDocstructElement dse = new WellcomeDocstructElement("Monograph", 1);
            currentDocStructs.add(dse);
        }
        return currentDocStructs;
    }

    @Override
    public List<String> getPossibleDocstructs() {
        List<String> dsl = new ArrayList<>();
        dsl.add("Archive");
        dsl.add("Artwork");
        dsl.add("Audio");
        dsl.add("Born digital");
        dsl.add("Bound manuscript");
        dsl.add("Monograph");
        dsl.add("Multiple copy");
        dsl.add("Multiple volume");
        dsl.add("Multiple volume multiple copy");
        dsl.add("Poster image");
        dsl.add("Still images");
        dsl.add("Transcript");
        dsl.add("Video");
        dsl.add("Report");
        dsl.add("Annex");
        return dsl;
    }

    @Override
    public WellcomeDocstructElement getDocstruct() {
        return docstruct;
    }

    @Override
    public void setDocstruct(DocstructElement dse) {
        docstruct = (WellcomeDocstructElement) dse;
    }

    public void setDocstruct(WellcomeDocstructElement dse) {
        docstruct = dse;
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
        MultipleManifestationMillenniumImport other = (MultipleManifestationMillenniumImport) obj;
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
