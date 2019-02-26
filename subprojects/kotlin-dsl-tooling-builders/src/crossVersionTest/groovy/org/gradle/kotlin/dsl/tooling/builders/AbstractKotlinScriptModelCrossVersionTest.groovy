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

import org.gradle.integtests.tooling.fixture.ToolingApiAdditionalClasspath
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.file.TestFile

import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel

import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

import java.util.regex.Pattern

import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.CoreMatchers.hasItems
import static org.junit.Assert.assertThat


@ToolingApiAdditionalClasspath(KotlinDslToolingModelsClasspathProvider)
abstract class AbstractKotlinScriptModelCrossVersionTest extends ToolingApiSpecification {

    def setup() {
        // Required for the lenient classpath mode
        toolingApi.requireDaemons()
    }

    protected TestFile withDefaultSettings() {
        return file("settings.gradle.kts") << ""
    }

    protected TestFile withFile(String path, String text = "") {
        return file(path) << text
    }

    protected void withBuildSrc() {
        projectDir.createFile("buildSrc/src/main/groovy/build/Foo.groovy") << """
            package build
            class Foo {}
        """.stripIndent()
    }

    protected List<File> canonicalClassPathFor(File projectDir, File scriptFile = null) {
        return canonicalClasspathOf(kotlinBuildScriptModelFor(projectDir, scriptFile))
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

    protected static Matcher<Iterable<? super String>> hasBuildSrc() {
        hasItem("buildSrc.jar")
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
}
