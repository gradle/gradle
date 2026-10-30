/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.composite.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.LeaksFileHandles
import spock.lang.Issue

@LeaksFileHandles("Kotlin Compiler Daemon working directory")
@Issue("https://github.com/gradle/gradle/issues/29652")
class PluginBuildsMultiPluginApplicationIntegrationTest extends AbstractIntegrationSpec {
    def "can apply plugin from included build with version"() {
        settingsKotlinFile << """
include("child-project")
includeBuild(rootDir.resolve("other-build"))

dependencyResolutionManagement {
    versionCatalogs {
        register("libs") {
            plugin("otherBuildPlugin", "internal.integTest.other-build-plugin")
                .version("1.0")
        }
    }
}
"""

        buildKotlinFile << """
plugins {
    alias(libs.plugins.otherBuildPlugin)
}
"""

        file("child-project", "build.gradle.kts") << """
plugins {
    alias(libs.plugins.otherBuildPlugin)
}
"""

        file("other-build", "settings.gradle.kts") << """
rootProject.name = "other-build"
dependencyResolutionManagement.repositories.gradlePluginPortal()
"""

        file("other-build", "build.gradle.kts") << """
plugins {
    `kotlin-dsl`
}

group = "internal.integTest.\${rootProject.name}"
version = "1.1"
"""

        file("other-build", "src", "main", "kotlin", "internal.integTest.other-build-plugin.gradle.kts") << """
tasks.register("otherBuildPluginTask") {
    doLast {
        println("Hello from \$path")
    }
}
"""

        when:
        succeeds "otherBuildPluginTask"

        then:
        outputContains "Hello from :otherBuildPluginTask"
        outputContains "Hello from :child-project:otherBuildPluginTask"
    }
}
