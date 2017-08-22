/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.test.xctest.plugins

import org.gradle.language.swift.SwiftComponent
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import spock.lang.Specification

class XCTestConventionPluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("testApp").build()

    def "adds extension with convention for source layout"() {
        given:
        def src = projectDir.file("src/test/swift/test.swift").createFile()

        when:
        project.pluginManager.apply(XCTestConventionPlugin)

        then:
        project.xctest instanceof SwiftComponent
        project.xctest.swiftSource.files == [src] as Set
    }

    def "registers a component for the test suite"() {
        when:
        project.pluginManager.apply(XCTestConventionPlugin)

        then:
        project.components.test == project.xctest
    }


}
