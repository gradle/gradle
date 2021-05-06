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
package org.gradle.plugins.ide.internal.generator

import org.gradle.api.Action
import org.gradle.api.XmlProvider
import org.gradle.internal.xml.XmlTransformer
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.Specification

class XmlPersistableConfigurationObjectTest extends Specification {
    @Rule public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    String rootElement
    final XmlPersistableConfigurationObject object = new XmlPersistableConfigurationObject(new XmlTransformer()) {
        @Override protected String getDefaultResourceName() {
            return 'defaultResource.xml'
        }

        @Override protected void load(Node xml) {
            rootElement = xml.attributes().get("name") as String
        }

        @Override protected void store(Node xml) {
            xml.attributes().put("name", rootElement)
        }
    }

    def loadsFromXmlFile() {
        def inputFile = tmpDir.file('input.xml')
        inputFile.text = '<root name="some-xml"/>'

        when:
        object.load(inputFile)

        then:
        rootElement == 'some-xml'
    }

    def loadsFromDefaultResource() {
        when:
        object.loadDefaults()

        then:
        rootElement == 'default-xml'
    }

    def storesToXmlFile() {
        object.loadDefaults()
        rootElement = 'modified-xml'
        def outputFile = tmpDir.file('output.xml')

        when:
        object.store(outputFile)

        then:
        outputFile.text == TextUtil.toPlatformLineSeparators('<?xml version="1.0" encoding="UTF-8"?>\n<root name="modified-xml"/>\n')
    }

    def "can add transform Actions"() {
        object.loadDefaults()
        def outputFile = tmpDir.file('output.xml')

        when:
        object.transformAction({ xmlProvider ->
            xmlProvider.asNode().attributes().put('some', 'attribute')
        } as Action<XmlProvider>)

        and:
        object.store(outputFile)

        then:
        outputFile.text == TextUtil.toPlatformLineSeparators('<?xml version="1.0" encoding="UTF-8"?>\n<root name="default-xml" some="attribute"/>\n')
    }
}
