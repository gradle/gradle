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

    protected XcodeProjectPackage xcodeProject(String path) {
        new XcodeProjectPackage(file(path))
    }

    protected XcodeWorkspacePackage xcodeWorkspace(String path) {
        new XcodeWorkspacePackage(file(path))
    }

    protected void assertProjectHasEqualsNumberOfGradleAndIndexTargets(def targets) {
        assert targets.findAll(gradleTargets()).size() == targets.size() / 2
        assert targets.findAll(indexTargets()).size() == targets.size() / 2
    }

    protected static def gradleTargets() {
        return {
            it.isa == 'PBXLegacyTarget'
        }
    }

    protected static def indexTargets() {
        return {
            it.isa == 'PBXNativeTarget'
        }
    }
}
