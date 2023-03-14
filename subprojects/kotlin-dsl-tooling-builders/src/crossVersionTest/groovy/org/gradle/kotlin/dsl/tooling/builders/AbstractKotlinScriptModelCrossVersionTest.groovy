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

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.build.ProjectSourceRoots
import org.gradle.integtests.fixtures.build.TestProjectInitiation
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApiAdditionalClasspath
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.gradle.util.GradleVersion
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

import java.util.function.Consumer
import java.util.function.Predicate
import java.util.regex.Pattern

import static org.gradle.kotlin.dsl.resolver.KotlinBuildScriptModelRequestKt.fetchKotlinBuildScriptModelFor
import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.MatcherAssert.assertThat

@ToolingApiVersion(">=4.1")
@ToolingApiAdditionalClasspath(KotlinDslToolingModelsClasspathProvider)
@CompileStatic
abstract class AbstractKotlinScriptModelCrossVersionTest extends ToolingApiSpecification  implements TestProjectInitiation, KotlinScriptsModelFetcher {

    def setup() {
        // Required for the lenient classpath mode
        toolingApi.requireDaemons()
        // Only Kotlin settings scripts
        settingsFile.delete()
        file("settings.gradle.kts").touch()
        // Gradle 6.5.1 instrumented jar cache has concurrency issues causing flakiness
        // This is exacerbated by those cross-version tests running concurrently
        // This isolates the Gradle user home for this version only
        if (GradleVersion.version(releasedGradleVersion) == GradleVersion.version("6.5.1")) {
            toolingApi.requireIsolatedUserHome()
        }
    }

    private String targetKotlinVersion

    protected String getTargetKotlinVersion() {
        if (targetKotlinVersion == null) {
            targetKotlinVersion = loadTargetDistKotlinVersion()
        }
        return targetKotlinVersion
    }

    private String loadTargetDistKotlinVersion() {
        def props = new JarTestFixture(targetDist.gradleHomeDir.file("lib").listFiles().find {
            it.name.startsWith("gradle-kotlin-dsl-${targetVersion.baseVersion.version}")
        }).content("gradle-kotlin-dsl-versions.properties")
        return new Properties().tap { load(new StringReader(props)) }.getProperty("kotlin")
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
        return fetchKotlinBuildScriptModelFor(
            projectDir,
            scriptFile,
            { selectedProjectDir -> connector().forProjectDirectory(selectedProjectDir) }
        )
    }

    protected static List<File> canonicalClasspathOf(KotlinBuildScriptModel model) {
        return model.classPath.collect { it.canonicalFile }
    }

    protected static void assertClassPathContains(List<File> classPath, File... expectedFiles) {
        assertThat(
            classPath.collect { it.name } as List<String>,
            hasItems(fileNameSetOf(expectedFiles))
        )
    }

    protected static void assertIncludes(List<File> classPath, File... files) {
        assertThat(
            classPath.collect { it.name } as List<String>,
            hasItems(fileNameSetOf(files))
        )
    }

    protected static void assertExcludes(List<File> classPath, File... files) {
        assertThat(
            classPath.collect { it.name } as List<String>,
            not(hasItems(fileNameSetOf(files)))
        )
    }

    private static String[] fileNameSetOf(File... files) {
        return files.collect { it.name }.toSet() as String[]
    }

    protected static void assertContainsGradleKotlinDslJars(List<File> classPath) {
        def version = "[0-9.]+(-.+?)?"
        assertThat(
            classPath.collect { it.name } as List<String>,
            hasItems(
                matching("gradle-kotlin-dsl-$version\\.jar"),
                matching("gradle-api-$version\\.jar"),
                matching("gradle-kotlin-dsl-extensions-$version\\.jar")
            )
        )
    }

    protected void assertClassPathFor(
        File buildScript,
        Set<? extends File> includes,
        Set<? extends File> excludes,
        File importedProjectDir = projectDir
    ) {
        def includeItems = hasItems(includes.collect { it.name } as String[])
        def excludeItems = not(hasItems(excludes.collect { it.name } as String[]))
        def condition = excludes.isEmpty() ? includeItems : allOf(includeItems, excludeItems)
        assertThat(
            classPathFor(importedProjectDir, buildScript).collect { it.name },
            condition
        )
    }

    protected static void assertContainsBuildSrc(List<File> classPath) {
        assertThat(
            classPath.collect { it.name } as List<String>,
            hasBuildSrc()
        )
    }

    protected static void assertNotContainsBuildSrc(List<File> classPath) {
        assertThat(
            classPath.collect { it.name } as List<String>,
            not(hasBuildSrc())
        )
    }

    protected static Matcher<Iterable<? super String>> hasBuildSrc() {
        hasItem("buildSrc.jar")
    }

    protected static Matcher<Iterable<? super File>> matchesProjectsSourceRoots(ProjectSourceRoots... projectSourceRoots) {
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
                    } as Collection<Matcher<Iterable<? super File>>>
                } as Collection<Matcher<Iterable<? super File>>>

            def resourceDirs =
                sourceRoots.sourceSets.collect { sourceSet ->
                    hasLanguageDir(sourceRoots.projectDir, sourceSet, "resources")
                } as Collection<Matcher<Iterable<? super File>>>

            languageDirs + resourceDirs
        })
    }

    private static Matcher<Iterable<? super File>> hasLanguageDir(File base, String set, String lang) {
        return hasItem(new File(base, "src/$set/$lang"))
    }

    protected static Matcher<? super String> matching(String pattern) {
        def compiledPattern = Pattern.compile(pattern)
        return matching({ it.appendText("a string matching the pattern").appendValue(pattern) }, { String item ->
            compiledPattern.matcher(item).matches()
        } as Predicate<String>)
    }

    protected static <T> Matcher<T> matching(Consumer<Description> describe, Predicate<T> match) {
        return new TypeSafeMatcher<T>() {
            @Override
            protected boolean matchesSafely(T item) {
                return match.test(item)
            }

            @Override
            void describeTo(Description description) {
                describe.accept(description)
            }
        }
    }

    protected static String normalizedPathOf(File file) {
        return TextUtil.normaliseFileSeparators(file.path)
    }

    protected static List<File> canonicalClasspathOf(KotlinDslScriptsModel model, File script) {
        return model.scriptModels[script].classPath.collect { it.canonicalFile }
    }

    protected static void assertHasExceptionMessage(KotlinDslScriptsModel model, TestFile script, String message) {
        assertThat(model.scriptModels[script].exceptions, hasItem(containsString(message)))
    }
}
