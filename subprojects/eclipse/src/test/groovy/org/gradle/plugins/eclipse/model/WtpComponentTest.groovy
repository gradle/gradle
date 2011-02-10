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
import org.gradle.plugins.eclipse.EclipseWtpComponent

import org.junit.Rule
import spock.lang.Specification

/**
 * @author Hans Dockter
 */
public class WtpComponentTest extends Specification {
    private static final List CUSTOM_WB_MODULE_ENTRIES = [
            new WbDependentModule('/WEB-INF/lib', "module:/classpath/myapp-1.0.0.jar"),
            new WbResource("/WEB-INF/classes", "src/main/java")]

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    def initWithReader() {
        WtpComponent component = createComponent(componentReader: customComponentReader)

        expect:
        component.deployName == 'recu'
        component.contextPath == 'recu'
        component.wbModuleEntries == CUSTOM_WB_MODULE_ENTRIES
    }

    def initWithReaderAndValues_shouldBeMerged() {
        def constructorDeployName = 'build'
        def constructorContextPath = 'context'
        def constructorWbModuleEntries = [createSomeWbModuleEntry()]

        def component = createComponent(wbModuleEntries: constructorWbModuleEntries + [CUSTOM_WB_MODULE_ENTRIES[0]],
                deployName: constructorDeployName, contextPath: constructorContextPath, componentReader: customComponentReader)

        expect:
        component.wbModuleEntries == CUSTOM_WB_MODULE_ENTRIES + constructorWbModuleEntries
        component.deployName == constructorDeployName
        component.contextPath == constructorContextPath
    }

    def initWithNullReader() {
        def constructorDeployName = 'build'
        def constructorWbModuleEntries = [createSomeWbModuleEntry()]

        def component = createComponent(wbModuleEntries: constructorWbModuleEntries, deployName: constructorDeployName)

        expect:
        component.xmlDocument != null
        component.wbModuleEntries == constructorWbModuleEntries
        component.deployName == constructorDeployName
    }

    def toXml() {
        when:
        def component = createComponent(componentReader: customComponentReader)

        then:
        File componentFile = tmpDir.file("component.xml")
        def componentReader = getComponentReader(component)
        component.toXml(componentFile)
        assertXmlIsWritten(componentFile, component.xmlDocument, componentReader)
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

        def component = createComponent(wbModuleEntries: constructorWbModuleEntries,
                deployName: constructorDeployName, contextPath: constructorContextPath,
                componentReader: customComponentReader)
        def componentReader = getComponentReader(component)

        when:
        def wtpFromXml = createComponent(componentReader: componentReader)

        then:
        component == wtpFromXml
    }

    def beforeConfigured() {
        def constructorWbModuleEntries = [createSomeWbModuleEntry()]
        ActionBroadcast beforeConfiguredActions = new ActionBroadcast()
        beforeConfiguredActions.add { WtpComponent component ->
            component.wbModuleEntries.clear()
        }

        when:
        def component = createComponent(wbModuleEntries: constructorWbModuleEntries,
                componentReader: customComponentReader,
                beforeConfiguredActions: beforeConfiguredActions)
        def componentReader = getComponentReader(component)
        def wtpFromXml = createComponent(componentReader: componentReader)

        then:
        wtpFromXml.wbModuleEntries == constructorWbModuleEntries
    }

    def whenConfigured() {
        def constructorWbModuleEntry = createSomeWbModuleEntry()
        def configureActionWbModuleEntry = createSomeWbModuleEntry()
        configureActionWbModuleEntry.name = configureActionWbModuleEntry.name + 'Other'

        ActionBroadcast whenConfiguredActions = new ActionBroadcast()
        whenConfiguredActions.add { WtpComponent component ->
            assert component.wbModuleEntries.contains(CUSTOM_WB_MODULE_ENTRIES[0])
            assert component.wbModuleEntries.contains(constructorWbModuleEntry)
            component.wbModuleEntries.add(configureActionWbModuleEntry)
        }

        when:
        def component = createComponent(wbModuleEntries: [constructorWbModuleEntry], componentReader: customComponentReader,
                whenConfiguredActions: whenConfiguredActions)
        def componentReader = getComponentReader(component)
        then:
        createComponent(componentReader: componentReader).wbModuleEntries == CUSTOM_WB_MODULE_ENTRIES +
                ([constructorWbModuleEntry, configureActionWbModuleEntry])
    }

    def withXml() {
        ActionBroadcast withXmlActions = new ActionBroadcast()
        def component = createComponent(componentReader: customComponentReader, withXmlActions: withXmlActions)

        when:
        withXmlActions.add { xmls ->
            xmls['org.eclipse.wst.commons.component'].'wb-module'.property.find { it.@name == 'context-root' }.@value = 'newValue'
        }
        def componentReader = getComponentReader(component)

        then:
        new XmlParser().parse(componentReader).'wb-module'.property.find { it.@name == 'context-root' }.@value == 'newValue'
    }

    private InputStreamReader getCustomComponentReader() {
        return new InputStreamReader(getClass().getResourceAsStream('customOrgEclipseWstCommonComponent.xml'))
    }

    private WbProperty createSomeWbModuleEntry() {
        return new WbProperty('someProp', 'someValue')
    }

    private WtpComponent createComponent(Map customArgs) {
        ActionBroadcast dummyBroadcast = new ActionBroadcast()
        Map args = [wbModuleEntries: [], deployName: null, defaultOutput: null, componentReader: null,
                beforeConfiguredActions: dummyBroadcast, whenConfiguredActions: dummyBroadcast, withXmlActions: dummyBroadcast] + customArgs
        EclipseWtpComponent eclipseComponent = Mock()
        eclipseComponent.getBeforeConfiguredActions() >> args.beforeConfiguredActions
        eclipseComponent.getWhenConfiguredActions() >> args.whenConfiguredActions
        eclipseComponent.getWithXmlActions() >> args.withXmlActions
        eclipseComponent.getDeployName() >> args.deployName
        eclipseComponent.getContextPath() >> args.contextPath
        return new WtpComponent(eclipseComponent, args.wbModuleEntries, args.componentReader)
    }

    private Reader getComponentReader(WtpComponent component) {
        StringWriter output = new StringWriter()
        component.toXml(output)
        new StringReader(output.toString())
    }
}