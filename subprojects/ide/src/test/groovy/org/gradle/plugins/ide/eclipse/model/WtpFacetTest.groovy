/*
 * Copyright 2009 the original author or authors.
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

import org.custommonkey.xmlunit.XMLUnit
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.eclipse.model.Facet.FacetType
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

public class WtpFacetTest extends Specification {
    private static final List CUSTOM_FACETS = [new Facet(FacetType.fixed, 'jst.java', null), new Facet(FacetType.fixed, 'jst.web', null), new Facet(FacetType.installed, 'jst.web', '2.4'), new Facet(FacetType.installed, 'jst.java', '1.4')]

    private final WtpFacet facet = new WtpFacet(new XmlTransformer())

    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "load existing XML file"() {
        when:
        facet.load(customFacetReader)

        then:
        facet.facets == CUSTOM_FACETS
    }

    def "merge existing and newly added facets"() {
        def constructorFacets = [createSomeFacet()]

        when:
        facet.load(customFacetReader)
        facet.configure(constructorFacets + [CUSTOM_FACETS[0]])

        then:
        facet.facets == CUSTOM_FACETS + constructorFacets
    }

    def "load defaults"() {
        when:
        facet.loadDefaults()

        then:
        facet.xml != null
        facet.facets == []
    }

    def "roundtripping the facet file leaves it unchanged"() {
        when:
        facet.load(customFacetReader)
        def roundTripped = tmpDir.file("facet.xml")
        facet.store(roundTripped)

        then:
        XMLUnit.compareXML(customFacetReader.text, roundTripped.text).identical()
    }

    private InputStream getCustomFacetReader() {
        getClass().getResourceAsStream('customOrgEclipseWstCommonProjectFacetCoreXml.xml')
    }

    private Facet createSomeFacet() {
        new Facet(FacetType.installed, 'someName', '1.0.0')
    }
}
