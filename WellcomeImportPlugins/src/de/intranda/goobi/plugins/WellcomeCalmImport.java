package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.xeoh.plugins.base.annotations.PluginImplementation;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.goobi.production.Import.Record;
import org.goobi.production.enums.ImportReturnValue;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IImportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;

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
import de.intranda.goobi.plugins.utils.WellcomeUtils;
import de.sub.goobi.Import.ImportOpac;
import de.sub.goobi.config.ConfigMain;

@PluginImplementation
public class WellcomeCalmImport implements IImportPlugin, IPlugin {

	/** Logger for this class. */
	private static final Logger logger = Logger.getLogger(WellcomeCalmImport.class);

	private static final String NAME = "Calm Import";
//	private static final String VERSION = "1.0";
	private static final String MAPPING_FILE = ConfigMain.getParameter("KonfigurationVerzeichnis") + "WellcomeCalm_map.properties";
	private String data = "";
	private String importFolder = "";
	private File importFile;
	private Prefs prefs;

	private String currentTitle;

	private String currentAuthor;

	private String currentIdentifier;

	private List<String> currentCollectionList;

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
				Element dScribeRecord = doc.getRootElement();

				// generating DocStruct

				String dsType = dScribeRecord.getChild("RecordType").getText(); 
				
				// TODO sicherstellen das alle dsType im Regelsatz existieren
				if (!dsType.equals("Monograph")) {
					System.out.println(dsType);
					dsType = "Monograph";
				}
				DocStruct dsRoot = dd.createDocStruct(this.prefs.getDocStrctTypeByName(dsType));
				dd.setLogicalDocStruct(dsRoot);

				DocStructType dst = this.prefs.getDocStrctTypeByName("BoundBook");
				DocStruct dsBoundBook = dd.createDocStruct(dst);
				dd.setPhysicalDocStruct(dsBoundBook);

				Metadata path = new Metadata(this.prefs.getMetadataTypeByName("pathimagefiles"));
				path.setValue("./");
				dsBoundBook.addMetadata(path);
				
				// reading import file
				List<String> elementList = WellcomeUtils.getKeys(MAPPING_FILE);
				for (String key : elementList) {
					Element toTest = null;
					// finding the right Element
					if (key.contains(".")) {
						String[] subElements = key.split(".");
						Element sub = dScribeRecord;
						for (String subString : subElements) {
							if (sub.getChildren(subString).size() > 0) {
								sub = (Element) sub.getChildren(subString).get(0);
							}
						}
						if (!sub.getName().equals(subElements[subElements.length])) {
							toTest = null;
						} else {
							toTest = sub;
						}
					} else {
						if (dScribeRecord.getChild(key) != null) {
							toTest = dScribeRecord.getChild(key);
						}
					}

					if (toTest != null) {
						if (toTest.getChild("_")!= null) {
							toTest = toTest.getChild("_");
						}
						String metadataName = WellcomeUtils.getValue(MAPPING_FILE, key);
						MetadataType mdt = this.prefs.getMetadataTypeByName(metadataName);
						Metadata md = new Metadata(mdt);
						md.setValue(toTest.getText());
						dsRoot.addMetadata(md);
						if (metadataName.equalsIgnoreCase("CatalogIDDigital")) {
							this.currentIdentifier = md.getValue();
						}
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
				this.currentIdentifier = WellcomeUtils.getIdentifier(this.prefs, dsRoot);
				this.currentTitle = WellcomeUtils.getTitle(this.prefs, dsRoot);
				this.currentAuthor = WellcomeUtils.getAuthor(this.prefs, dsRoot);
			}
			
			WellcomeUtils.writeXmlToFile(getImportFolder() + File.separator + getProcessTitle().replace(".xml", "_src"),
					getProcessTitle().replace(".xml", "_CALM.xml"), doc);
		} catch (JDOMException e) {
			logger.error(e.getMessage(), e);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		} catch (PreferencesException e) {
			logger.error(e.getMessage(), e);
		} catch (MetadataTypeNotAllowedException e) {
			logger.error(e.getMessage(), e);
		} catch (TypeNotAllowedForParentException e) {
			logger.error(e.getMessage(), e);
		}

