/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.nativeintegration.jansi

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.internal.nativeintegration.jansi.JansiBootPathConfigurer.JANSI_LIBRARY_PATH_SYS_PROP

class JansiBootPathConfigurerTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def configurer = new JansiBootPathConfigurer()

    String originalJansiPath

    def setup() {
        originalJansiPath = System.getProperty(JANSI_LIBRARY_PATH_SYS_PROP)
    }

    def cleanup() {
        if (originalJansiPath == null) {
            System.clearProperty(JANSI_LIBRARY_PATH_SYS_PROP)
        } else {
            System.setProperty(JANSI_LIBRARY_PATH_SYS_PROP, originalJansiPath)
        }
    }

    def "does not leak library.jansi.path system property after configure"() {
        given:
        System.clearProperty(JANSI_LIBRARY_PATH_SYS_PROP)

        when:
        configurer.configure(tmpDir.testDirectory)

        then:
        System.getProperty(JANSI_LIBRARY_PATH_SYS_PROP) == null
    }

    def "restores previous library.jansi.path value after configure"() {
        given:
        def previousValue = "/some/previous/jansi/path"
        System.setProperty(JANSI_LIBRARY_PATH_SYS_PROP, previousValue)

        when:
        configurer.configure(tmpDir.testDirectory)

        then:
        System.getProperty(JANSI_LIBRARY_PATH_SYS_PROP) == previousValue
    }
}
