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
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class DistributedPerformanceTestRelocateablityTest extends AbstractPerformanceTestSpec {
    @Rule
    TemporaryFolder relocationFolder = new TemporaryFolder()

    File relocationFile(String path) {
        def result = new File(relocationFolder.root, path)
        result.parentFile.mkdirs()
        return result
    }

    String buildFile() {
        """
            ${buildscriptBlock()}
            apply plugin: 'java'
            dependencies { 
                ${junitClasspath()}
            }
            tasks.create(name: 'mockDistributedPerformanceTest', type: org.gradle.plugins.performance.MockDistributedPerformanceTest) {
                3.times {
                    // we only want to test ourself's providers
                    jvmArgumentProviders.remove(0)
                } 
                testClassesDirs = sourceSets['test'].output.classesDirs
                classpath = sourceSets['test'].runtimeClasspath
                addDatabaseParameters(['org.gradle.performance.db.url': project.findProperty('dbUrl')])
                buildTypeId = 'myBuildTypeId'
                teamCityUrl = 'myTeamCityUrl'
                teamCityUsername = 'myTeamCityUsername'
                workerTestTaskName = 'myWorkerTestTaskName'
                scenarioList = file('scenarioList')
                scenarioReport = file('scenarioReport')
                reportDir = file('reportDir')

                doFirst {
                    println systemProperties
                }
            }
        """
    }

    def "distributed preformance test is relocateable"() {
        given:
        file('src/test/java/Test.java') << testFile()
        relocationFile('src/test/java/Test.java') << testFile()
        buildFile << buildFile()
        relocationFile('build.gradle') << buildFile()

        when:
        build('mockDistributedPerformanceTest', '--baselines', 'myBaselines', '-PdbUrl=myUrl', '--build-cache')
        def result = createAndConfigureGradleRunner(relocationFolder.root, 'mockDistributedPerformanceTest', '--baselines', 'myBaselines', '-PdbUrl=myUrl', '--build-cache').build()

        then:
        assert result.task(':mockDistributedPerformanceTest').outcome == TaskOutcome.FROM_CACHE
        assert relocationFile('reportDir/report').text == 'myBaselines'
    }
}
