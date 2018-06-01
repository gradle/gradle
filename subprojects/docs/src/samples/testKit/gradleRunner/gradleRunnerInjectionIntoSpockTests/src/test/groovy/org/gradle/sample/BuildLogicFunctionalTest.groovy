/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.sample

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.spock.ProjectDirProvider
import org.junit.ClassRule
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.IgnoreRest
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files

class BuildLogicFunctionalTest extends Specification {
    // START SNIPPET fields
    @Shared
    private GradleRunner sharedGradleRunner

    private GradleRunner gradleRunner
    // END SNIPPET fields

    // START SNIPPET fields-with-custom-project-dir
    @Shared
    @ClassRule
    private TemporaryFolder sharedTestProjectDir = new TemporaryFolder()

    @ProjectDirProvider({ sharedTestProjectDir })
    private GradleRunner gradleRunnerWithCustomProjectDir

    @Shared
    @ProjectDirProvider({ Files.createTempDirectory null })
    private GradleRunner sharedGradleRunnerWithCustomProjectDir
    // END SNIPPET fields-with-custom-project-dir

    // START SNIPPET parameter
    def 'GradleRunner is injected as parameter'(GradleRunner gradleRunner) {
        when:
        gradleRunner.build()

        then:
        noExceptionThrown()
    }
    // END SNIPPET parameter

    // START SNIPPET parameter-in-data-driven-feature
    def 'GradleRunner is injected as parameter into data-driven feature'(
            a, b, GradleRunner gradleRunner) {
        when:
        gradleRunner.build()

        then:
        noExceptionThrown()

        where:
        a | b
        1 | 3
        2 | 4

        and:
        gradleRunner = null
    }
    // END SNIPPET parameter-in-data-driven-feature

    // START SNIPPET parameter-with-custom-project-dir
    @Rule
    private TemporaryFolder testProjectDir

    def 'GradleRunner is injected as parameter with custom project dir'(
            @ProjectDirProvider({ testProjectDir }) GradleRunner gradleRunner) {
        when:
        gradleRunner.build()

        then:
        noExceptionThrown()
    }
    // END SNIPPET parameter-with-custom-project-dir
}
