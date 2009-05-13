package org.gradle.build.docs

import org.w3c.dom.Element
import org.w3c.dom.Document

/**
 * This class is used by the UserGuideTransformTask to inject the example location into
 * the first figure or example child of a sample block.
 * <p>
 * I would have included this as an inner class of UserGuideTransformTask but groovy doesn't 
 * support them.
 */
class SampleElementLocationHandler {
    
    private Document doc
    private Element sampleElement
    private String srcDir
    private boolean includeLocation = false
    private boolean locationIncluded = false

    def SampleElementLocationHandler(Document doc, Element sampleElement, String srcDir) {
        this.doc = doc
        this.srcDir = srcDir
        this.includeLocation = Boolean.valueOf(sampleElement.'@includeLocation')
    }

    public void processSampleLocation(Element child) {
        
        if (includeLocation && !locationIncluded) {
            Element tipElement = doc.createElement('tip')
            tipElement.setAttribute('role', 'exampleLocation')
            Element textElement = doc.createElement('para')
            tipElement.appendChild(textElement)
            Element emphasisElement = doc.createElement('emphasis')
            textElement.appendChild(emphasisElement)
            emphasisElement.appendChild(doc.createTextNode('Note:'))
            textElement.appendChild(doc.createTextNode(' The code for this example can be found at '))
            Element filenameElement = doc.createElement('filename')
            textElement.appendChild(filenameElement)
            filenameElement.appendChild(doc.createTextNode("samples/$srcDir"))

            child.appendChild(tipElement)

            locationIncluded = true
        }
    }

}
