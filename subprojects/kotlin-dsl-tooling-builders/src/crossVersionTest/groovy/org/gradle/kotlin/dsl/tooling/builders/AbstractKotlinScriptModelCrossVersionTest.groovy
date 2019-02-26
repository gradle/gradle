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

package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApiAdditionalClasspath
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.file.TestFile

import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel

import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

import java.util.regex.Pattern

import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.CoreMatchers.not
import static org.junit.Assert.assertThat


class ProjectSourceRoots {

    final File projectDir
    final List<String> sourceSets
    final List<String> languages

    ProjectSourceRoots(File projectDir, List<String> sourceSets, List<String> languages) {
        this.projectDir = projectDir
        this.sourceSets = sourceSets
        this.languages = languages
    }
}


@ToolingApiAdditionalClasspath(KotlinDslToolingModelsClasspathProvider)
abstract class AbstractKotlinScriptModelCrossVersionTest extends ToolingApiSpecification {

    def setup() {
        // Required for the lenient classpath mode
        toolingApi.requireDaemons()
    }

    private String defaultSettingsScript = ""

    private String repositoriesBlock = """
        repositories {
            gradlePluginPortal()
        }
    """.stripIndent()

    protected TestFile withDefaultSettings() {
        return withSettings(defaultSettingsScript)
    }

    protected TestFile withSettings(String script) {
        return withSettingsIn(".", script)
    }

    protected TestFile withDefaultSettingsIn(String baseDir) {
        return withSettingsIn(baseDir, defaultSettingsScript)
    }

    protected TestFile withSettingsIn(String baseDir, String script) {
        return withFile("$baseDir/settings.gradle.kts", script)
    }

    protected TestFile withBuildScriptIn(String baseDir, String script = "") {
        return withFile("$baseDir/build.gradle.kts", script)
    }

    protected TestFile withFile(String path, String text = "") {
        return file(path) << text.stripIndent()
    }

    protected void withBuildSrc() {
        projectDir.createFile("buildSrc/src/main/groovy/build/Foo.groovy") << """
            package build
            class Foo {}
        """.stripIndent()
    }

    protected void withKotlinBuildSrc() {
        withDefaultSettingsIn("buildSrc")
        withBuildScriptIn("buildSrc", """
            plugins {
                `kotlin-dsl`
            }

            $repositoriesBlock
        """)
    }

    protected ProjectSourceRoots[] withMultiProjectKotlinBuildSrc() {
        withSettingsIn("buildSrc", """
            include(":a", ":b", ":c")
        """)
        withFile("buildSrc/build.gradle.kts", """
            plugins {
                java
                `kotlin-dsl` apply false
            }

            val kotlinDslProjects = listOf(project.project(":a"), project.project(":b"))

            kotlinDslProjects.forEach {
                it.apply(plugin = "org.gradle.kotlin.kotlin-dsl")
            }

            dependencies {
                kotlinDslProjects.forEach {
                    "runtime"(project(it.path))
                }
            }
        """)
        withFile("buildSrc/b/build.gradle.kts", """dependencies { implementation(project(":c")) }""")
        withFile("buildSrc/c/build.gradle.kts", "plugins { java }")

        return [
            withMainSourceSetJavaIn("buildSrc"),
            withMainSourceSetJavaKotlinIn("buildSrc/a"),
            withMainSourceSetJavaKotlinIn("buildSrc/b"),
            withMainSourceSetJavaIn("buildSrc/c")
        ]
    }

    protected List<File> canonicalClassPath() {
        return canonicalClassPathFor(projectDir)
    }

    protected List<File> canonicalClassPathFor(File projectDir, File scriptFile = null) {
        return canonicalClasspathOf(kotlinBuildScriptModelFor(projectDir, scriptFile))
    }

    protected List<File> classPathFor(File projectDir, File scriptFile = null) {
        return kotlinBuildScriptModelFor(projectDir, scriptFile).classPath
    }

    protected List<File> sourcePathFor(File scriptFile) {
        kotlinBuildScriptModelFor(projectDir, scriptFile).sourcePath
    }

