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

package org.gradle.kotlin.dsl.tooling.builders.r95

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.tooling.BuildException
import org.hamcrest.Matcher
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.MatcherAssert.assertThat


@TargetGradleVersion(">=9.6")
@Issue("https://github.com/gradle/gradle/issues/37942")
class LazySourceSetAccessorCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    def "TAPI model does not expose accessors for configurations of source sets lazily registered in the script body"() {

        given:
        withDefaultSettings()
        // A source set (not just a configuration) is required to reproduce: DefaultProjectSchemaProvider
        // walks the SourceSetContainer and a lazily registered source set introduces configurations
        // such as `<name>Implementation` that the script body itself never sees, since the body has
        // not yet executed at accessor-generation time.
        withBuildScript("""
            plugins { `java-library` }
            val customSourceSet = sourceSets.register("customSourceSet")
        """)

        when:
        def classPath = classPathFor(projectDir, buildFileKts)

        then: "build-time script compilation cannot resolve customSourceSetImplementation, so the TAPI accessor classpath must not contain it either"
        assertThat(classPath, not(hasAccessorClassFile(
            "org/gradle/kotlin/dsl/CustomSourceSetImplementationConfigurationAccessorsKt.class"
        )))

        when: "the script references the supposed accessor and the build runs"
        buildFileKts << """
            dependencies {
                customSourceSetImplementation("org.example:does-not-matter:1.0")
            }
        """.stripIndent()
        fails { connection -> connection.newBuild().forTasks("help").run() }

        then: "the script fails to compile because the accessor really does not exist"
        BuildException e = thrown()
        e.cause.message.contains("Unresolved reference 'customSourceSetImplementation'")
    }

    private static Matcher<Iterable<? super File>> hasAccessorClassFile(String relativePath) {
        return hasItem(
            matching({ it.appendText("a classpath entry containing $relativePath") }) { File entry ->
                new File(entry, relativePath).isFile()
            }
        )
    }
}
