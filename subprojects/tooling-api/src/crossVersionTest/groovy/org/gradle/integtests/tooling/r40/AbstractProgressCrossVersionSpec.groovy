/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.tooling.r40

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.util.GradleVersion

class AbstractProgressCrossVersionSpec extends ToolingApiSpecification {

    String applyInitScript(File script) {
        if (targetVersion.baseVersion >= GradleVersion.version("6.6")) {
            return "Apply initialization script '${pathOfScript(script)}' to build"
        } else {
            return "Apply script ${script.name} to build"
        }
    }

    String applyInitScriptPlugin(File script) {
        if (targetVersion.baseVersion >= GradleVersion.version("6.6")) {
            return "Apply script '${pathOfScript(script)}' to build"
        } else {
            return "Apply script ${script.name} to build"
        }
    }

    String applySettingsFile() {
        if (targetVersion.baseVersion >= GradleVersion.version("6.6")) {
            return "Apply settings file '${pathOfScript(settingsFile)}' to settings '${projectDir.name}'"
        } else {
            return "Apply script settings.gradle to settings '${projectDir.name}'"
        }
    }

    String applySettingsScriptPlugin(File script, String name) {
        if (targetVersion.baseVersion >= GradleVersion.version("6.6")) {
            return "Apply script '${pathOfScript(script)}' to settings '${name}'"
        } else {
            return "Apply script ${script.name} to settings '${name}'"
        }
    }

    String applyBuildScriptRootProject(String project = 'single') {
        if (targetVersion.baseVersion >= GradleVersion.version("6.6")) {
            return "Apply build file '${pathOfScript(buildFile)}' to root project '$project'"
        } else {
            return "Apply script build.gradle to root project '$project'"
        }
    }

    String applyBuildScriptPluginRootProject(File script, String project = 'single') {
        if (targetVersion.baseVersion >= GradleVersion.version("6.6")) {
            return "Apply script '${pathOfScript(script)}' to root project '$project'"
        } else {
            return "Apply script ${script.name} to root project '$project'"
        }
    }

    String applyBuildScript(File script, String project) {
        if (targetVersion.baseVersion >= GradleVersion.version("6.6")) {
            return "Apply build file '${pathOfScript(script)}' to project '$project'"
        } else {
            return "Apply script ${script.name} to project '$project'"
        }
    }

    String applyBuildScriptPlugin(File script, String project) {
        if (targetVersion.baseVersion >= GradleVersion.version("6.6")) {
            return "Apply script '${pathOfScript(script)}' to project '$project'"
        } else {
            return "Apply script ${script.name} to project '$project'"
        }
    }

    private String pathOfScript(File script) {
        return temporaryFolder.testDirectory.relativePath(script).replace('/', File.separator)
    }
}
