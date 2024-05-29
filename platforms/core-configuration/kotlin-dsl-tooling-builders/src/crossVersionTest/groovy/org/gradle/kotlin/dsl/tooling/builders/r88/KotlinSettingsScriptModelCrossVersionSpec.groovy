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

package org.gradle.kotlin.dsl.tooling.builders.r88

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.test.fixtures.Flaky

@TargetGradleVersion(">=8.8")
@Flaky(because = "https://github.com/gradle/gradle-private/issues/3714")
class KotlinSettingsScriptModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    def "settings script has type-safe accessors on the classpath"() {
        given:
        withDefaultSettingsIn("included")
        withBuildScriptIn("included", """
            plugins { `kotlin-dsl` }
            $repositoriesBlock
        """)
        file("included/src/main/kotlin/SettingsExtension.kt") << """
            import org.gradle.api.provider.*
            interface SettingsExtension {
                val myProperty: Property<Int>
            }
        """
        file("included/src/main/kotlin/settings-plugin.settings.gradle.kts") << """
            extensions.create<SettingsExtension>("mySettingsExtension")
        """
        withSettings("""
            pluginManagement {
                includeBuild("included")
            }
            plugins {
                id("settings-plugin")
            }

            mySettingsExtension {
                myProperty = 42
            }

            println(mySettingsExtension.myProperty)
        """)

        when:
        def accessorsClassPath = accessorsClassPathFor(settingsFileKts)

        then:
        !accessorsClassPath.isEmpty()
    }

    private List<File> accessorsClassPathFor(File buildFile) {
        return classPathFor(projectDir, buildFile)
            .tap { println(it) }
            .findAll { isAccessorsDir(it) }
    }

    private static boolean isAccessorsDir(File dir) {
        return dir.isDirectory() && dir.path.contains("accessors")
    }
}
