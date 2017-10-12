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

package org.gradle.nativeplatform.test.xctest.internal

import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.internal.file.DefaultProjectLayout
import org.gradle.api.internal.file.TestFiles
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

@Subject(DefaultSwiftXCTestSuite)
class DefaultSwiftXCTestSuiteTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def fileOperations = TestFiles.fileOperations(tmpDir.testDirectory)
    def projectLayout = new DefaultProjectLayout(tmpDir.testDirectory, TestFiles.resolver(tmpDir.testDirectory))
    def testSuite = new DefaultSwiftXCTestSuite("test", TestUtil.objectFactory(), fileOperations, Stub(ConfigurationContainer), projectLayout)

    def "has a bundle"() {
        expect:
        testSuite.bundle.name == "testBundle"
        testSuite.bundle.debuggable
        testSuite.developmentBinary == testSuite.bundle
    }

    def "can change location of Info.plist by changing the test suite resource directory location"() {
        def file = tmpDir.createFile("Tests")

        expect:
        testSuite.resourceDir.set(file)
        testSuite.bundle.informationPropertyList.get().asFile == tmpDir.file("Tests/Info.plist")
    }

    def "uses source layout convention when Info.plist not set"() {
        expect:
        testSuite.bundle.informationPropertyList.get().asFile == tmpDir.file("src/test/resources/Info.plist")
    }
}
