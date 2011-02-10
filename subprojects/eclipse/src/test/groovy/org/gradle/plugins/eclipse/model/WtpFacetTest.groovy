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
package org.gradle.plugins.eclipse.model

import org.gradle.listener.ActionBroadcast
import org.gradle.util.TemporaryFolder
import org.gradle.util.TestFile
import org.gradle.plugins.eclipse.EclipseWtpFacet

import org.junit.Rule
import spock.lang.Specification

/**
 * @author Hans Dockter
 */
public class WtpFacetTest extends Specification {
    private static final List CUSTOM_FACETS = [new Facet('jst.web', '2.4'), new Facet('jst.java', '1.4')]

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    def initWithReader() {
        WtpFacet facet = createFacet(facetReader: customFacetReader)

        expect:
        facet.facets == CUSTOM_FACETS
    }

    def initWithReaderAndValues_shouldBeMerged() {
        def constructorFacets = [createSomeFacet()]

        WtpFacet facet = createFacet(facets: constructorFacets + [CUSTOM_FACETS[0]], facetReader: customFacetReader)

        expect:
        facet.facets == CUSTOM_FACETS + constructorFacets
    }

    def initWithNullReader() {
        def constructorFacets = [createSomeFacet()]

        WtpFacet facet = createFacet(facets: constructorFacets)

        expect:
        facet.xmlDocument != null
        facet.facets == constructorFacets
    }

    def toXml() {
        when:
        WtpFacet facet = createFacet(facetReader: customFacetReader)

        then:
        File facetFile = tmpDir.file("facet.xml")
        def facetReader = getFacetReader(facet)
        facet.toXml(facetFile)
        assertXmlIsWritten(facetFile, facet.xmlDocument, facetReader)
    }

    void assertXmlIsWritten(TestFile file, Node xml, Reader reader) {
        StringWriter stringWriterFileXml = new StringWriter()
        new XmlNodePrinter(new PrintWriter(stringWriterFileXml)).print(new XmlParser().parse(file))
        StringWriter stringWriterWrittenXml = new StringWriter()
        new XmlNodePrinter(new PrintWriter(stringWriterWrittenXml)).print(new XmlParser().parse(reader))
        StringWriter stringWriterInternalXml = new StringWriter()
        new XmlNodePrinter(new PrintWriter(stringWriterInternalXml)).print(xml)

        assert stringWriterWrittenXml.toString() == stringWriterInternalXml.toString()
        assert stringWriterWrittenXml.toString() == stringWriterFileXml.toString()
    }

    def toXml_shouldContainCustomValues() {
        def constructorFacets = [createSomeFacet()]

        WtpFacet facet = createFacet(facets: constructorFacets, facetReader: customFacetReader)
        def facetReader = getFacetReader(facet)

        when:
        def wtpFromXml = createFacet(facetReader: facetReader)

        then:
        facet == wtpFromXml
    }

    def beforeConfigured() {
        def constructorFacets = [createSomeFacet()]
        ActionBroadcast beforeConfiguredActions = new ActionBroadcast()
        beforeConfiguredActions.add { WtpFacet facet -> facet.facets.clear() }

        when:
        WtpFacet facet = createFacet(facets: constructorFacets, facetReader: customFacetReader, beforeConfiguredActions: beforeConfiguredActions)
        def facetReader = getFacetReader(facet)
        def wtpFromXml = createFacet(facetReader: facetReader)

        then:
        wtpFromXml.facets == constructorFacets
    }

    def withXml() {
        ActionBroadcast withXmlActions = new ActionBroadcast()
        def facet = createFacet(facetReader: customFacetReader, withXmlActions: withXmlActions)

        when:
        withXmlActions.add { xmls ->
            xmls['org.eclipse.wst.commons.project.facet.core'].installed.find { it.@facet == 'jst.java' }.@version = '-5x'
        }
        def facetReader = getFacetReader(facet)

        then:
        new XmlParser().parse(facetReader).installed.find { it.@facet == 'jst.java' }.@version == '-5x'
    }

    private InputStreamReader getCustomFacetReader() {
        return new InputStreamReader(getClass().getResourceAsStream('customOrgEclipseWstCommonProjectFacetCoreXml.xml'))
    }

    private Facet createSomeFacet() {
        return new Facet('someName', '1.0.0')
    }

    private WtpFacet createFacet(Map customArgs) {
        ActionBroadcast dummyBroadcast = new ActionBroadcast()
        Map args = [facets: [], defaultOutput: null, facetReader: null, beforeConfiguredActions: dummyBroadcast,
                whenConfiguredActions: dummyBroadcast, withXmlActions: dummyBroadcast] + customArgs
        EclipseWtpFacet eclipseFacet = Mock()
        eclipseFacet.getBeforeConfiguredActions() >> args.beforeConfiguredActions
        eclipseFacet.getWhenConfiguredActions() >> args.whenConfiguredActions
        eclipseFacet.getWithXmlActions() >> args.withXmlActions
        eclipseFacet.getFacets() >> args.facets
        return new WtpFacet(eclipseFacet, args.facetReader)
    }

    private getFacetReader(WtpFacet facet) {
        StringWriter toFacetXmlText = new StringWriter()
        facet.toXml(toFacetXmlText)
        new StringReader(toFacetXmlText.toString())
    }
}