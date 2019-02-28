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

import groovy.transform.CompileStatic

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.test.fixtures.file.LeaksFileHandles

import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest

import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.CoreMatchers.hasItems
import static org.junit.Assert.assertThat
import static org.junit.Assert.assertTrue


@TargetGradleVersion(">=5.3")
class KotlinBuildScriptModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    def "can fetch buildSrc classpath in face of compilation errors"() {

        given:
        withBuildSrc()

        and:
        withDefaultSettings()
        withBuildScript("""
            val p =
        """)

        expect:
        assertContainsBuildSrc(canonicalClassPath())
    }

    def "can fetch buildSrc classpath in face of buildscript exceptions"() {

        given:
        withBuildSrc()

        and:
        withDefaultSettings()
        withBuildScript("""
            buildscript { TODO() }
        """)

        expect:
        assertContainsBuildSrc(canonicalClassPath())
    }

    def "can fetch buildscript classpath in face of compilation errors"() {

        given:
        projectDir.createFile("classes.jar")
        settingsFile << ""
        buildFileKts << """
            buildscript {
                dependencies {
                    classpath(files("classes.jar"))
                }
            }

            val p =
        """.stripIndent()

        expect:
        assertClassPathContains(
            file("classes.jar"))
    }

    @LeaksFileHandles("Kotlin compiler daemon on buildSrc jar")
    def "can fetch classpath in face of buildSrc test failures"() {

        given:
        withKotlinBuildSrc()
        withFile("buildSrc/build.gradle.kts").tap { buildSrcScript ->
            buildSrcScript.text = buildSrcScript.text + """
                dependencies {
                    testImplementation("junit:junit:4.12")
                }
            """
        }
        withFile("buildSrc/src/test/kotlin/FailingTest.kt", """
            class FailingTest {
                @org.junit.Test fun test() {
                    throw Exception("BOOM")
                }
            }
        """)
        withDefaultSettings()

        expect:
        assertContainsBuildSrc(canonicalClassPath())
    }

    def "can fetch buildscript classpath of top level Groovy script"() {

        given:
        withBuildSrc()

        withFile("classes.jar")

        withDefaultSettings()
        withFile("build.gradle", """
            buildscript {
                dependencies {
                    classpath(files("classes.jar"))
                }
            }
        """)

        when:
        def classPath = canonicalClassPath()

        then:
        assertThat(
            classPath.collect { it.name } as List<String>,
            hasItems("classes.jar"))

        assertContainsBuildSrc(classPath)

        assertContainsGradleKotlinDslJars(classPath)
    }

    def "can fetch buildscript classpath for sub-project script when parent has errors"() {

        given:
        withSettings("""include("sub")""")

        withDefaultSettings()
        withBuildScript("val p =")

        def jar = withFile("libs/jar.jar")

        def subProjectScript =
            withFile("sub/build.gradle.kts", """
                buildscript {
                    dependencies { classpath(files("${normalizedPathOf(jar)}")) }
                }
            """)

        expect:
        assertClassPathFor(
            subProjectScript,
            [jar] as Set,
            [] as Set
        )
    }

    def "can fetch buildscript classpath for sub-project script"() {

        expect:
        assertCanFetchClassPathForSubProjectScriptIn(".")
    }

    def "can fetch buildscript classpath for sub-project script of nested project"() {

        given:
        withDefaultSettings()

        expect:
        assertCanFetchClassPathForSubProjectScriptIn("nested-project")
    }

    @CompileStatic
    private void assertCanFetchClassPathForSubProjectScriptIn(String location) {

        withSettingsIn(location, "include(\"foo\", \"bar\")")

        def parentJar = withClassJar("$location/libs/parent.jar")
        def fooJar = withClassJar("$location/libs/foo.jar")
        def barJar = withClassJar("$location/libs/bar.jar")

        assertTrue parentJar.isFile()
        assertTrue fooJar.isFile()
        assertTrue barJar.isFile()

        def parentBuildScript = withFile("$location/build.gradle.kts", buildScriptDependencyOn(parentJar))
        def fooBuildScript = withFile("$location/foo/build.gradle.kts", buildScriptDependencyOn(fooJar))
        def barBuildScript = withFile("$location/bar/build.gradle.kts", buildScriptDependencyOn(barJar))

        assertTrue parentBuildScript.text.contains(normalizedPathOf(parentJar))
        assertTrue fooBuildScript.text.contains(normalizedPathOf(fooJar))
        assertTrue barBuildScript.text.contains(normalizedPathOf(barJar))

        assertClassPathFor(
            parentBuildScript,
            [parentJar] as Set,
            [fooJar, barJar] as Set,
            projectDir
        )

        assertClassPathFor(
            fooBuildScript,
            [parentJar, fooJar] as Set,
            [barJar] as Set,
            projectDir
        )

        assertClassPathFor(
            barBuildScript,
            [parentJar, barJar] as Set,
            [fooJar] as Set,
            projectDir
        )
    }

    def "can fetch buildscript classpath for sub-project script outside root project dir"() {

        given:
        def rootDependency = withClassJar("libs/root.jar")
        def subDependency = withClassJar("libs/sub.jar")

        and:
        withSettingsIn("root", """
            include("sub")
            project(":sub").apply {
                projectDir = file("../sub")
                buildFileName = "sub.gradle.kts"
            }
        """)
        file("sub").mkdirs()

        and:
        def rootBuildScript = withFile("root/build.gradle", buildScriptDependencyOn(rootDependency))
        def subBuildScript = withFile("sub/sub.gradle.kts", buildScriptDependencyOn(subDependency))
        def rootProjectDir = rootBuildScript.parentFile

        expect:
        assertClassPathFor(
            rootBuildScript,
            [rootDependency] as Set,
            [subDependency] as Set,
            rootProjectDir
        )

        and:
        assertClassPathFor(
            subBuildScript,
            [rootDependency, subDependency] as Set,
            [] as Set,
            rootProjectDir
        )
    }

    def "can fetch buildscript classpath for buildSrc sub-project script outside buildSrc root"() {

        expect:
        assertCanFetchClassPathForSubProjectScriptOfNestedProjectOutsideProjectRoot("buildSrc")
    }

    def "can fetch buildscript classpath for sub-project script of nested project outside nested project root"() {

        when:
        // This use-case was never supported and continues not to be supported
        assertCanFetchClassPathForSubProjectScriptOfNestedProjectOutsideProjectRoot("nested-project")

        then:
        thrown(AssertionError)
    }

    private void assertCanFetchClassPathForSubProjectScriptOfNestedProjectOutsideProjectRoot(String nestedProjectName) {

        withDefaultSettings()

        def rootDependency = withClassJar("libs/root-dep.jar")
        def nestedRootDependency = withClassJar("libs/$nestedProjectName-root-dep.jar")
        def nestedSubDependency = withClassJar("libs/$nestedProjectName-sub-dep.jar")

        withSettingsIn(nestedProjectName, """
            include("sub")
            project(":sub").apply {
                projectDir = file("../$nestedProjectName-sub")
                buildFileName = "sub.gradle.kts"
            }
        """)
        file("$nestedProjectName-sub").mkdirs()

        def rootBuildScript = withFile("build.gradle", buildScriptDependencyOn(rootDependency))
        def nestedBuildScript = withFile("$nestedProjectName/build.gradle.kts", buildScriptDependencyOn(nestedRootDependency))
        def nestedSubBuildScript = withFile("$nestedProjectName-sub/sub.gradle.kts", buildScriptDependencyOn(nestedSubDependency))

        assertClassPathFor(
            rootBuildScript,
            [rootDependency] as Set,
            [nestedRootDependency, nestedSubDependency] as Set
        )

        assertClassPathFor(
            nestedBuildScript,
            [nestedRootDependency] as Set,
            [rootDependency, nestedSubDependency] as Set
        )

        assertClassPathFor(
            nestedSubBuildScript,
            [nestedRootDependency, nestedSubDependency] as Set,
            [rootDependency] as Set
        )
    }

    def "can fetch classpath of script plugin"() {

        expect:
        assertCanFetchClassPathOfScriptPlugin("")
    }

    def "can fetch classpath of script plugin with compilation errors"() {

        expect:
        assertCanFetchClassPathOfScriptPlugin("val p = ")
    }

    def "can fetch classpath of script plugin with buildscript block compilation errors"() {

        expect:
        assertCanFetchClassPathOfScriptPlugin("buildscript { val p = }")
    }

    private void assertCanFetchClassPathOfScriptPlugin(String scriptPluginCode) {

        withBuildSrc()

        def buildSrcDependency =
            withFile("buildSrc-dependency.jar")

        withFile("buildSrc/build.gradle", """
            dependencies { compile(files("../${buildSrcDependency.name}")) }
        """)

        def rootProjectDependency = withFile("rootProject-dependency.jar")

        withDefaultSettings()
        withFile("build.gradle", """
            buildscript {
                dependencies { classpath(files("${rootProjectDependency.name}")) }
            }
        """)

        def scriptPlugin = withFile("plugin.gradle.kts", scriptPluginCode)

        def scriptPluginClassPath = canonicalClassPathFor(projectDir, scriptPlugin)
        assertThat(
            scriptPluginClassPath.collect { it.name },
            allOf(
                not(hasItem(rootProjectDependency.name)),
                hasItem(buildSrcDependency.name)
            )
        )
        assertContainsBuildSrc(scriptPluginClassPath)
        assertContainsGradleKotlinDslJars(scriptPluginClassPath)
    }


    def "can fetch classpath of plugin portal plugin in plugins block"() {

        given:
        withDefaultSettings()
        withBuildScript("""
            plugins {
                id("org.gradle.hello-world") version "0.2"
            }
        """)

        expect:
        assertThat(
            canonicalClassPath().collect { it.name } as List<String>,
            hasItems("gradle-hello-world-plugin-0.2.jar")
        )
    }

    def "can fetch classpath of script plugin with buildscript block"() {

        given:
        withDefaultSettings()

        def scriptPluginDependency =
            withFile("script-plugin-dependency.jar")

        def scriptPlugin = withFile("plugin.gradle.kts", """
            buildscript {
                dependencies { classpath(files("${scriptPluginDependency.name}")) }
            }

            // Shouldn't be evaluated
            throw IllegalStateException()
        """)

        when:
        def model = kotlinBuildScriptModelFor(projectDir, scriptPlugin)

        then:
        assertThat(
            "Script body shouldn't be evaluated",
            model.exceptions,
            equalTo([]))

        and:
        def scriptPluginClassPath = canonicalClasspathOf(model)
        assertThat(
            scriptPluginClassPath.collect { it.name } as List<String>,
            hasItem(scriptPluginDependency.name))

        assertContainsGradleKotlinDslJars(scriptPluginClassPath)
    }

    private static String buildScriptDependencyOn(File jar) {
        return """
            buildscript {
                dependencies { classpath(files("${normalizedPathOf(jar)}")) }
            }
        """
    }
}