    protected KotlinBuildScriptModel kotlinBuildScriptModelFor(File projectDir, File scriptFile = null) {
        withConnector {
            it.forProjectDirectory(projectDir)
        }
        return withConnection {
            model(KotlinBuildScriptModel).tap {
                setJvmArguments("-Dorg.gradle.kotlin.dsl.provider.mode=classpath")
                if (scriptFile != null) {
                    withArguments("-Porg.gradle.kotlin.dsl.provider.script=${scriptFile.canonicalPath}")
                }
            }.get()
        }
    }

    private static List<File> canonicalClasspathOf(KotlinBuildScriptModel model) {
        return model.classPath.collect { it.canonicalFile }
    }

    protected static void assertClassPathContains(List<File> classPath, File... expectedFiles) {
        assertThat(
            classPath.collect { it.name },
            hasItems(fileNameSetOf(expectedFiles))
        )
    }

    protected void assertClassPathContains(File... expectedFiles) {
        assertThat(
            canonicalClassPath().collect { it.name } as List<String>,
            hasItems(fileNameSetOf(expectedFiles))
        )
    }

    protected void assertIncludes(List<File> classPath, File... files) {
        assertThat(
            classPath.collect { it.name } as List<String>,
            hasItems(fileNameSetOf(*files)))
    }

    protected void assertExcludes(List<File> classPath, File... files) {
        assertThat(
            classPath.collect { it.name } as List<String>,
            not(hasItems(*fileNameSetOf(*files))))
    }

    private static String[] fileNameSetOf(File... files) {
        return files.collect { it.name }.toSet()
    }

    protected static void assertContainsGradleKotlinDslJars(List<File> classPath) {
        def version = "[0-9.]+(-.+?)?"
        assertThat(
            classPath.collect { it.name } as List<String>,
            hasItems(
                matching("gradle-kotlin-dsl-$version\\.jar"),
                matching("gradle-api-$version\\.jar"),
                matching("gradle-kotlin-dsl-extensions-$version\\.jar")))
    }

    protected void assertContainsBuildSrc(List<File> classPath) {
        assertThat(
            classPath.collect { it.name } as List<String>,
            hasBuildSrc())
    }

    protected static Matcher<Iterable<? super String>> hasBuildSrc() {
        hasItem("buildSrc.jar")
    }

    protected ProjectSourceRoots withMainSourceSetJavaIn(String projectDir) {
        return new ProjectSourceRoots(file(projectDir), ["main"], ["java"])
    }

    protected ProjectSourceRoots withMainSourceSetJavaKotlinIn(String projectDir) {
        return new ProjectSourceRoots(file(projectDir), ["main"], ["java", "kotlin"])
    }

    protected static Matcher<Iterable<File>> matchesProjectsSourceRoots(ProjectSourceRoots... projectSourceRoots) {
        return allOf(projectSourceRoots.findAll { !it.languages.isEmpty() }.collectMany { sourceRoots ->

            def languageDirs =
                sourceRoots.sourceSets.collectMany { sourceSet ->
                    ["java", "kotlin"].collect { language ->
                        def hasLanguageDir = hasLanguageDir(sourceRoots.projectDir, sourceSet, language)
                        if (language in sourceRoots.languages) {
                            hasLanguageDir
                        } else {
                            not(hasLanguageDir)
                        }
                    }
                }

            def resourceDirs =
                sourceRoots.sourceSets.collect { sourceSet ->
                    hasLanguageDir(sourceRoots.projectDir, sourceSet, "resources")
                }

            languageDirs + resourceDirs
        })
    }

    protected static Matcher<Iterable<?>> hasLanguageDir(File base, String set, String lang) {
        return hasItem(new File(base, "src/$set/$lang"))
    }

    private static Matcher<String> matching(String pattern) {
        def compiledPattern = Pattern.compile(pattern)
        return new TypeSafeMatcher<String>() {

            @Override
            protected boolean matchesSafely(String item) {
                return compiledPattern.matcher(item).matches()
            }

            @Override
            void describeTo(Description description) {
                description.appendText("a string matching the pattern").appendValue(pattern)
            }
        }
    }

    protected static String normalizedPathOf(File file) {
        return TextUtil.normaliseFileSeparators(file.path)
    }
}
