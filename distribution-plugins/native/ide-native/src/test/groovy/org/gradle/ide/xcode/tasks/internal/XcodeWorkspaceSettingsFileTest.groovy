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

import com.dd.plist.NSDictionary
import org.gradle.api.internal.PropertyListTransformer
import org.gradle.ide.xcode.fixtures.WorkspaceSettingsFile
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class XcodeWorkspaceSettingsFileTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())

    def generator = new XcodeWorkspaceSettingsFile(new PropertyListTransformer<NSDictionary>())

    def "setup"() {
        generator.loadDefaults()
    }

    def "empty workspace settings file"() {
        expect:
        workspaceSettingsFile.file.exists()
        workspaceSettingsFile.autoCreateContextsIfNeeded == false
    }

    private WorkspaceSettingsFile getWorkspaceSettingsFile() {
        def file = file("workspace.xcsettings")
        generator.store(file)
        return new WorkspaceSettingsFile(file)
    }

    private TestFile file(String name) {
        testDirectoryProvider.testDirectory.file(name)
    }

}
