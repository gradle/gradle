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
import org.gradle.api.internal.xml.XmlTransformer
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

/**
 * @author Hans Dockter
 */
public class WtpComponentTest extends Specification {
    private static final List CUSTOM_WB_MODULE_ENTRIES = [
            new WbDependentModule('/WEB-INF/lib', "module:/classpath/myapp-1.0.0.jar"),
            new WbResource("/WEB-INF/classes", "src/main/java")]

    private final WtpComponent component = new WtpComponent(new XmlTransformer())

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder()

    def "load existing XML file"() {
        when:
        component.load(customComponentReader)

        then:
        component.deployName == 'recu'
        component.contextPath == 'recu'
        component.wbModuleEntries == CUSTOM_WB_MODULE_ENTRIES
    }

    def "merge existing and new configuration"() {
        def constructorDeployName = 'build'
        def constructorContextPath = 'context'
        def constructorWbModuleEntries = [createSomeWbModuleEntry()]

        when:
        component.load(customComponentReader)
        component.configure(constructorDeployName, constructorContextPath, constructorWbModuleEntries + [CUSTOM_WB_MODULE_ENTRIES[0]])

        then:
        component.wbModuleEntries == CUSTOM_WB_MODULE_ENTRIES + constructorWbModuleEntries
        component.deployName == constructorDeployName
        component.contextPath == constructorContextPath
    }

    def "load defaults"() {
        when:
        component.loadDefaults()

        then:
        component.xml != null
        component.wbModuleEntries == []
        component.deployName == null
        component.contextPath == null
    }

    def "roundtripping the component file leaves it unchanged"() {
        when:
        component.load(customComponentReader)
        def roundTripped = tmpDir.file("component.xml")
        component.store(roundTripped)

        then:
        XMLUnit.compareXML(customComponentReader.text, roundTripped.text).identical()

    }

    private InputStream getCustomComponentReader() {
        getClass().getResourceAsStream('customOrgEclipseWstCommonComponent.xml')
    }

    private WbProperty createSomeWbModuleEntry() {
        return new WbProperty('someProp', 'someValue')
    }
}