package de.intranda.goobi.plugins.utils;

import java.util.ArrayList;
import java.util.List;

import org.goobi.production.importer.DocstructElement;
import org.goobi.production.properties.ImportProperty;
import org.goobi.production.properties.Type;

import jakarta.faces.model.SelectItem;

public class WellcomeDocstructElement extends DocstructElement {

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
		for (int i = 1; i <= 99; i++) {
			values.add(String.valueOf(i));
		}
		volumeProperty.setPossibleValues(values.stream()
				.map(v -> new SelectItem(v, v))
				.toList());
		volumeProperty.setValue("N/A");
		
		
		copyProperty.setName("Copy");
		copyProperty.setType(Type.LIST);
		List<String> possibleValues = new ArrayList<String>();
		possibleValues.add("N/A");
		for (int i = 1; i <= 99; i++) {
			possibleValues.add(String.valueOf(i));
		}
		copyProperty.setPossibleValues(possibleValues.stream()
				.map(v -> new SelectItem(v, v))
				.toList());
		copyProperty.setValue("N/A");
	}

	public ImportProperty getCopyProperty() {
		return copyProperty;
	}
	
	public ImportProperty getVolumeProperty() {
		return volumeProperty;
	}
}
