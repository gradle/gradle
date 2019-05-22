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

import org.hamcrest.Matcher

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.CoreMatchers.not
import static org.junit.Assert.assertThat


@TargetGradleVersion(">=5.4")
class AccessorsClassPathModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    def "classpath model includes jit accessors by default"() {

        given:
        withDefaultSettings()
        withBuildScript("""
            plugins { java }
        """)

        expect:
        assertAccessorsInClassPathOf(buildFileKts)
    }

    def "jit accessors can be turned off"() {

        given:
        withDefaultSettings()
        withBuildScript("""
            plugins { java }
        """)

        and:
        withFile("gradle.properties", "org.gradle.kotlin.dsl.accessors=off")

        expect:
        assertThat(
            classPathFor(projectDir, buildFile),
            not(hasAccessorsClasses())
        )
    }

    def "the set of jit accessors is a function of the set of applied plugins"() {

        given:
        // TODO:accessors - rework this test to ensure it's providing enough coverage
        def s1 = setOfAutomaticAccessorsFor(["application"])
        def s2 = setOfAutomaticAccessorsFor(["java"])
        def s3 = setOfAutomaticAccessorsFor(["application"])
        def s4 = setOfAutomaticAccessorsFor(["application", "java"])
        def s5 = setOfAutomaticAccessorsFor(["java"])

        expect:
        assertThat(s1, not(equalTo(s2))) // application ≠ java
        assertThat(s1, equalTo(s3))      // application = application
        assertThat(s2, equalTo(s5))      // java        = java
        assertThat(s1, equalTo(s4))      // application ⊇ java
    }

    private void assertAccessorsInClassPathOf(File buildFile) {
        def model = kotlinBuildScriptModelFor(projectDir, buildFile)
        assertThat(model.classPath, hasAccessorsClasses())
        assertThat(model.sourcePath, hasAccessorsSource())
    }

    private Matcher<Iterable<? super File>> hasAccessorsClasses() {
        return hasItem(
            matching({ it.appendText("accessors classes") }) { File file ->
                new File(file, accessorsClassFilePath).isFile()
            }
        )
    }

    private Matcher<Iterable<? super File>> hasAccessorsSource() {
        return hasItem(
            matching({ it.appendText("accessors source") }) { File file ->
                new File(file, accessorsSourceFilePath).isFile()
            }
        )
    }

    private File setOfAutomaticAccessorsFor(Iterable<String> plugins) {
        withDefaultSettings()
        def buildFile = withBuildScript("plugins {\n${plugins.join("\n")}\n}")
        def classFilePath = accessorsClassFor(buildFile).toPath()
        return projectDir.toPath().relativize(classFilePath).toFile()
    }

    private File accessorsClassFor(File buildFile) {
        return classPathFor(projectDir, buildFile)
            .tap { println(it) }
            .find { it.isDirectory() && new File(it, accessorsClassFilePath).isFile() }
    }

    private String accessorsClassFilePath = "org/gradle/kotlin/dsl/ArchivesConfigurationAccessorsKt.class"

    private String accessorsSourceFilePath = "org/gradle/kotlin/dsl/ArchivesConfigurationAccessors.kt"
}
