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
package org.gradle.plugins.performance

import org.gradle.testkit.runner.TaskOutcome

class PerformanceTestCacheablityTest extends AbstractPerformanceTestSpec {
    def setup() {
        file('src/test/java/Test.java') << testFile()
        buildFile << """ 
            ${buildscriptBlock()}
            apply plugin: 'java'
            dependencies { 
                ${junitClasspath()}
            }
            tasks.create(name: 'mockPerformanceTest', type: org.gradle.testing.PerformanceTest) {
                3.times {
                    // we only want to test ourself's providers
                    jvmArgumentProviders.remove(0)
                } 
                testClassesDirs = sourceSets['test'].output.classesDirs
                classpath = sourceSets['test'].runtimeClasspath
                addDatabaseParameters(['org.gradle.performance.db.url': project.findProperty('dbUrl')])
                addDatabaseParameters(['org.gradle.performance.db.username': project.findProperty('dbUsername')])
            }
        """
    }

    def 'can read all system properties and is cacheable'() {
        when:
        def result = build('mockPerformanceTest', '-PdbUrl=myUrl',
            '--scenarios', 'myScenarios',
            '--baselines', 'myBaselines',
            '--warmups', 'myWarmups',
            '--runs', 'myRuns',
            '--checks', 'myChecks',
            '--channel', 'myChannel',
            '--flamegraphs', 'true')
        def report = file('build/reports/tests/mockPerformanceTest/classes/Test.html').text

        then:
        result.task(':mockPerformanceTest').outcome == TaskOutcome.SUCCESS
        report.contains('org.gradle.performance.scenarios=myScenarios')
        report.contains('org.gradle.performance.baselines=myBaselines')
        report.contains('org.gradle.performance.execution.warmups=myWarmups')
        report.contains('org.gradle.performance.execution.checks=myChecks')
        report.contains('org.gradle.performance.execution.channel=myChannel')
        report.contains("org.gradle.performance.db.url=myUrl")
        report.matches(/(?s).*org.gradle.performance.flameGraphTargetDir=.*mockPerformanceTest.flames.*/)

        when:
        result = build('mockPerformanceTest', '-PdbUrl=myUrl',
            '--scenarios', 'myScenarios',
            '--baselines', 'myBaselines',
            '--warmups', 'myWarmups',
            '--runs', 'myRuns',
            '--checks', 'myChecks',
            '--channel', 'myChannel',
            '--flamegraphs', 'true')

        then:
        result.task(':mockPerformanceTest').outcome == TaskOutcome.UP_TO_DATE

        when:
        result = build('mockPerformanceTest', '-PdbUrl=myUrl')

        then:
        result.task(':mockPerformanceTest').outcome == TaskOutcome.SUCCESS
    }

    def "can read db username but it's not input"() {
        when:
        def result = build('mockPerformanceTest', '-PdbUrl=myUrl', '-PdbUsername=myUsername')
        def report = file('build/reports/tests/mockPerformanceTest/classes/Test.html').text

        then:
        result.task(':mockPerformanceTest').outcome == TaskOutcome.SUCCESS
        report.contains("org.gradle.performance.db.username=myUsername")

        when:
        result = build('mockPerformanceTest', '-PdbUrl=myUrl', '-PdbUsername=yourUsername')

        then:
        result.task(':mockPerformanceTest').outcome == TaskOutcome.UP_TO_DATE
    }

    def 'do not cache when baselines contains nightly'() {
        when:
        build('mockPerformanceTest', '-PdbUrl=myUrl', '--baselines', 'nightly,whatever')
        def result = build('mockPerformanceTest', '-PdbUrl=myUrl', '--baselines', 'nightly,whatever', '-i')

        then:
        result.task(':mockPerformanceTest').outcome == TaskOutcome.SUCCESS
    }
}
