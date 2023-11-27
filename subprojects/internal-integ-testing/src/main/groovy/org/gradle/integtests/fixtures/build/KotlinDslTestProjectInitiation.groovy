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

package org.gradle.integtests.fixtures.build

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.file.TestFile

import java.util.zip.ZipOutputStream

import static org.gradle.util.internal.TextUtil.escapeString

@CompileStatic
trait KotlinDslTestProjectInitiation {

    abstract TestFile file(Object... path)

    String defaultSettingsScript = ""

    String repositoriesBlock = """
        repositories {
            ${RepoScriptBlockUtil.gradlePluginRepositoryDefinition(GradleDsl.KOTLIN)}
        }
    """.stripIndent()

    BuildSpec withMultiProjectBuildWithBuildSrc() {
        withBuildSrc()
        def someJar = withEmptyJar("classes_some.jar")
        def settingsJar = withEmptyJar("classes_settings.jar")
        def rootJar = withEmptyJar("classes_root.jar")
        def aJar = withEmptyJar("classes_a.jar")
        def bJar = withEmptyJar("classes_b.jar")
        def precompiledJar = withEmptyJar("classes_b_precompiled.jar")

        def some = withFile("some.gradle.kts", getBuildScriptDependency(someJar))
        def settings = withSettings("""${getBuildScriptDependency(settingsJar)}
            apply(from = "some.gradle.kts")
            include("a", "b")
        """)
        def root = withBuildScript("""
            ${getBuildScriptDependency(rootJar)}
            apply(from = "some.gradle.kts")
        """)
        def a = withBuildScriptIn("a", """
            ${getBuildScriptDependency(aJar)}
            apply(from = "../some.gradle.kts")
        """)
        def b = withBuildScriptIn("b", """
            plugins {
                `kotlin-dsl`
            }
            ${getBuildScriptDependency(bJar)}
            apply(from = "../some.gradle.kts")

            $repositoriesBlock

            dependencies {
                implementation(files("${escapeString(precompiledJar)}"))
            }
        """)
        def precompiled = withFile("b/src/main/kotlin/precompiled/precompiled.gradle.kts", "")
        return new BuildSpec(
            scripts: [
                settings: settings,
                root: root,
                a: a,
                b: b,
                precompiled: precompiled
            ],
            appliedScripts: [
                some: some
            ],
            jars: [
                some: someJar,
                settings: settingsJar,
                root: rootJar,
                a: aJar,
                b: bJar,
                precompiled: precompiledJar
            ]
        )
    }

    BuildSpec withSingleSubproject() {
        def settings = withSettings("""
            include("a")
        """)
        def a = withBuildScriptIn("a", """
        """)
        return new BuildSpec(
            scripts: [
                settings: settings,
                a: a
            ]
        )
    }

    /**
     * Initializes a new project with <i>Kotlin-based</i> build scripts.
     */
    BuildSpec withMultipleSubprojects() {
        def settings = withSettings("""
            include("a", "b")
        """)
        def a = withBuildScriptIn("a", """
        """)
        def b = withBuildScriptIn("b", """
        """)
        return new BuildSpec(
            scripts: [
                settings: settings,
                a: a,
                b: b
            ]
        )
    }

    String getBuildScriptDependency(someJar) {
        """
            buildscript {
                dependencies {
                    classpath(files("${escapeString(someJar)}"))
                }
            }
        """
    }

    TestFile withDefaultSettings() {
        return withSettings(defaultSettingsScript)
    }

    TestFile withSettings(String script) {
        return withSettingsIn(".", script)
    }

    TestFile withDefaultSettingsIn(String baseDir) {
        return withSettingsIn(baseDir, defaultSettingsScript)
    }

    TestFile withSettingsIn(String baseDir, String script) {
        return withFile("$baseDir/$settingsKotlinFileName", script)
    }

    TestFile withSettingsGroovy(String script) {
        return withSettingsGroovyIn(".", script)
    }

