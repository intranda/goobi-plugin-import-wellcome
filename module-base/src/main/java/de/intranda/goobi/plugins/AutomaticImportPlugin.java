package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
public class AutomaticImportPlugin implements IImportPlugin, IPlugin {

    private static final String COMMAND_NAME = "AutomaticImportPlugin";
    private static final String COLLECTION_NAME = "19th century";

    private static final Logger logger = Logger.getLogger(AutomaticImportPlugin.class);

    private Map<String, String> map = new HashMap<>();

    private static final String XSLT = ConfigurationHelper.getInstance().getXsltFolder() + "MARC21slim2MODS3.xsl";
    private static final String MODS_MAPPING_FILE = ConfigurationHelper.getInstance().getXsltFolder() + "mods_map.xml";
    private static final Namespace MARC = Namespace.getNamespace("marc", "http://www.loc.gov/MARC21/slim");
    private String currentIdentifier;
    private String currentWellcomeIdentifier;

    public AutomaticImportPlugin() {
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

    private Prefs prefs;
    private String data;

    private String importfolder;

    @Override
    public PluginType getType() {
        return PluginType.Import;
    }

    @Override
    public String getTitle() {
        return COMMAND_NAME;
    }

    public String getDescription() {
        return COMMAND_NAME;
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
    public Fileformat convertData() throws ImportPluginException {
        Fileformat ff = null;
        Document doc;
        try {
            // System.out.println(this.data);

            doc = new SAXBuilder().build(new StringReader(this.data));
            if (doc != null && doc.hasRootElement()) {
                Element record = null;
                Element root = doc.getRootElement();
                if ("record".equals(root.getName())) {
                    record = root;
                } else {
                    doc.getRootElement().getChild("record", MARC);
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
                logger.debug(new XMLOutputter().outputString(docMods));

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
                logger.debug("Docstruct type: " + dsType);

                DocStruct dsRoot = dd.createDocStruct(this.prefs.getDocStrctTypeByName(dsType));
                dd.setLogicalDocStruct(dsRoot);

                DocStruct dsBoundBook = dd.createDocStruct(this.prefs.getDocStrctTypeByName("BoundBook"));
                dd.setPhysicalDocStruct(dsBoundBook);

                // Collect MODS metadata
                WellcomeUtils.parseModsSection(MODS_MAPPING_FILE, this.prefs, dsRoot, dsBoundBook, eleMods);
                this.currentIdentifier = WellcomeUtils.getIdentifier(this.prefs, dsRoot);

                this.currentWellcomeIdentifier = WellcomeUtils.getWellcomeIdentifier(this.prefs, dsRoot);

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
            logger.error(this.currentIdentifier + ": " + e.getMessage(), e);
            throw new ImportPluginException(e);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new ImportPluginException(e);
        }
        return ff;
    }

    @Override
    public String getImportFolder() {
        return importfolder;
    }

    @Override
    public String getProcessTitle() {
        if (this.currentWellcomeIdentifier != null) {
            String temp = this.currentWellcomeIdentifier.replaceAll("\\W", "_");
            if (StringUtils.isNotBlank(temp)) {
                return temp.toLowerCase() + "_" + this.currentIdentifier;
            }
        }
        return this.currentIdentifier;
    }

    @Override
    public List<ImportObject> generateFiles(List<Record> recordList) {
        List<ImportObject> answer = new ArrayList<>();

        for (Record record : recordList) {
            this.data = record.getData();
            ImportObject io = new ImportObject();
            Fileformat ff = null;
            try {
                ff = convertData();
            } catch (ImportPluginException e1) {
                io.setErrorMessage(e1.getMessage());
            }

            generateProperties(io);
            io.setProcessTitle(getProcessTitle());
            io.setImportFileName(record.getId());
            if (ff != null) {
                record.setId(this.currentIdentifier);
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
                    io.setErrorMessage(e.getMessage());
                    io.setImportReturnValue(ImportReturnValue.InvalidData);
                    // ret.put(getProcessTitle(), ImportReturnValue.InvalidData);
                } catch (WriteException e) {
                    logger.error(e.getMessage(), e);
                    io.setImportReturnValue(ImportReturnValue.WriteError);
                    io.setErrorMessage(e.getMessage());
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

    private void generateProperties(ImportObject io) {
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

        {
            Processproperty pe = new Processproperty();
            pe.setTitel("CollectionName1");
            pe.setType(PropertyType.STRING);
            pe.setWert("Digitised");
            io.getProcessProperties().add(pe);
        }
        {
            Processproperty pe = new Processproperty();
            pe.setTitel("CollectionName2");
            pe.setType(PropertyType.STRING);
            pe.setWert(COLLECTION_NAME);
            io.getProcessProperties().add(pe);
        }
        {
            Processproperty pe = new Processproperty();
            pe.setTitel("securityTag");
            pe.setType(PropertyType.STRING);
            pe.setWert("open");
            io.getProcessProperties().add(pe);

        }
        {
            Processproperty pe = new Processproperty();
            pe.setTitel("schemaName");
            pe.setType(PropertyType.STRING);
            pe.setWert("Millennium");
            io.getProcessProperties().add(pe);
        }
    }

    @Override
    public void setImportFolder(String folder) {
        this.importfolder = folder;
    }

    @Override
    public List<Record> splitRecords(String records) {
        return new ArrayList<>();
    }

    @Override
    public List<Record> generateRecordsFromFile() {
        return new ArrayList<>();
    }

    @Override
    public List<Record> generateRecordsFromFilenames(List<String> filenames) {
        return new ArrayList<>();
    }

    @Override
    public void setFile(File importFile) {
    }

    @Override
    public List<String> splitIds(String ids) {
        return new ArrayList<>();
    }

    @Override
    public List<ImportType> getImportTypes() {
        return new ArrayList<>();
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
        String folder = ConfigPlugins.getPluginConfig(this).getString("importfolder", "/opt/digiverso/other/wellcome/import/");
        for (String filename : selectedFilenames) {
            File f = new File(folder, filename);
            FileUtils.deleteQuietly(f);
        }
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
    public void setForm(MassImportForm form) {
        // TODO Auto-generated method stub

    }

}
