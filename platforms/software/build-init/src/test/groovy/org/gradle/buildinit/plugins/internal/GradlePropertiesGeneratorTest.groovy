/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.buildinit.plugins.internal

import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class GradlePropertiesGeneratorTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    InitSettings settings = Mock()
    File propertiesFile = tmpDir.file("gradle.properties")

    def setup() {
        Directory target = Mock()
        RegularFile propertiesRegularFile = Mock()
        settings.target >> target
        target.file('gradle.properties') >> propertiesRegularFile
        propertiesRegularFile.asFile >> propertiesFile
    }

    def "generates gradle.properties file if incubating"() {
        setup:
        def generator = new GradlePropertiesGenerator()
        settings.isUseIncubatingAPIs() >> true

        when:
        generator.generate(settings, null)

        then:
        propertiesFile.file
        propertiesFile.text.contains('org.gradle.parallel=true')
        propertiesFile.text.contains('org.gradle.caching=true')
    }

    def "doesn't generate gradle.properties file if not incubating"() {
        setup:
        def generator = new GradlePropertiesGenerator()
        settings.isUseIncubatingAPIs() >> false

        when:
        generator.generate(settings, null)

        then:
        !propertiesFile.file
    }
}
