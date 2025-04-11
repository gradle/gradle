/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.invocation

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class GradleRootProjectIntegrationTest extends AbstractIntegrationSpec {

    def 'settings plugin from included build can apply plugin by id to the root project'() {
        file("build-logic/src/main/kotlin/foo.gradle.kts") << """
            println("Foo applied")
        """

        file("build-logic/src/main/kotlin/bar.settings.gradle.kts") << """
            gradle.rootProject {
                plugins.apply("foo")
            }
        """

        file("build-logic/build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
            }
            repositories {
               mavenCentral()
            }
        """

        settingsFile """
            pluginManagement {
                includeBuild("build-logic")
            }
            plugins {
                id("bar")
            }
        """

        when:
        run "help"

        then:
        outputContains "Foo applied"
    }

    def "plugin from buildSrc can be applied by id to the root project"() {
        file("buildSrc/src/main/kotlin/foo.gradle.kts") << """
            println("Foo applied")
        """
        file("buildSrc/build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
            }
            repositories {
               mavenCentral()
            }
        """

        settingsFile """
            gradle.rootProject {
                plugins.apply("foo")
            }
        """

        when:
        run "help"

        then:
        outputContains "Foo applied"
    }
}
