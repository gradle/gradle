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

package org.gradle.performance.regression.corefeature

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.WithExternalRepository
import spock.lang.Unroll

class LargeDependencyGraphPerformanceTest extends AbstractCrossVersionPerformanceTest implements WithExternalRepository {

    private final static TEST_PROJECT_NAME = 'excludeRuleMergingBuild'
    public static final String MIN_MEMORY = "-Xms512m"
    public static final String MAX_MEMORY = "-Xmx512m"

    def setup() {
        runner.minimumVersion = '4.6'
        runner.targetVersions = ["5.0-20180909235858+0000"]
    }

    def "resolve large dependency graph from file repo"() {
        runner.testProject = TEST_PROJECT_NAME

        given:
        runner.tasksToRun = ['resolveDependencies']
        runner.gradleOpts = [MIN_MEMORY, MAX_MEMORY]
        runner.args = ["-PnoExcludes"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    @Unroll
    def "resolve large dependency graph (parallel = #parallel)"() {
        runner.testProject = TEST_PROJECT_NAME
        startServer()

        given:
        runner.tasksToRun = ['resolveDependencies']
        runner.gradleOpts = [MIN_MEMORY, MAX_MEMORY]
        runner.args = ['-PuseHttp', "-PhttpPort=${serverPort}", '-PnoExcludes']
        if (parallel) {
            runner.args += '--parallel'
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        cleanup:
        stopServer()

        where:
        parallel << [false, true]
    }

}
