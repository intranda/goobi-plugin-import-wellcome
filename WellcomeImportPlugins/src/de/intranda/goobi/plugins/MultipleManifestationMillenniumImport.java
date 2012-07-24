package de.intranda.goobi.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.xeoh.plugins.base.annotations.PluginImplementation;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.goobi.production.Import.DocstructElement;
import org.goobi.production.Import.ImportObject;
import org.goobi.production.Import.Record;
import org.goobi.production.enums.ImportReturnValue;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IImportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.properties.ImportProperty;
import org.goobi.production.properties.Type;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;
import org.jdom.transform.XSLTransformer;

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
import de.sub.goobi.Beans.Prozesseigenschaft;
import de.sub.goobi.config.ConfigMain;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.enums.PropertyType;
import de.sub.goobi.helper.exceptions.ImportPluginException;

@PluginImplementation
public class MultipleManifestationMillenniumImport implements IImportPlugin, IPlugin {

	/** Logger for this class. */
	private static final Logger logger = Logger.getLogger(MultipleManifestationMillenniumImport.class);

	private static final String NAME = "Multiple Manifestation Millennium Import";
	// private static final String VERSION = "0.1";
	private static final String XSLT = ConfigMain.getParameter("xsltFolder") + "MARC21slim2MODS3.xsl";
	private static final String MODS_MAPPING_FILE = ConfigMain.getParameter("xsltFolder") + "mods_map_multi.xml";
	private static final Namespace MARC = Namespace.getNamespace("marc", "http://www.loc.gov/MARC21/slim");

	private Prefs prefs;
	private String data = "";
	private File importFile = null;
	private String importFolder = "C:/Goobi/";
	private String currentIdentifier;
//	private String currentTitle;
	private String currentWellcomeIdentifier;
//	private String currentWellcomeLeader6;
//	private String currentAuthor;
	private List<String> currentCollectionList;
	private List<ImportProperty> properties = new ArrayList<ImportProperty>();
	private List<WellcomeDocstructElement> currentDocStructs = new ArrayList<WellcomeDocstructElement>();
	private WellcomeDocstructElement docstruct;

	public MultipleManifestationMillenniumImport() {

		{
			ImportProperty ip = new ImportProperty();
			ip.setName("CollectionName1");
			ip.setType(Type.LIST);
			List<String> values = new ArrayList<String>();
			values.add("Digitised");
			values.add("Born digital");
			ip.setPossibleValues(values);
			ip.setRequired(true);
			this.properties.add(ip);
		}
		{
			ImportProperty ip = new ImportProperty();
			ip.setName("CollectionName2");
			ip.setType(Type.TEXT);
			ip.setRequired(true);
			this.properties.add(ip);
		}
		{
			ImportProperty ip = new ImportProperty();
			ip.setName("securityTag");
			ip.setType(Type.LIST);
			List<String> values = new ArrayList<String>();
			values.add("open");
			values.add("closed");
			ip.setPossibleValues(values);
			ip.setRequired(true);
			this.properties.add(ip);
		}
		{
			ImportProperty ip = new ImportProperty();
			ip.setName("schemaName");
			ip.setType(Type.LIST);
			List<String> values = new ArrayList<String>();
			values.add("Millennium");
			ip.setPossibleValues(values);
			ip.setRequired(true);
			this.properties.add(ip);
		}

		{
			ImportProperty ip = new ImportProperty();
			ip.setName("Multiple manifestation type");
			ip.setType(Type.LIST);
			List<String> values = new ArrayList<String>();
			values.add("General");
			values.add("Video & transcript & poster image");
			values.add("Video & transcript");
			values.add("Audio & transcript");
			values.add("Audio & transcript & poster image");
			ip.setPossibleValues(values);
			ip.setRequired(true);
			this.properties.add(ip);
		}

	}

	@Override
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

