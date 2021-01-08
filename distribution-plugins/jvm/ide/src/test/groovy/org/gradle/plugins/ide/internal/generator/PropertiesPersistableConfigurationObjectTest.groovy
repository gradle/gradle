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
import org.gradle.api.internal.PropertiesTransformer
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Matchers
import org.junit.Rule
import spock.lang.Specification

class PropertiesPersistableConfigurationObjectTest extends Specification {
    @Rule public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    String propertyValue
    final org.gradle.plugins.ide.internal.generator.PropertiesPersistableConfigurationObject object = new org.gradle.plugins.ide.internal.generator.PropertiesPersistableConfigurationObject(new PropertiesTransformer()) {
        @Override protected String getDefaultResourceName() {
            return 'defaultResource.properties'
        }

        @Override protected void load(Properties properties) {
            propertyValue = properties['prop']
        }

        @Override protected void store(Properties properties) {
            properties['prop'] = propertyValue
        }
    }

    def loadsFromPropertiesFile() {
        def inputFile = tmpDir.file('input.properties')
        inputFile.text = 'prop=value'

        when:
        object.load(inputFile)

        then:
        propertyValue == 'value'
    }

    def loadsFromDefaultResource() {
        when:
        object.loadDefaults()

        then:
        propertyValue == 'default-value'
    }

    def storesToXmlFile() {
        object.loadDefaults()
        propertyValue = 'modified-value'
        def outputFile = tmpDir.file('output.properties')

        when:
        object.store(outputFile)

        then:
        Matchers.containsLine(outputFile.text, 'prop=modified-value')
    }

    def "can add transform Actions"() {
        object.loadDefaults()
        def outputFile = tmpDir.file('output.properties')

        when:
        object.transformAction({ properties ->
            properties.put('some', 'property')
        } as Action<Properties>)

        and:
        object.store(outputFile)

        then:
        Matchers.containsLine(outputFile.text, 'some=property')
    }
}