		return ff;
	}

	@Override
	public String getImportFolder() {
		return this.importFolder;
	}

	@Override
	public String getProcessTitle() {
		if (StringUtils.isNotBlank(this.currentTitle)) {
			return new ImportOpac().createAtstsl(this.currentTitle, this.currentAuthor).toLowerCase() + "_" + this.currentIdentifier + ".xml";
		}
		return this.currentIdentifier + ".xml";
	}

	@Override
	public List<Record> splitRecords(String records) {
		List<Record> ret = new ArrayList<Record>();
		Record r = new Record();
		r.setData(records);
		ret.add(r);
		return ret;
	}

	@Override
	public HashMap<String, ImportReturnValue> generateFiles(List<Record> records) {
		HashMap<String, ImportReturnValue> ret = new HashMap<String, ImportReturnValue>();

		for (Record r : records) {
			this.data = r.getData();
			this.currentCollectionList = r.getCollections();
			Fileformat ff = convertData();
			if (ff != null) {
				r.setId(this.currentIdentifier);
				try {
					MetsMods mm = new MetsMods(this.prefs);
					mm.setDigitalDocument(ff.getDigitalDocument());
					String fileName = getImportFolder() + getProcessTitle();
					logger.debug("Writing '" + fileName + "' into hotfolder...");
					mm.write(fileName);
					ret.put(getProcessTitle(), ImportReturnValue.ExportFinished);
				} catch (PreferencesException e) {
					logger.error(e.getMessage(), e);
					ret.put(getProcessTitle(), ImportReturnValue.InvalidData);
				} catch (WriteException e) {
					logger.error(e.getMessage(), e);
					ret.put(getProcessTitle(), ImportReturnValue.WriteError);
				}
			} else {
				ret.put(getProcessTitle(), ImportReturnValue.InvalidData);
			}
		}

		return ret;

	}

	@Override
	public void setImportFolder(String folder) {
		this.importFolder = folder;

	}

	@Override
	public List<Record> generateRecordsFromFile() {
		List<Record> ret = new ArrayList<Record>();
		try {
			Document doc = new SAXBuilder().build(this.importFile);
			if (doc != null && doc.getRootElement() != null) {
				Record record = new Record();
				record.setData(new XMLOutputter().outputString(doc));
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
	public void setFile(File importFile) {
		this.importFile = importFile;
	}

	@Override
	public List<String> splitIds(String ids) {
		return new ArrayList<String>();
	}

	@Override
	public List<ImportType> getImportTypes() {
		List<ImportType> answer = new ArrayList<ImportType>();
		answer.add(ImportType.Record);
		answer.add(ImportType.FILE);
		return answer;
	}

	public static void main(String[] args) throws PreferencesException, WriteException {
//		WellcomeCalmImport wci = new WellcomeCalmImport();
//		wci.setFile(new File("/home/robert/workspace/WellcomeImportPlugins/resources/fa68b9ed-1d84-4ebb-89e4-cdbaba1570e7.xml"));
//		List<Record> bla = wci.generateRecordsFromFile();
//		
//
//		
//		Prefs prefs = new Prefs();
//		wci.setImportFolder("/opt/digiverso/goobi/hotfolder/");
//		wci.setPrefs(prefs);
//		wci.prefs.loadPrefs("/opt/digiverso/goobi/rulesets/gdz.xml");
//		List<Record> recordList = wci.generateRecordsFromFile();
//		// Record r = recordList.get(0);
//		for (Record r : recordList) {
//			wci.data = r.getData();
//			Fileformat ff = wci.convertData();
//			if (ff != null) {
//				MetsMods mm = new MetsMods(prefs);
//				mm.setDigitalDocument(ff.getDigitalDocument());
//				String fileName = wci.getImportFolder() + wci.getProcessTitle() + ".xml";
//				logger.debug("Writing '" + fileName + "' into hotfolder...");
//				mm.write(fileName);
//			}
//		}
		
		File[] calms = new File("/home/robert/Downloads/wellcome/CALM/").listFiles();
		WellcomeCalmImport wci = new WellcomeCalmImport();
		Prefs prefs = new Prefs();
		wci.setImportFolder("/opt/digiverso/goobi/hotfolder/");
		wci.setPrefs(prefs);
		wci.prefs.loadPrefs("/opt/digiverso/goobi/rulesets/gdz.xml");
		List<Record> recordList = new ArrayList<Record>();
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
			logger.debug("Writing '" + fileName + "' into hotfolder...");
			mm.write(fileName);
		}
	}
		
	}
}
