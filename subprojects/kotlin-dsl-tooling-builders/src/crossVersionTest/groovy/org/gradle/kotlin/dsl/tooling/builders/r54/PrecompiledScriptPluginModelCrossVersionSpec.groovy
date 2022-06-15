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

package org.gradle.kotlin.dsl.tooling.builders.r54

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.Flaky

import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.CoreMatchers.startsWith
import static org.hamcrest.MatcherAssert.assertThat

@TargetGradleVersion(">=5.4")
@Flaky(because = 'https://github.com/gradle/gradle-private/issues/3414')
class PrecompiledScriptPluginModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    @LeaksFileHandles("Kotlin Compiler Daemon working directory")
    def "given a single project build, the classpath of a precompiled script plugin is the compile classpath of its enclosing source-set"() {

        given:
        def implementationDependency = withEmptyJar("implementation.jar")
        def classpathDependency = withEmptyJar("classpath.jar")

        and:
        withDefaultSettings()
        withBuildScript("""
            plugins {
                `kotlin-dsl`
            }

            buildscript {
                dependencies {
                    classpath(files("${classpathDependency.name}"))
                }
            }

            $repositoriesBlock

            dependencies {
                implementation(files("${implementationDependency.name}"))
            }
        """)

        and:
        def precompiledScriptPlugin = withPrecompiledKotlinScript("my-plugin.gradle.kts", "")

        expect:
        assertClassPathFor(
            precompiledScriptPlugin,
            [implementationDependency] as Set,
            [classpathDependency] as Set
        )
    }

    def "given a multi-project build, the classpath of a precompiled script plugin is the compile classpath of its enclosing source-set"() {

        given:
        def dependencyA = withEmptyJar("a.jar")
        def dependencyB = withEmptyJar("b.jar")

        and:
        withDefaultSettings().append("""
            include("project-a")
            include("project-b")
        """.stripIndent())

        and:
        withImplementationDependencyOn("project-a", dependencyA)
        withFile("project-a/src/main/kotlin/my-plugin-a.gradle.kts")

        and:
        withImplementationDependencyOn("project-b", dependencyB)
        withFile("project-b/src/main/kotlin/my-plugin-b.gradle.kts")

        expect:
        assertClassPathFor(
            file("project-a/src/main/kotlin/my-plugin-a.gradle.kts"),
            [dependencyA] as Set,
            [dependencyB] as Set
        )

        and:
        assertClassPathFor(
            file("project-b/src/main/kotlin/my-plugin-b.gradle.kts"),
            [dependencyB] as Set,
            [dependencyA] as Set
        )
    }

    def "implicit imports include type-safe accessors packages"() {

        given:
        withDefaultSettings()
        withBuildScript("""
            plugins {
                `kotlin-dsl`
            }

            $repositoriesBlock
        """)

        and:
        def pluginFile = withPrecompiledKotlinScript("plugin.gradle.kts", """
            plugins { java }
        """)

        expect:
        assertThat(
            kotlinBuildScriptModelFor(projectDir, pluginFile).implicitImports,
            hasItems(
                startsWith("gradle.kotlin.dsl.accessors._"),
                startsWith("gradle.kotlin.dsl.plugins._")
            )
        )
    }

    private TestFile withPrecompiledKotlinScript(String fileName, String code) {
        return withFile("src/main/kotlin/$fileName", code)
    }

    private TestFile withImplementationDependencyOn(String basedir, TestFile jar) {
        return withBuildScriptIn(basedir, """
            plugins {
                `kotlin-dsl`
            }

            $repositoriesBlock

            dependencies {
                implementation(files("${jar.name}"))
            }
        """)
    }
}