    TestFile withDefaultSettingsGroovyIn(String baseDir) {
        return withSettingsGroovyIn(baseDir, defaultSettingsScript)
    }

    TestFile withSettingsGroovyIn(String baseDir, String script) {
        return withFile("$baseDir/$settingsFileName", script)
    }

    TestFile withBuildScript(String script = "") {
        return withBuildScriptIn(".", script)
    }

    TestFile withBuildScriptIn(String baseDir, String script = "") {
        return withFile("$baseDir/$defaultBuildKotlinFileName", script)
    }

    TestFile withFile(String path, String content = "") {
        return file(path).tap { text = content.stripIndent() }
    }

    TestFile withEmptyJar(String path) {
        return this.file(path).tap { jarFile ->
            jarFile.parentFile.mkdirs()
            new ZipOutputStream(jarFile.newOutputStream()).close()
        }
    }

    void withBuildSrc() {
        this.withFile("buildSrc/src/main/groovy/build/Foo.groovy", """
            package build
            class Foo {}
        """)
    }

    def withKotlinBuildSrc() {
        withDefaultSettingsIn("buildSrc")
        withBuildScriptIn("buildSrc", """
            plugins {
                `kotlin-dsl`
            }

            $repositoriesBlock
        """)
    }

    ProjectSourceRoots[] withMultiProjectKotlinBuildSrc() {
        withDefaultSettingsIn("buildSrc").append("""
            include(":a", ":b", ":c")
        """)

        withBuildScriptIn("buildSrc", """
            plugins {
                java
                `kotlin-dsl` apply false
            }

            dependencies {
                "runtimeOnly"(project(":a"))
                "runtimeOnly"(project(":b"))
            }
        """)

        // Duplication in build scripts to avoid Isolated Projects violations without introducing shared build logic
        withBuildScriptIn("buildSrc/a", """
            plugins {
                id("org.gradle.kotlin.kotlin-dsl")
            }
            $repositoriesBlock
        """)
        withBuildScriptIn("buildSrc/b", """
            plugins {
                id("org.gradle.kotlin.kotlin-dsl")
            }
            $repositoriesBlock
            dependencies { implementation(project(":c")) }
        """)
        withBuildScriptIn("buildSrc/c", """
            plugins { java }
            $repositoriesBlock
        """)

        return [
            withMainSourceSetJavaIn("buildSrc"),
            withMainSourceSetJavaKotlinIn("buildSrc/a"),
            withMainSourceSetJavaKotlinIn("buildSrc/b"),
            withMainSourceSetJavaIn("buildSrc/c")
        ] as ProjectSourceRoots[]
    }

    ProjectSourceRoots withMainSourceSetJavaIn(String projectDir) {
        return new ProjectSourceRoots(file(projectDir), ["main"], ["java"])
    }

    ProjectSourceRoots withMainSourceSetJavaKotlinIn(String projectDir) {
        return new ProjectSourceRoots(file(projectDir), ["main"], ["java", "kotlin"])
    }

    TestFile getBuildFile() {
        file(defaultBuildFileName)
    }

    TestFile getPropertiesFile() {
        file("gradle.properties")
    }

    TestFile getBuildFileKts() {
        file(defaultBuildKotlinFileName)
    }

    TestFile getBuildKotlinFile() {
        getBuildFileKts()
    }

    TestFile getSettingsKotlinFile() {
        getSettingsFileKts()
    }

    TestFile getSettingsFile() {
        file(settingsFileName)
    }

    TestFile getSettingsFileKts() {
        file(settingsKotlinFileName)
    }

    String getSettingsFileName() {
        'settings.gradle'
    }

    String getSettingsKotlinFileName() {
        'settings.gradle.kts'
    }

    String getDefaultBuildFileName() {
        'build.gradle'
    }

    String getDefaultBuildKotlinFileName() {
        "build.gradle.kts"
    }

    List<TestFile> createDirs(String... dirs) {
        dirs.collect({ name ->
            TestFile tf = file(name)
            tf.mkdirs()
            return tf
        })
    }
}
