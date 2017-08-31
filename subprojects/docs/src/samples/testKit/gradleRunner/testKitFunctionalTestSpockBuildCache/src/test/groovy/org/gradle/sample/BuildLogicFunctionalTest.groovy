/*
 * Copyright 2017 the original author or authors.
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
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.gradle.testkit.runner.TaskOutcome.*

class BuildLogicFunctionalTest extends Specification {

    @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
    File buildFile

    def setup() {
        buildFile = testProjectDir.newFile('build.gradle')
    }

    // START SNIPPET functional-test-build-cache
    def "cacheableTask is loaded from cache"() {
        given:
        buildFile << """
            plugins {
                id 'org.gradle.sample.helloworld'
            }
        """

        when:
        def result = runner()
            .withArguments( '--build-cache', 'cacheableTask')
            .build()

        then:
        result.task(":cacheableTask").outcome == SUCCESS

        when:
        new File(testProjectDir.root, 'build').deleteDir()
        result = runner()
            .withArguments( '--build-cache', 'cacheableTask')
            .build()

        then:
        result.task(":cacheableTask").outcome == FROM_CACHE
    }
    // END SNIPPET functional-test-build-cache

    def runner() {
        return GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withPluginClasspath()
    }
}
