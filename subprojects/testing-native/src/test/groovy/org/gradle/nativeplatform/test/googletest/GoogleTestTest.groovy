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
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.type.ModelTypes
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.nativeplatform.test.googletest.plugins.GoogleTestPlugin
import org.gradle.platform.base.test.TestSuiteSpec
import org.gradle.util.TestUtil
import spock.lang.Specification

class GoogleTestTest extends Specification {
    final def project = TestUtil.createRootProject();

    def "creates a test suite for each library under test"() {
        given:
        project.apply(plugin: CppPlugin)
        project.apply(plugin: GoogleTestPlugin)
        project.model {
            components {
                main(NativeLibrarySpec)
            }
        }
        project.evaluate()

        when:
        GoogleTestTestSuiteSpec testSuite = project.modelRegistry.realize(ModelPath.path("testSuites"), ModelTypes.modelMap(TestSuiteSpec)).mainTest
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
