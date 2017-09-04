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

package org.gradle.ide.xcode.fixtures

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile

class AbstractXcodeIntegrationSpec extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
allprojects {
    apply plugin: 'xcode'
}
"""
        settingsFile << """
rootProject.name = "${rootProjectName}"
"""
    }

    protected String getRootProjectName() {
        'app'
    }

    protected TestFile exe(String str) {
        file(OperatingSystem.current().getExecutableName(str))
    }

    protected TestFile sharedLib(String str) {
        file(OperatingSystem.current().getSharedLibraryName(str))
    }

    protected XcodeProjectPackage xcodeProject(String path) {
        new XcodeProjectPackage(file(path))
    }

    protected XcodeWorkspacePackage xcodeWorkspace(String path) {
        new XcodeWorkspacePackage(file(path))
    }
}
