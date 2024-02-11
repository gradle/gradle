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

package org.gradle.kotlin.dsl.tooling.builders.r75

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.test.fixtures.Flaky
import org.gradle.test.fixtures.file.LeaksFileHandles

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.not

@TargetGradleVersion(">=7.5")
class KotlinSettingsScriptModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    @LeaksFileHandles("Kotlin compiler daemon on buildSrc jar")
    def "sourcePath does not include buildSrc source roots"() {

        given:
        withKotlinBuildSrc()
        def settings = withDefaultSettings() << """
            include(":sub")
        """

        expect:
        assertThat(
            sourcePathFor(settings),
            not(matchesProjectsSourceRoots(withMainSourceSetJavaKotlinIn("buildSrc")))
        )
    }

    @LeaksFileHandles("Kotlin compiler daemon on buildSrc jar")
    @Flaky(because = "https://github.com/gradle/gradle-private/issues/3708")
    def "sourcePath does not include buildSrc project dependencies source roots"() {

        given:
        def sourceRoots = withMultiProjectKotlinBuildSrc()
        def settings = withDefaultSettings() << """
            include(":sub")
        """

        expect:
        assertThat(
            sourcePathFor(settings),
            not(matchesProjectsSourceRoots(sourceRoots))
        )
    }

}
