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
package org.gradle.initialization

import org.gradle.api.internal.GradleDistributionLocator
import org.gradle.api.internal.GradleInternal
import org.gradle.groovy.scripts.UriScriptSource
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DistributionInitScriptFinderTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final def distDir = tmpDir.createDir("gradle-home")
    final GradleDistributionLocator locator = Mock()
    final GradleInternal gradle = Mock()
    final DistributionInitScriptFinder finder = new DistributionInitScriptFinder(locator)

    def setup() {
    }

    def "does nothing when init.d directory does not exist there is no distribution"() {
        def scripts = []

        given:
        _ * locator.gradleHome >> null

        when:
        finder.findScripts(gradle, scripts)

        then:
        scripts.empty
    }

    def "does nothing when init.d directory does not exist in distribution"() {
        def scripts = []

        given:
        _ * locator.gradleHome >> distDir

        when:
        finder.findScripts(gradle, scripts)

        then:
        scripts.empty
    }

    def "locates each script from init.d directory"() {
        def scripts = []

        given:
        def script1 = distDir.createFile("init.d/script1.gradle")
        def script2 = distDir.createFile("init.d/script2.gradle")
        distDir.createFile("init.d/readme.txt")
        distDir.createFile("init.d/lib/test.jar")

        and:
        _ * locator.gradleHome >> distDir

        when:
        finder.findScripts(gradle, scripts)

        then:
        scripts.size() == 2
        scripts.find {
            it instanceof UriScriptSource && it.resource.sourceFile == script1
        }
        scripts.find {
            it instanceof UriScriptSource && it.resource.sourceFile == script2
        }
    }
}
