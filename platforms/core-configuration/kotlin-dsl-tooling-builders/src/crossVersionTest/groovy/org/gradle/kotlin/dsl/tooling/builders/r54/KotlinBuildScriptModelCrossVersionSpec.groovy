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
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.Flaky
import org.hamcrest.Matcher

import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.Assert.assertTrue

@TargetGradleVersion(">=5.4")
@Flaky(because = 'https://github.com/gradle/gradle-private/issues/3414')
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
        withEmptyJar("classes.jar")
        withDefaultSettings()
        withBuildScript("""
            buildscript {
                dependencies {
                    classpath(files("classes.jar"))
                }
            }

            val p =
        """)

        expect:
        assertClassPathContains(
            canonicalClassPath(),
            file("classes.jar")
        )
    }

    @LeaksFileHandles("Kotlin compiler daemon on buildSrc jar")
    def "can fetch classpath in face of buildSrc test failures"() {

        given:
        withKotlinBuildSrc()
        withFile("buildSrc/build.gradle.kts").tap { buildSrcScript ->
            buildSrcScript.text = buildSrcScript.text + """
                dependencies {
                    testImplementation("junit:junit:4.13")
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

        withEmptyJar("classes.jar")

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
            hasItems("classes.jar")
        )

        assertContainsBuildSrc(classPath)

        assertContainsGradleKotlinDslJars(classPath)
    }

    def "can fetch buildscript classpath for sub-project script when parent has errors"() {

        given:
        withDefaultSettings().append("""
            include("sub")
        """)

        withBuildScript("val p =")

        def jar = withEmptyJar("libs/jar.jar")

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

        withDefaultSettingsIn(location).append("""
            include("foo", "bar")
        """)

        def parentJar = withEmptyJar("$location/libs/parent.jar")
        def fooJar = withEmptyJar("$location/libs/foo.jar")
        def barJar = withEmptyJar("$location/libs/bar.jar")

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
        def rootDependency = withEmptyJar("libs/root.jar")
        def subDependency = withEmptyJar("libs/sub.jar")

        and:
        withDefaultSettingsIn("root").append("""
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

        def rootDependency = withEmptyJar("libs/root-dep.jar")
        def nestedRootDependency = withEmptyJar("libs/$nestedProjectName-root-dep.jar")
        def nestedSubDependency = withEmptyJar("libs/$nestedProjectName-sub-dep.jar")

        withDefaultSettingsIn(nestedProjectName).append("""
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
            withEmptyJar("buildSrc-dependency.jar")

        withFile("buildSrc/build.gradle", """
            dependencies { implementation(files("../${buildSrcDependency.name}")) }
        """)

        def rootProjectDependency = withEmptyJar("rootProject-dependency.jar")

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
            withEmptyJar("script-plugin-dependency.jar")

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
            equalTo([])
        )

        and:
        def scriptPluginClassPath = canonicalClasspathOf(model)
        assertThat(
            scriptPluginClassPath.collect { it.name } as List<String>,
            hasItem(scriptPluginDependency.name)
        )

        assertContainsGradleKotlinDslJars(scriptPluginClassPath)
    }

    def "sourcePath includes Gradle sources"() {

        expect:
        assertSourcePathIncludesGradleSourcesGiven("", "")
    }

    def "sourcePath includes kotlin-stdlib sources resolved against project"() {

        expect:
        assertSourcePathIncludesKotlinStdlibSourcesGiven(
            "",
            "buildscript { $repositoriesBlock }"
        )
    }

    def "sourcePath includes kotlin-stdlib sources resolved against project hierarchy"() {

        expect:
        assertSourcePathIncludesKotlinStdlibSourcesGiven(
            "buildscript { $repositoriesBlock }",
            ""
        )
    }

    def "sourcePath includes buildscript classpath sources resolved against project"() {

        expect:
        assertSourcePathIncludesKotlinPluginSourcesGiven(
            "",
            """
                buildscript {
                    dependencies { classpath(embeddedKotlin("gradle-plugin")) }
                    $repositoriesBlock
                }
            """
        )
    }

    def "sourcePath includes buildscript classpath sources resolved against project hierarchy"() {

        expect:
        assertSourcePathIncludesKotlinPluginSourcesGiven(
            """
                buildscript {
                    dependencies { classpath(embeddedKotlin("gradle-plugin")) }
                    $repositoriesBlock
                }
            """,
            ""
        )
    }

    def "sourcePath includes plugins classpath sources resolved against project"() {

        expect:
        assertSourcePathIncludesKotlinPluginSourcesGiven(
            "",
            """ plugins { kotlin("jvm") version "$targetKotlinVersion" } """
        )
    }

    @LeaksFileHandles("Kotlin compiler daemon on buildSrc jar")
    def "sourcePath includes buildSrc source roots"() {

        given:
        withKotlinBuildSrc()
        withDefaultSettings().append("""
            include(":sub")
        """)

        expect:
        assertThat(
            sourcePathFor(withFile("sub/build.gradle.kts")),
            matchesProjectsSourceRoots(withMainSourceSetJavaKotlinIn("buildSrc"))
        )
    }

    @LeaksFileHandles("Kotlin compiler daemon on buildSrc jar")
    def "sourcePath includes buildSrc project dependencies source roots"() {

        given:
        def sourceRoots = withMultiProjectKotlinBuildSrc()
        withDefaultSettings().append("""
            include(":sub")
        """)

        expect:
        assertThat(
            sourcePathFor(withFile("sub/build.gradle.kts")),
            matchesProjectsSourceRoots(sourceRoots)
        )
    }

    private void assertSourcePathIncludesGradleSourcesGiven(String rootProjectScript, String subProjectScript) {
        assertSourcePathGiven(
            rootProjectScript,
            subProjectScript,
            hasItems("core-api")
        )
    }

    private void assertSourcePathIncludesKotlinStdlibSourcesGiven(String rootProjectScript, String subProjectScript) {
        assertSourcePathGiven(
            rootProjectScript,
            subProjectScript,
            hasItems("kotlin-stdlib-${targetKotlinVersion}-sources.jar".toString())
        )
    }

    private void assertSourcePathIncludesKotlinPluginSourcesGiven(String rootProjectScript, String subProjectScript) {
        assertSourcePathGiven(
            rootProjectScript,
            subProjectScript,
            hasItems(
                equalTo("kotlin-gradle-plugin-${targetKotlinVersion}-sources.jar".toString())
            )
        )
    }

    private void assertSourcePathGiven(
        String rootProjectScript,
        String subProjectScript,
        Matcher<Iterable<String>> matches
    ) {

        def subProjectName = "sub"
        withDefaultSettings().append("""
            include("$subProjectName")
        """)

        withBuildScript(rootProjectScript)
        def subProjectScriptFile = withBuildScriptIn(subProjectName, subProjectScript)

        def srcConventionalPathDirNames = ["java", "groovy", "kotlin", "resources"]
        def sourcePath = sourcePathFor(subProjectScriptFile).collect { path ->
            if (srcConventionalPathDirNames.contains(path.name)) {
                path.parentFile.parentFile.parentFile.name
            } else {
                path.name
            }
        }.unique()
        assertThat(sourcePath, matches)
    }

    private static String buildScriptDependencyOn(File jar) {
        return """
            buildscript {
                dependencies { classpath(files("${normalizedPathOf(jar)}")) }
            }
        """
    }
}
