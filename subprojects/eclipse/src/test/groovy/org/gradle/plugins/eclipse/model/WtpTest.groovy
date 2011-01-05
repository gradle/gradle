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
package org.gradle.plugins.eclipse.model;


import org.gradle.listener.ActionBroadcast
import org.gradle.plugins.eclipse.EclipseWtp
import org.gradle.util.TemporaryFolder
import org.gradle.util.TestFile
import org.junit.Rule
import spock.lang.Specification

/**
 * @author Hans Dockter
 */

public class WtpTest extends Specification {
    private static final List CUSTOM_WB_MODULE_ENTRIES = [
            new WbDependentModule('/WEB-INF/lib', "module:/classpath/myapp-1.0.0.jar"),
            new WbResource("/WEB-INF/classes", "src/main/java")]
    private static final List CUSTOM_FACETS = [new Facet('jst.web', '2.4'), new Facet('jst.java', '1.4')]

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    def initWithReader() {
        Wtp wtp = createWtp(componentReader: customComponentReader, facetReader: customFacetReader)

        expect:
        wtp.deployName == 'recu'
        wtp.contextPath == 'recu'
        wtp.wbModuleEntries == CUSTOM_WB_MODULE_ENTRIES
        wtp.facets == CUSTOM_FACETS
    }

    def initWithReaderAndValues_shouldBeMerged() {
        def constructorDeployName = 'build'
        def constructorContextPath = 'context'
        def constructorWbModuleEntries = [createSomeWbModuleEntry()]
        def constructorFacets = [createSomeFacet()]

        Wtp wtp = createWtp(wbModuleEntries: constructorWbModuleEntries + [CUSTOM_WB_MODULE_ENTRIES[0]], facets: constructorFacets + [CUSTOM_FACETS[0]],
                deployName: constructorDeployName, contextPath: constructorContextPath, componentReader: customComponentReader, facetReader: customFacetReader)

        expect:
        wtp.wbModuleEntries == CUSTOM_WB_MODULE_ENTRIES + constructorWbModuleEntries
        wtp.deployName == constructorDeployName
        wtp.contextPath == constructorContextPath
        wtp.facets == CUSTOM_FACETS + constructorFacets
    }

    def initWithNullReader() {
        def constructorDeployName = 'build'
        def constructorWbModuleEntries = [createSomeWbModuleEntry()]
        def constructorFacets = [createSomeFacet()]

        Wtp wtp = createWtp(wbModuleEntries: constructorWbModuleEntries, facets: constructorFacets,
                deployName: constructorDeployName)

        expect:
        wtp.orgEclipseWstCommonComponentXml != null
        wtp.orgEclipseWstCommonProjectFacetCoreXml != null
        wtp.wbModuleEntries == constructorWbModuleEntries
        wtp.deployName == constructorDeployName
        wtp.facets == constructorFacets
    }

    def toXml() {
        when:
        Wtp wtp = createWtp(componentReader: customComponentReader, facetReader: customFacetReader)

        then:
        File componentFile = tmpDir.file("component.xml")
        File facetFile = tmpDir.file("facet.xml")
        def (componentReader, facetReader) = getToXmlReaders(wtp)
        wtp.toXml(componentFile, facetFile)
        assertXmlIsWritten(componentFile, wtp.orgEclipseWstCommonComponentXml, componentReader)
        assertXmlIsWritten(facetFile, wtp.orgEclipseWstCommonProjectFacetCoreXml, facetReader)
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
        def constructorDeployName = 'build'
        def constructorContextPath = 'contextPath'
        def constructorWbModuleEntries = [createSomeWbModuleEntry()]
        def constructorFacets = [createSomeFacet()]

        Wtp wtp = createWtp(wbModuleEntries: constructorWbModuleEntries, facets: constructorFacets,
                deployName: constructorDeployName, contextPath: constructorContextPath,
                componentReader: customComponentReader, facetReader: customFacetReader)
        def (componentReader, facetReader) = getToXmlReaders(wtp)

        when:
        def wtpFromXml = createWtp(componentReader: componentReader, facetReader: facetReader)

        then:
        wtp == wtpFromXml
    }

