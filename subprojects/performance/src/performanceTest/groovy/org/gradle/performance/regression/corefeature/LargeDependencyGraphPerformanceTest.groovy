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

import org.gradle.performance.AbstractCrossVersionGradleProfilerPerformanceTest
import org.gradle.performance.WithExternalRepository
import spock.lang.Ignore
import spock.lang.Unroll

class LargeDependencyGraphPerformanceTest extends AbstractCrossVersionGradleProfilerPerformanceTest implements WithExternalRepository {

    private final static TEST_PROJECT_NAME = 'excludeRuleMergingBuild'
    public static final String MIN_MEMORY = "-Xms800m"
    public static final String MAX_MEMORY = "-Xmx800m"

    def setup() {
        runner.minimumBaseVersion = '4.8'
        runner.targetVersions = ["6.1-20191217025822+0000"]
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
    def "resolve large dependency graph (parallel = #parallel, locking = #locking)"() {
        runner.testProject = TEST_PROJECT_NAME
        startServer()

        given:
        runner.tasksToRun = ['resolveDependencies']
        runner.gradleOpts = [MIN_MEMORY, MAX_MEMORY]
        runner.targetVersions = ["6.1-20191217025822+0000"]
        runner.args = ['-PuseHttp', "-PhttpPort=${serverPort}", '-PnoExcludes']
        if (parallel) {
            runner.args += '--parallel'
        }
        if (locking) {
            runner.args += '-PuseLocking'
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        cleanup:
        stopServer()

        where:
        parallel << [false, true, false, true]
        locking << [false, false, true, true]
    }

    @Ignore
    def "resolve large dependency graph with strict versions"() {
        runner.minimumBaseVersion = '6.0'
        runner.testProject = TEST_PROJECT_NAME
        startServer()

        given:
        runner.tasksToRun = ['resolveDependencies']
        runner.gradleOpts = [MIN_MEMORY, MAX_MEMORY]
        runner.args = ['-PuseHttp', "-PhttpPort=${serverPort}", '-PnoExcludes', '-PuseSubgraphConstraints']

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        cleanup:
        stopServer()
    }

}
