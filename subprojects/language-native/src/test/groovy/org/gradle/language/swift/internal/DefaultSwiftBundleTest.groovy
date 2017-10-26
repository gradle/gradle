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

package org.gradle.language.swift.internal

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.DefaultProjectLayout
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.provider.Provider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

@Subject(DefaultSwiftBundle)
class DefaultSwiftBundleTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def resourceDirectory
    DefaultSwiftBundle binary

    def setup() {
        def projectLayout = new DefaultProjectLayout(tmpDir.testDirectory, TestFiles.resolver(tmpDir.testDirectory))
        resourceDirectory = projectLayout.directoryProperty()
        resourceDirectory.set(tmpDir.file("resources"))

        binary = new DefaultSwiftBundle("mainDebug", projectLayout, TestUtil.objectFactory(), Stub(Provider), true, Stub(FileCollection),  Stub(ConfigurationContainer), Stub(Configuration), resourceDirectory)
    }

    def "honor changes to resource directory for the location of Info.plist"() {
        def file = tmpDir.file("Tests")

        expect:
        resourceDirectory.set(file)
        binary.informationPropertyList.get().asFile == tmpDir.file("Tests/Info.plist")
    }

    def "base the location of Info.plist on the resource directory"() {
        expect:
        binary.informationPropertyList.get().asFile == new File(resourceDirectory.asFile.get(), "Info.plist")
    }
}
