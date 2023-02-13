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
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

public class WtpComponentTest extends Specification {
    private static final List CUSTOM_WB_MODULE_ENTRIES = [
            new WbDependentModule('myapp-1.0.0.jar', '/WEB-INF/lib', "module:/classpath/myapp-1.0.0.jar"),
            new WbResource("/WEB-INF/classes", "src/main/java")]

    private final WtpComponent component = new WtpComponent(new XmlTransformer())

    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

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
        def constructorWbModuleEntries = createSomeWbModuleEntries()

        when:
        component.load(customComponentReader)
        component.configure(constructorDeployName, constructorContextPath, constructorWbModuleEntries)

        then:
        component.deployName == constructorDeployName
        component.contextPath == constructorContextPath
        // dependent modules are replaced, other entries are added up
        component.wbModuleEntries as Set == [
                new WbDependentModule('foo-1.2.3.jar', '/WEB-INF/lib', "module:/classpath/foo-1.2.3.jar"),
                new WbResource("/WEB-INF/classes", "src/main/java"),
                new WbResource("/WEB-INF/classes", "src/other/java")] as Set
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

    private List createSomeWbModuleEntries() {
        [new WbDependentModule('foo-1.2.3.jar', '/WEB-INF/lib', "module:/classpath/foo-1.2.3.jar"),
        new WbResource("/WEB-INF/classes", "src/other/java")]
    }
}
