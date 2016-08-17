/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.performance.fixture

import org.gradle.performance.measure.MeasuredOperation
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Resources
import org.junit.Rule
import spock.lang.Specification

class PerformanceCounterCollectorTest extends Specification {
    @Rule
    Resources resources = new Resources()
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def collector = new PerformanceCounterCollector()

    def "performance counters should record total compile time and total gc time for operation"() {
        given:
        def operation = new MeasuredOperation()
        def projectDir = tmpDir.createDir("project")
        def buildDir = projectDir.createDir("build")
        ['perf_counters_start.txt', 'perf_counters_finish.txt'].each { resources.getResource(it).copyTo(buildDir.file(it)) }
        def invocationInfo = Stub(BuildExperimentInvocationInfo) {
            getProjectDir() >> projectDir
        }

        when:
        collector.collect(invocationInfo, operation)

        then:
        operation.compileTotalTime.getValue().toLong() == 6004L
        operation.gcTotalTime.getValue().toLong() == 81L
    }
}
