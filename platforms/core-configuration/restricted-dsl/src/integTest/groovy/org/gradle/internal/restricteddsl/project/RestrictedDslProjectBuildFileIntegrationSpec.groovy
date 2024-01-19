/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.restricteddsl.project


import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class RestrictedDslProjectBuildFileIntegrationSpec extends AbstractIntegrationSpec {

    def 'can apply plugins in restricted DSL'() {
        file("build.gradle.something") << """
            plugins {
                id("java")
            }
        """

        when:
        run("compileJava")

        then:
        succeeds()
    }

    def 'can declare dependencies on other projects with #kind notation'() {
        given:
        file("settings.gradle.something") << """
            rootProject.name = "test"
            include(":a")
            include(":b")

            $settingsFlags
        """

        file("a/build.gradle.something") << """
            plugins {
                id("java")
            }
        """

        file("b/build.gradle.something") << """
            plugins {
                id("java")
            }
            dependencies {
                implementation($dependency)
            }
        """

        when:
        run(":b:compileJava")

        then:
        executed(":a:compileJava", ":b:compileJava")

        where:
        kind        | dependency        | settingsFlags
        "string"    | "project(\":a\")" | ""
        "type-safe" | "projects.a"      | "enableFeaturePreview(\"TYPESAFE_PROJECT_ACCESSORS\")\n"
    }

    def 'schema is written during project files interpretation with restricted DSL'() {
        given:
        settingsFile("""
            include(":sub")
        """)

        file("build.gradle.something") << """
        plugins {
        }
        """

        file("sub/build.gradle.something") << """
        plugins {
        }
        """

        when:
        run(":help")

        then:
        [
            file(".gradle/restricted-schema/plugins.something.schema"),
            file(".gradle/restricted-schema/project.something.schema"),
            file("sub/.gradle/restricted-schema/plugins.something.schema"),
            file("sub/.gradle/restricted-schema/project.something.schema")
        ].every { it.isFile() && it.text != "" }
    }

    def 'reports #kind errors in project file #part'() {
        given:
        file("build.gradle.something") << """
        plugins {
            id("java")
            $pluginsCode
        }

        $bodyCode
        """

        when:
        def failure = fails(":help")

        then:
        failure.assertHasErrorOutput(expectedMessage)

        where:
        kind               | part          | pluginsCode              | bodyCode              | expectedMessage
        "syntax"           | "plugins DSL" | "..."                    | ""                    | "3:13: parsing error: Expecting an element"
        "language feature" | "plugins DSL" | "@A id(\"application\")" | ""                    | "3:13: unsupported language feature: AnnotationUsage"
        "semantic"         | "plugins DSL" | "x = 1"                  | ""                    | "3:13: unresolved reference 'x'"
        "syntax"           | "body"        | ""                       | "..."                 | "4:9: parsing error: Expecting an element"
        "language feature" | "body"        | ""                       | "@A dependencies { }" | "4:9: unsupported language feature: AnnotationUsage"
        "semantic"         | "body"        | ""                       | "x = 1"               | "4:9: unresolved reference 'x'"
    }
}
