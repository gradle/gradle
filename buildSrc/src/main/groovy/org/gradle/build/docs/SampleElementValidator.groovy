package org.gradle.build.docs

import org.w3c.dom.Element

/**
 * Validates sample element - looks for missing attributes.
 * 
 * @author Tomek Kaczanowski
 */
public class SampleElementValidator {

	void validate(Element element) {
        String id = element.'@id'
        if (!id) {
            throw new RuntimeException("No id attribute specified for sample.")
        }
        String srcDir = element.'@dir'
        if (!srcDir) {
            throw new RuntimeException("No dir attribute specified for sample '$id'.")
        }
        String title = element.'@title' 
        if (!title) {
        	throw new RuntimeException("No title attribute specified for sample '$id'.")
        }
        element.children().each {Element child ->
        	if (child.name() == 'sourcefile') {
        		if (!child.'@file') {
        			throw new RuntimeException("No file attribute specified for source file in sample '$id'.")
        		}

        	} else if (child.name() == 'output' || child.name() == 'test') {
          	  	if (child.'@args' == null) {
          		  throw new RuntimeException("No args attribute specified for output for sample '$id'.")
          	  	}
        	} else if (child.name() == 'layout') {
        		// nothing, makes no sense to do the validation here, cause I'd have to copy all the logic also
        	}
        	else {
                throw new RuntimeException("Unrecognised sample type ${child.name()} found.")
            }
        	
        } 
	}
                        
}