    def beforeConfigured() {
        def constructorWbModuleEntries = [createSomeWbModuleEntry()]
        def constructorFacets = [createSomeFacet()]
        ActionBroadcast beforeConfiguredActions = new ActionBroadcast()
        beforeConfiguredActions.add { Wtp wtp ->
            wtp.wbModuleEntries.clear()
            wtp.facets.clear()
        }

        when:
        Wtp wtp = createWtp(wbModuleEntries: constructorWbModuleEntries, facets: constructorFacets,
                componentReader: customComponentReader,
                facetReader: customFacetReader,
                beforeConfiguredActions: beforeConfiguredActions)
        def (componentReader, facetReader) = getToXmlReaders(wtp)
        def wtpFromXml = createWtp(componentReader: componentReader, facetReader: facetReader)

        then:
        wtpFromXml.wbModuleEntries == constructorWbModuleEntries
        wtpFromXml.facets == constructorFacets
    }

    def whenConfigured() {
        def constructorWbModuleEntry = createSomeWbModuleEntry()
        def configureActionWbModuleEntry = createSomeWbModuleEntry()
        configureActionWbModuleEntry.name = configureActionWbModuleEntry.name + 'Other'

        ActionBroadcast whenConfiguredActions = new ActionBroadcast()
        whenConfiguredActions.add { Wtp wtp ->
            assert wtp.wbModuleEntries.contains(CUSTOM_WB_MODULE_ENTRIES[0])
            assert wtp.wbModuleEntries.contains(constructorWbModuleEntry)
            wtp.wbModuleEntries.add(configureActionWbModuleEntry)
        }

        when:
        Wtp wtp = createWtp(wbModuleEntries: [constructorWbModuleEntry], componentReader: customComponentReader,
                facetReader: customFacetReader, whenConfiguredActions: whenConfiguredActions)
        def (componentReader, facetReader) = getToXmlReaders(wtp)
        then:
        createWtp(componentReader: componentReader, facetReader: facetReader).wbModuleEntries == CUSTOM_WB_MODULE_ENTRIES +
                ([constructorWbModuleEntry, configureActionWbModuleEntry])
    }

    def withXml() {
        ActionBroadcast withXmlActions = new ActionBroadcast()
        Wtp wtp = createWtp(componentReader: customComponentReader,
                facetReader: customFacetReader, withXmlActions: withXmlActions)

        when:
        withXmlActions.add { xmls ->
            xmls['org.eclipse.wst.commons.component'].'wb-module'.property.find { it.@name == 'context-root' }.@value = 'newValue'
            xmls['org.eclipse.wst.commons.project.facet.core'].installed.find { it.@facet == 'jst.java' }.@version = '-5x'
        }
        def (componentReader, facetReader) = getToXmlReaders(wtp)

        then:
        new XmlParser().parse(componentReader).'wb-module'.property.find { it.@name == 'context-root' }.@value == 'newValue'
        new XmlParser().parse(facetReader).installed.find { it.@facet == 'jst.java' }.@version == '-5x'
    }

    private InputStreamReader getCustomComponentReader() {
        return new InputStreamReader(getClass().getResourceAsStream('customOrgEclipseWstCommonComponent.xml'))
    }

    private InputStreamReader getCustomFacetReader() {
        return new InputStreamReader(getClass().getResourceAsStream('customOrgEclipseWstCommonProjectFacetCoreXml.xml'))
    }

    private WbProperty createSomeWbModuleEntry() {
        return new WbProperty('someProp', 'someValue')
    }

    private Facet createSomeFacet() {
        return new Facet('someName', '1.0.0')
    }

    private Wtp createWtp(Map customArgs) {
        ActionBroadcast dummyBroadcast = new ActionBroadcast()
        Map args = [wbModuleEntries: [], facets: [], deployName: null, defaultOutput: null, componentReader: null,
                facetReader: null, beforeConfiguredActions: dummyBroadcast, whenConfiguredActions: dummyBroadcast, withXmlActions: dummyBroadcast] + customArgs
        EclipseWtp eclipseWtpStub = Mock()
        eclipseWtpStub.getBeforeConfiguredActions() >> args.beforeConfiguredActions
        eclipseWtpStub.getWhenConfiguredActions() >> args.whenConfiguredActions
        eclipseWtpStub.getWithXmlActions() >> args.withXmlActions
        eclipseWtpStub.getDeployName() >> args.deployName
        eclipseWtpStub.getContextPath() >> args.contextPath
        eclipseWtpStub.getFacets() >> args.facets
        return new Wtp(eclipseWtpStub, args.wbModuleEntries, args.componentReader, args.facetReader)
    }

    private def getToXmlReaders(Wtp wtp) {
        StringWriter toComponentXmlText = new StringWriter()
        StringWriter toFacetXmlText = new StringWriter()
        wtp.toXml(toComponentXmlText, toFacetXmlText)
        [new StringReader(toComponentXmlText.toString()), new StringReader(toFacetXmlText.toString())]
    }
}