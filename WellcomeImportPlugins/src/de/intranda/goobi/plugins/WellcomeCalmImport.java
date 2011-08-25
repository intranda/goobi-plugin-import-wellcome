package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
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
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;

import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import de.sub.goobi.Import.ImportOpac;
import de.sub.goobi.config.ConfigMain;

@PluginImplementation
public class WellcomeCalmImport implements IImportPlugin, IPlugin {

	/** Logger for this class. */
	private static final Logger logger = Logger.getLogger(WellcomeCalmImport.class);

	private static final String NAME = "Calm Import";
	private static final String VERSION = "0.0";
	private static final String MAPPING_FILE = ConfigMain.getParameter("KonfigurationVerzeichnis") + "WellcomeCalm_map.xml";

	private String data = "";
	private String importFolder = "";
	private File importFile;
	Prefs prefs;

	private String currentTitle;

	private String currentAuthor;

	private String currentIdentifier;

	@Override
	public String getId() {
		return getDescription();
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
		return NAME + " v" + VERSION;
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
		// TODO Auto-generated method stub
		return null;
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setImportFolder(String folder) {
		// TODO Auto-generated method stub

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
	
	public static void main(String[] args) {
		WellcomeCalmImport wci = new WellcomeCalmImport();
		wci.setFile(new File("/home/robert/workspace/SotonImportPlugins/src/fa68b9ed-1d84-4ebb-89e4-cdbaba1570e7.xml"));
		List<Record> bla = wci.generateRecordsFromFile();
		for (Record r : bla) {
			System.out.println(r.getData());
		}
		
	}

}
