/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.nativeplatform.test.googletest
import org.gradle.language.cpp.CppSourceSet
import org.gradle.language.cpp.plugins.CppPlugin
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.nativeplatform.test.googletest.plugins.GoogleTestConventionPlugin
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testing.base.TestSuiteSpec
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.model.internal.type.ModelTypes.modelMap

class GoogleTestTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider(getClass())
    final def project = TestUtil.create(testDir).rootProject();

    def "creates a test suite for each library under test"() {
        given:
        project.apply(plugin: CppPlugin)
        project.apply(plugin: GoogleTestConventionPlugin)
        project.model {
            components {
                main(NativeLibrarySpec)
            }
        }
        project.evaluate()

        when:
        GoogleTestTestSuiteSpec testSuite = project.modelRegistry.realize("testSuites", modelMap(TestSuiteSpec)).mainTest
        def sources = testSuite.sources.values()
        def binaries = testSuite.binaries.values()

        then:
        sources.size() == 1
        sources.every { it instanceof CppSourceSet }

        and:
        binaries.size() == 1
        binaries.every { it instanceof GoogleTestTestSuiteBinarySpec }
    }
}
