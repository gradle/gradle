/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    public void processSampleLocation(Element parentElement) {
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
            textElement.appendChild(doc.createTextNode(' which is in both the binary and source distributions of Gradle.'))
            filenameElement.appendChild(doc.createTextNode("samples/$srcDir"))

            parentElement.appendChild(tipElement)

            locationIncluded = true
        }
    }

}
