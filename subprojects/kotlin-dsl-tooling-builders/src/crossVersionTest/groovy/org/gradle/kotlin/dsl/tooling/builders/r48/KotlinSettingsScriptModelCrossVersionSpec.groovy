/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.tooling.builders.r48

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest


@TargetGradleVersion(">=4.8")
class KotlinSettingsScriptModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    def "can fetch classpath of settings script plugin"() {

        given:
        withBuildSrc()
        withDefaultSettings()

        and:
        def settingsDependency = withFile("settings-dependency.jar", "")
        def settings = withFile("my.settings.gradle.kts", """
            buildscript {
                dependencies {
                    classpath(files("${normalizedPathOf(settingsDependency)}"))
                }
            }
        """)

        and:
        def projectDependency = withFile("project-dependency.jar", "")
        withFile("build.gradle", """
            buildscript {
                dependencies {
                    classpath(files("${normalizedPathOf(projectDependency)}"))
                }
            }
        """)

        when:
        def classPath = canonicalClassPathFor(projectDir, settings)

        then:
        assertContainsBuildSrc(classPath)
        assertContainsGradleKotlinDslJars(classPath)
        assertIncludes(classPath, settingsDependency)
        assertExcludes(classPath, projectDependency)
    }
}