	@SuppressWarnings("unchecked")
	@Override
	public Fileformat convertData() throws ImportPluginException {
		Fileformat ff = null;
		Document doc;
		try {
			// System.out.println(this.data);

			doc = new SAXBuilder().build(new StringReader(this.data));
			if (doc != null && doc.hasRootElement()) {

				Element record = doc.getRootElement().getChild("record", MARC);
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

				logger.debug("Docstruct type: " + dsType);

				DocStruct dsRoot = dd.createDocStruct(this.prefs.getDocStrctTypeByName(dsType));
				dd.setLogicalDocStruct(dsRoot);

				DocStruct dsBoundBook = dd.createDocStruct(this.prefs.getDocStrctTypeByName("BoundBook"));
				dd.setPhysicalDocStruct(dsBoundBook);
				DocStruct dsVolume = dd.createDocStruct(this.prefs.getDocStrctTypeByName(docstruct.getDocStruct()));
				dsRoot.addChild(dsVolume);

				// Collect MODS metadata
				WellcomeUtils.parseModsSectionForMultivolumes(MODS_MAPPING_FILE, this.prefs, dsRoot, dsVolume, dsBoundBook, eleMods);
				this.currentIdentifier = WellcomeUtils.getIdentifier(this.prefs, dsRoot);
				this.currentWellcomeIdentifier = WellcomeUtils.getWellcomeIdentifier(this.prefs, dsRoot);
//				this.currentTitle = WellcomeUtils.getTitle(this.prefs, dsRoot);
//				this.currentAuthor = WellcomeUtils.getAuthor(this.prefs, dsRoot);
//				this.currentWellcomeLeader6 = WellcomeUtils.getLeader6(this.prefs, dsRoot);

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
				Metadata CurrentNoSorting = new Metadata(this.prefs.getMetadataTypeByName("CurrentNoSorting"));
				CurrentNoSorting.setValue(String.valueOf(docstruct.getOrder()));
				dsVolume.addMetadata(CurrentNoSorting);

				Metadata manifestationType = new Metadata(this.prefs.getMetadataTypeByName("_ManifestationType"));
				for (ImportProperty ip : this.properties) {
					if (ip.getName().equals("Multiple manifestation type")) {
						manifestationType.setValue(ip.getValue());
						break;
					}
				}
				dsRoot.addMetadata(manifestationType);
				String copyvalue = docstruct.getCopyProperty().getValue();
				if (!copyvalue.equals("N/A")) {
					Metadata copyType = new Metadata(this.prefs.getMetadataTypeByName("_copy"));
					copyType.setValue(copyvalue);
					dsVolume.addMetadata(copyType);
				}

				String volumevalue = docstruct.getVolumeProperty().getValue();
				if (!volumevalue.equals("N/A")) {
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
					logger.error("MetadataTypeNotAllowedException while reading images", e1);
				} catch (DocStructHasNoTypeException e1) {
					logger.error("DocStructHasNoTypeException while reading images", e1);
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
				File folderForImport = new File(getImportFolder() + File.separator + getProcessTitle() + File.separator + "import" + File.separator);
				WellcomeUtils.writeXmlToFile(folderForImport.getAbsolutePath(), getProcessTitle() + "_mrc.xml", doc);
			}
		} catch (JDOMException e) {
			logger.error(this.currentIdentifier + ": " + e.getMessage(), e);
			throw new ImportPluginException(e);
		} catch (IOException e) {
			logger.error(this.currentIdentifier + ": " + e.getMessage(), e);
			throw new ImportPluginException(e);
		} catch (PreferencesException e) {
			logger.error(this.currentIdentifier + ": " + e.getMessage(), e);
			throw new ImportPluginException(e);
		} catch (TypeNotAllowedForParentException e) {
			logger.error(this.currentIdentifier + ": " + e.getMessage(), e);
			throw new ImportPluginException(e);
		} catch (MetadataTypeNotAllowedException e) {
			logger.error(this.currentIdentifier + ": " + e.getMessage(), e);
			throw new ImportPluginException(e);
		} catch (TypeNotAllowedAsChildException e) {
			logger.error(this.currentIdentifier + ": " + e.getMessage(), e);
			throw new ImportPluginException(e);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw new ImportPluginException(e);
		}

		return ff;
	}

	private void generateProperties(ImportObject io) {
		for (ImportProperty ip : this.properties) {
			if (!ip.getName().equals("Multiple manifestation type")) {
				Prozesseigenschaft pe = new Prozesseigenschaft();
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
			Prozesseigenschaft pe = new Prozesseigenschaft();
			pe.setTitel("importPlugin");
			pe.setWert(getTitle());
			pe.setType(PropertyType.String);
			io.getProcessProperties().add(pe);
		}
		{
			Prozesseigenschaft pe = new Prozesseigenschaft();
			pe.setTitel("b-number");
			pe.setWert(this.currentIdentifier);
			pe.setType(PropertyType.String);
			io.getProcessProperties().add(pe);
		}
	}

	@Override
	public List<ImportObject> generateFiles(List<Record> records) {
		List<ImportObject> answer = new ArrayList<ImportObject>();

		if (records.size() > 0) {
			Record r = records.get(0);
			this.data = r.getData();
			this.currentCollectionList = r.getCollections();
			for (DocstructElement dse : currentDocStructs) {
				docstruct = (WellcomeDocstructElement) dse;
				ImportObject io = new ImportObject();
				Fileformat ff = null;
				try {
					ff = convertData();
				} catch (ImportPluginException e1) {
					io.setErrorMessage(e1.getMessage());
				}

				generateProperties(io);
				io.setProcessTitle(getProcessTitle());
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
		List<Record> ret = new ArrayList<Record>();
		// InputStream input = null;
		try {
			Document doc = new SAXBuilder().build(this.importFile);
			if (doc != null && doc.getRootElement() != null) {
				Record record = new Record();
				record.setData(new XMLOutputter().outputString(doc));
				// record.setData(new
				// XMLOutputter().outputString(doc).replace("marc:", ""));
				ret.add(record);

			} else {
				logger.error("Could not parse '" + this.importFile + "'.");
			}
		} catch (JDOMException e) {
			logger.error(e.getMessage(), e);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
		return ret;
	}

	@Override
	public List<Record> splitRecords(String records) {
		List<Record> ret = new ArrayList<Record>();

		// Split strings
		List<String> recordStrings = new ArrayList<String>();
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
			logger.error(e.getMessage(), e);
		}

		// Convert strings to MARCXML records and add them to Record objects
		for (String s : recordStrings) {
			String data;
			try {
				data = convertTextToMarcXml(s);
				if (data != null) {
					Record rec = new Record();
					rec.setData(data);
					ret.add(rec);
				}
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		}

		return ret;
	}

	@Override
	public List<String> splitIds(String ids) {
		return new ArrayList<String>();
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
		List<ImportType> answer = new ArrayList<ImportType>();
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
						eleDataField.setAttribute("ind1", !ind1.equals("\\") ? ind1 : "");
						eleDataField.setAttribute("ind2", !ind2.equals("\\") ? ind2 : "");
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
		if (currentDocStructs.size() == 0) {
			WellcomeDocstructElement dse = new WellcomeDocstructElement("Monograph", 1);
			currentDocStructs.add(dse);
		}
		return currentDocStructs;
	}

	@Override
	public List<String> getPossibleDocstructs() {
		List<String> dsl = new ArrayList<String>();
		// TODO Auswahl in Ruleset-DocStructs mappen
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

	
	public static void main(String[] args) throws PreferencesException, WriteException, ImportPluginException {

		File[] calms = new File("/opt/digiverso/goobi/import/millennium/").listFiles();
		MultipleManifestationMillenniumImport wci = new MultipleManifestationMillenniumImport();
		Prefs prefs = new Prefs();
		wci.setImportFolder("/opt/digiverso/goobi/hotfolder/");
		wci.setPrefs(prefs);
		wci.prefs.loadPrefs("/opt/digiverso/goobi/rulesets/wellcome.xml");
		List<Record> recordList = new ArrayList<Record>();
		File filename = new File("/home/robert/b16756654.xml");
		// for (File filename : calms) {
		// File filename = calms[0];
		wci.setFile(filename);
		recordList.addAll(wci.generateRecordsFromFile());
		// }
		// Record r = recordList.get(0);
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
}
