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

package org.gradle.ide.xcode.tasks.internal

import org.gradle.ide.xcode.fixtures.SchemeFile
import org.gradle.internal.xml.XmlTransformer
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class XcodeSchemeFileTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())

    def generator = new XcodeSchemeFile(new XmlTransformer())

    def "setup"() {
        generator.loadDefaults()
    }

    def "empty scheme file"() {
        expect:
        schemeFile.file.exists()
        ["BuildAction", "TestAction", "LaunchAction", "ProfileAction", "AnalyzeAction", "ArchiveAction"].each {
            schemeFile.schemeXml."$it".size() == 1
        }
    }

    private SchemeFile getSchemeFile() {
        def file = file("scheme.xcscheme")
        generator.store(file)
        return new SchemeFile(file)
    }

    private TestFile file(String name) {
        testDirectoryProvider.testDirectory.file(name)
    }

}
