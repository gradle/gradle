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
package org.gradle.plugins.ide.eclipse.model

import groovy.xml.XmlParser
import org.gradle.plugins.ide.eclipse.model.Facet.FacetType
import spock.lang.Specification

class FacetTest extends Specification {
    final static String XML_TEXT = '<installed facet="jst.web" version="2.4"/>'
    final static String FIXED_XML_TEXT = '<fixed facet="jst.web"/>'

    def canReadFromXml() {
        when:
        Facet facet = new Facet(new XmlParser().parseText(XML_TEXT))

        then:
        facet == createFacet()
    }

    def canReadFixedFromXml() {
        when:
        Facet facet = new Facet(new XmlParser().parseText(FIXED_XML_TEXT))

        then:
        facet == createFixedFacet()
    }

    def canWriteToXml() {
        Node rootNode = new Node(null, 'root')

        when:
        createFacet().appendNode(rootNode)

        then:
        new Facet(rootNode.installed[0]) == createFacet()
    }

    def canWriteFixedToXml() {
        Node rootNode = new Node(null, 'root')

        when:
        createFixedFacet().appendNode(rootNode)

        then:
        new Facet(rootNode.fixed[0]) == createFixedFacet()
    }

    def equality() {
        Facet facet = createFacet()
        facet.name += 'x'

        expect:
        facet != createFacet()
    }

    def fixedEquality() {
        Facet facet = createFixedFacet()
        facet.name += 'x'

        expect:
        facet != createFixedFacet()
    }

    private Facet createFacet() {
        return new Facet(FacetType.installed, "jst.web", "2.4")
    }

    private Facet createFixedFacet() {
        return new Facet(FacetType.fixed, "jst.web", null)
    }


}
