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

import org.gradle.api.internal.file.DefaultProjectLayout
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.provider.DefaultProviderFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

@Subject(DefaultSwiftXCTestBundle)
class DefaultSwiftXCTestBundleTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def fileOperations = TestFiles.fileOperations(tmpDir.testDirectory)
    def providerFactory = new DefaultProviderFactory()
    def projectLayout = new DefaultProjectLayout(tmpDir.testDirectory, TestFiles.resolver(tmpDir.testDirectory))
    def component = new DefaultSwiftXCTestBundle("test", fileOperations, providerFactory, projectLayout)

    def "can change location of Info.plist"() {
        def f = tmpDir.createFile("src/test/resources/Info.plist")

        expect:
        component.informationPropertyList.set(f)
        component.informationPropertyList.asFile.get() == f
    }

    def "uses source layout convention when Info.plist not set"() {
        expect:
        component.informationPropertyList.asFile.get() == tmpDir.createFile("src/test/resources/Info.plist")
    }
}
