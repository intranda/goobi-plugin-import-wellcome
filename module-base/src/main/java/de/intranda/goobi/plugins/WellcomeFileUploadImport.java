package de.intranda.goobi.plugins;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Processproperty;
import org.goobi.production.enums.ImportReturnValue;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.importer.DocstructElement;
import org.goobi.production.importer.ImportObject;
import org.goobi.production.importer.Record;
import org.goobi.production.plugin.interfaces.IImportPluginVersion2;
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
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.forms.MassImportForm;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.enums.PropertyType;
import de.sub.goobi.helper.exceptions.ImportPluginException;
import lombok.Getter;
import lombok.Setter;
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
public class WellcomeFileUploadImport implements IImportPluginVersion2, IPlugin {

    private static final long serialVersionUID = -288083231750979912L;

    private static final String XSLT = ConfigurationHelper.getInstance().getXsltFolder() + "MARC21slim2MODS3.xsl";
    private static final String MODS_MAPPING_FILE = ConfigurationHelper.getInstance().getXsltFolder() + "mods_map.xml";
    private static final Namespace MARC = Namespace.getNamespace("marc", "http://www.loc.gov/MARC21/slim");

    @Getter
    @Setter
    private Prefs prefs;

    @Getter
    @Setter
    private transient Record data;
    @Getter
    @Setter
    private File file = null;
    @Getter
    @Setter
    private String importFolder;
    private Map<String, String> map = new HashMap<>();
    private String currentIdentifier;

    private String currentWellcomeIdentifier;

    private List<String> currentCollectionList;

    // add IA download identifier
    private String currentIADownloadIdentifier;

    @Setter
    private MassImportForm form;

    @Getter
    private PluginType type = PluginType.Import;

    @Getter
    private String title = "plugin_import_fileupload_wellcome";

    public WellcomeFileUploadImport() {

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

    @Override
    public Fileformat convertData() throws ImportPluginException {
        Fileformat ff = null;
        Document doc;
        try {

            doc = new SAXBuilder().build(new StringReader(data.getData()));
            if (doc != null && doc.hasRootElement()) {
                Element rec = null;
                Element root = doc.getRootElement();
                if ("record".equals(root.getName())) {
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
            this.data = r;
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
        List<Record> answer = new ArrayList<>();
        if (file.getName().endsWith(".xml")) {
            // marc file, import single record
            Record rec = readFile(file);
            if (rec != null) {
                answer.add(rec);
            }
        } else if (file.getName().endsWith(".zip")) {
            // zip file, extract it and handle xml files as marc file
            try {
                Path tempFolder = Files.createTempDirectory("metadata");
                extractZipFile(tempFolder);

                List<Path> allExtractedFiles = new ArrayList<>();

                try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(tempFolder)) {
                    for (Path path : directoryStream) {
                        allExtractedFiles.add(path);
                    }
                } catch (IOException ex) {
                }
                Collections.sort(allExtractedFiles);

                for (Path path : allExtractedFiles) {
                    if (path.getFileName().toString().endsWith(".xml")) {
                        Record rec = readFile(path.toFile());
                        if (rec != null) {
                            answer.add(rec);
                        }
                    }
                }

                // finally delete tempFolder
                StorageProvider.getInstance().deleteDir(tempFolder);

            } catch (IOException e) {
                log.error(e);
            }
        }
        return answer;
    }

    private void extractZipFile(Path tempFolder) throws IOException {
        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                Path extractedFile = Paths.get(tempFolder.toString(), zipEntry.getName());
                try (OutputStream os = Files.newOutputStream(extractedFile)) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        os.write(buffer, 0, len);
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }

    private Record readFile(File file) {
        try {
            Document doc = new SAXBuilder().build(file);
            if (doc != null && doc.getRootElement() != null) {
                Record rec = new Record();
                rec.setId(file.getName());
                rec.setData(new XMLOutputter().outputString(doc));
                return rec;

            } else {
                log.error("Could not parse '" + file.getAbsolutePath() + "'.");
            }
        } catch (JDOMException | IOException e) {
            log.error(e.getMessage(), e);
        }

        return null;
    }

    @Override
    public List<Record> splitRecords(String records) {
        return Collections.emptyList();
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
    public List<ImportType> getImportTypes() {
        List<ImportType> answer = new ArrayList<>();
        answer.add(ImportType.FILE);
        return answer;
    }

    @Override
    public List<ImportProperty> getProperties() {
        return null; //NOSONAR
    }

    @Override
    public List<String> getAllFilenames() {
        return Collections.emptyList();
    }

    @Override
    public List<Record> generateRecordsFromFilenames(List<String> filenames) {
        return Collections.emptyList();
    }

    @Override
    public void deleteFiles(List<String> selectedFilenames) {
        // nothing
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
    public List<DocstructElement> getCurrentDocStructs() {
        return null; //NOSONAR
    }

    @Override
    public List<String> getPossibleDocstructs() {
        return null; //NOSONAR
    }

    @Override
    public DocstructElement getDocstruct() {
        return null;
    }

    @Override
    public void setDocstruct(DocstructElement dse) {
        //nothing
    }

    @Override
    public boolean isRunnableAsGoobiScript() {
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((title == null) ? 0 : title.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
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
        WellcomeFileUploadImport other = (WellcomeFileUploadImport) obj;
        if (title == null) {
            if (other.title != null) {
                return false;
            }
        } else if (!title.equals(other.title)) {
            return false;
        }
        if (type != other.type) {
            return false;
        }
        return true;
    }

}
