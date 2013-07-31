package de.intranda.goobi.plugins.utils;

import java.util.ArrayList;
import java.util.List;

import org.goobi.production.importer.DocstructElement;
import org.goobi.production.properties.ImportProperty;
import org.goobi.production.properties.Type;

public class WellcomeDocstructElement extends DocstructElement {

//	List<ImportProperty> properties = new ArrayList<ImportProperty>();

	// private List<SelectItem> copyList = new ArrayList<SelectItem>();
	// private List<SelectItem> volumeList = new ArrayList<SelectItem>();

	private ImportProperty copyProperty = new ImportProperty();
	private ImportProperty volumeProperty = new ImportProperty();

	
	
	public WellcomeDocstructElement(String docStruct, int order) {
		super(docStruct, order);
		// this.docStruct = docStruct;
		// this.order = order;
		populatePropertyList();
	}

	private void populatePropertyList() {
		volumeProperty.setName("Volume");
		volumeProperty.setType(Type.LIST);
		List<String> values = new ArrayList<String>();
		values.add("N/A");
		for (int i = 1; i <= 30; i++) {
			values.add(String.valueOf(i));
		}
		volumeProperty.setPossibleValues(values);
		volumeProperty.setValue("N/A");
		
		
		copyProperty.setName("Copy");
		copyProperty.setType(Type.LIST);
		List<String> possibleValues = new ArrayList<String>();
		possibleValues.add("N/A");
		for (int i = 1; i <= 30; i++) {
			possibleValues.add(String.valueOf(i));
		}
		copyProperty.setPossibleValues(possibleValues);
		copyProperty.setValue("N/A");
	}

	public ImportProperty getCopyProperty() {
		return copyProperty;
	}
	
	public ImportProperty getVolumeProperty() {
		return volumeProperty;
	}
}
