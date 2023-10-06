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
import org.gradle.test.fixtures.Flaky

import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.MatcherAssert.assertThat

@TargetGradleVersion(">=5.4")
class KotlinInitScriptModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    @Flaky(because = 'https://github.com/gradle/gradle-private/issues/3708')
    def "initscript classpath does not include buildSrc"() {

        given:
        withBuildSrc()
        withDefaultSettings()

        and:
        def initScript = withFile("my.init.gradle.kts")

        when:
        def classPath = canonicalClassPathFor(initScript)

        then:
        assertContainsGradleKotlinDslJars(classPath)
        assertThat(
            classPath.collect { it.name },
            not(hasBuildSrc())
        )
    }

    def "can fetch initscript classpath in face of compilation errors"() {

        given:
        withDefaultSettings()
        withEmptyJar("classes.jar")

        and:
        def initScript =
            withFile("my.init.gradle.kts") << """
                initscript {
                    dependencies {
                        classpath(files("classes.jar"))
                    }
                }

                val p =
            """

        when:
        def classPath = canonicalClassPathFor(initScript)

        then:
        assertContainsGradleKotlinDslJars(classPath)
        assertClassPathContains(
            classPath,
            file("classes.jar")
        )
    }

    List<File> canonicalClassPathFor(File initScript) {
        canonicalClassPathFor(projectDir, initScript)
    }
}
