/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.project

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class GetPropertiesRemovalIntegrationTest extends AbstractIntegrationSpec {

    private static final String REMOVAL_MESSAGE =
        "Accessing 'properties' on a Project or script is no longer supported."

    def "accessing 'properties' on a #scriptType fails with a helpful error"() {
        file(scriptFile) << "println(properties)"

        expect:
        fails("help")
        failure.assertHasCause(REMOVAL_MESSAGE)

        where:
        scriptType               | scriptFile
        "Groovy build script"    | "build.gradle"
        "Groovy settings script" | "settings.gradle"
    }

    def "accessing 'properties' on an init script fails with a helpful error"() {
        def initScript = file("init.gradle") << "println(properties)"

        when:
        fails("help", "-I", initScript.absolutePath)

        then:
        failure.assertHasCause(REMOVAL_MESSAGE)
    }

    def "'#expr' in a Groovy build script fails with a helpful error"() {
        buildFile << "println($expr)"

        expect:
        fails("help")
        failure.assertHasCause(REMOVAL_MESSAGE)

        where:
        expr << [
            "getProperties()",
            "project.properties",
            "project.getProperties()",
        ]
    }

    def "'#expr' on another project fails with a helpful error"() {
        settingsFile << "include 'a', 'b'"
        file("a/build.gradle") << "println($expr)"
        file("b/build.gradle") << ""

        expect:
        fails("help")
        failure.assertHasCause(REMOVAL_MESSAGE)

        where:
        expr << [
            "rootProject.properties",
            "rootProject.getProperties()",
            "project(':b').properties",
        ]
    }

    def "'#expr' in a subprojects block fails with a helpful error"() {
        createDirs("a")
        settingsFile << "include 'a'"
        buildFile << "subprojects { println($expr) }"

        expect:
        fails("help")
        failure.assertHasCause(REMOVAL_MESSAGE)

        where:
        expr << ["properties", "it.properties", "it.getProperties()"]
    }

    def "'project.#expr' in a Groovy plugin class fails with a helpful error"() {
        file("buildSrc/src/main/groovy/MyPlugin.groovy") << """
            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    println(project.$expr)
                }
            }
        """
        buildFile << "apply plugin: MyPlugin"

        expect:
        fails("help")
        failure.assertHasCause(REMOVAL_MESSAGE)

        where:
        expr << ["properties", "getProperties()"]
    }

    def "accessing 'properties' in a Kotlin build script fails at script compilation"() {
        file("build.gradle.kts") << "println(properties)"

        when:
        fails("help")

        then:
        failure.assertHasDescription("Script compilation error")
        errorOutput =~ /build\.gradle\.kts:1:9: .*properties/
    }
}
