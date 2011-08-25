package de.intranda.goobi.plugins.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.output.XMLOutputter;

import ugh.dl.DocStruct;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.MetadataTypeNotAllowedException;

public class WellcomeUtils {
	
	private static final Logger logger = Logger.getLogger(WellcomeUtils.class);
	private static PropertiesConfiguration config = null;

	private static PropertiesConfiguration loadConfiguration(String configFile) {
		PropertiesConfiguration.setDefaultListDelimiter('&');
		if (configFile == null) {
			configFile = "GoobiConfig.properties";
		}
		try {
			config = new PropertiesConfiguration(configFile);
		} catch (ConfigurationException e) {
			config = new PropertiesConfiguration();
		}
		// config.setDelimiterParsingDisabled(true);
		config.setListDelimiter('|');

		config.setReloadingStrategy(new FileChangedReloadingStrategy());
		return config;
	}



	public static List<String> getKeys(String configFile) {
		List<String> keyList = new ArrayList<String>();
		if (config == null) {
			loadConfiguration(configFile);
		}
		@SuppressWarnings("unchecked")
		Iterator<String> iter = config.getKeys();
		while (iter.hasNext()) {
			String key = iter.next();
			keyList.add(key);
		}
		return keyList;
	}
	
	
	public static String getValue(String configFile, String key) {
		if (config == null) {
			loadConfiguration(configFile);
		}
		return config.getString(key, key);
	}

	public static void writeXmlToFile(String folderName, String fileName, Document doc) {
		try {
			File folder = new File(folderName);
			if (!folder.exists()) {
				folder.mkdirs();
			}
			new XMLOutputter().output(doc, new FileOutputStream(folder.getAbsolutePath() + File.separator + fileName));
		} catch (FileNotFoundException e) {
			logger.error(e.getMessage());
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
	}
	
	/**
	 * Returns the document's identifier, or a timestamp if the record has none
	 * 
	 * @param prefs
	 * @param ds
	 * @return
	 * @throws MetadataTypeNotAllowedException
	 * @throws DocStructHasNoTypeException
	 */
	public static String getIdentifier(Prefs prefs, DocStruct ds) throws MetadataTypeNotAllowedException, DocStructHasNoTypeException {
		String ret = null;

		MetadataType mdTypeId = prefs.getMetadataTypeByName("CatalogIDDigital");
		if (ds.getAllMetadataByType(mdTypeId) != null && !ds.getAllMetadataByType(mdTypeId).isEmpty()) {
			Metadata mdId = ds.getAllMetadataByType(mdTypeId).get(0);
			ret = mdId.getValue();
		} else {
			Metadata mdId = new Metadata(mdTypeId);
			ds.addMetadata(mdId);
			mdId.setValue(String.valueOf(System.currentTimeMillis()));
			ret = mdId.getValue();
		}

		return ret;
	}
	
	
	/**
	 * Returns the document's title.
	 * 
	 * @param prefs
	 * @param ds
	 * @return
	 * @throws MetadataTypeNotAllowedException
	 * @throws DocStructHasNoTypeException
	 */
	public static String getTitle(Prefs prefs, DocStruct ds) throws MetadataTypeNotAllowedException, DocStructHasNoTypeException {
		String ret = null;

		MetadataType mdTypeTitle = prefs.getMetadataTypeByName("TitleDocMain");
		if (ds.getAllMetadataByType(mdTypeTitle) != null && !ds.getAllMetadataByType(mdTypeTitle).isEmpty()) {
			Metadata mdTitle = ds.getAllMetadataByType(mdTypeTitle).get(0);
			ret = mdTitle.getValue();
		}

		return ret;
	}

	/**
	 * Returns the document's author.
	 * 
	 * @param prefs
	 * @param ds
	 * @return
	 * @throws MetadataTypeNotAllowedException
	 * @throws DocStructHasNoTypeException
	 */
	public static String getAuthor(Prefs prefs, DocStruct ds) throws MetadataTypeNotAllowedException, DocStructHasNoTypeException {
		String ret = null;

		MetadataType mdTypePerson = prefs.getMetadataTypeByName("Author");
		if (ds.getAllPersonsByType(mdTypePerson) != null && !ds.getAllPersonsByType(mdTypePerson).isEmpty()) {
			Person personAuthor = ds.getAllPersonsByType(mdTypePerson).get(0);
			ret = personAuthor.getLastname();
			if (StringUtils.isNotEmpty(personAuthor.getFirstname())) {
				ret += ", " + personAuthor.getFirstname();
			}
		}

		return ret;
	}
	
	
	
	
	
	public static void main(String[] args) {
		String wellcomeImageMapping = "/opt/digiverso/goobi/config/WellcomeImages_map.properties";
		List<String> keyList = WellcomeUtils.getKeys(wellcomeImageMapping);
		for (String key : keyList) {
			System.out.println(key + ": " + WellcomeUtils.getValue(wellcomeImageMapping, key));
		}
		
		
	}
}
