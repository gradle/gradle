/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.nativecode.cdt.model

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class ProjectDescriptorSpec extends Specification {

    @Rule public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    ProjectDescriptor descriptor = new ProjectDescriptor()

    def "method"() {
        given:
        descriptor.loadDefaults()

        when:
        new ProjectSettings(name: "test").applyTo(descriptor)

        then:
        def dict = xml.buildSpec[0].buildCommand[0].arguments[0].dictionary.key.find { it.text() == "org.eclipse.cdt.make.core.buildLocation" }.parent()
        dict.value[0].text() == "\${workspace_loc:/test/Debug}"
    }

    def getString() {
        def baos = new ByteArrayOutputStream()
        descriptor.store(baos)
        baos.toString()
    }
    
    def getXml() {
        new XmlParser().parseText(getString())
    }
}