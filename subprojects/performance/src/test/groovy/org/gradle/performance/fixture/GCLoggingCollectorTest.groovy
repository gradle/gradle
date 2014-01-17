/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.performance.measure.DataAmount
import org.gradle.performance.measure.MeasuredOperation
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Resources
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

class GCLoggingCollectorTest extends Specification {
    @Rule Resources resources = new Resources()
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def collector = new GCLoggingCollector()

    @Unroll
    def "parses GC Log #logName"() {
        def operation = new MeasuredOperation()
        def projectDir = tmpDir.createDir("project")
        resources.getResource(logName).copyTo(projectDir.file("gc.txt"))

        when:
        collector.beforeExecute(projectDir, Stub(GradleExecuter))
        collector.collect(projectDir, operation)

        then:
        operation.totalHeapUsage == DataAmount.kbytes(totalHeapUsage)
        operation.maxHeapUsage == DataAmount.kbytes(maxHeapUsage)
        operation.maxUncollectedHeap == DataAmount.kbytes(maxUncollectedHeap)
        operation.maxCommittedHeap == DataAmount.kbytes(maxCommittedHeap)

        where:
        logName    | totalHeapUsage | maxHeapUsage | maxUncollectedHeap | maxCommittedHeap
        "gc-1.txt" | 76639          | 33334        | 20002              | 44092
        "gc-2.txt" | 140210         | 40427        | 34145              | 223360
        "gc-3.txt" | 183544         | 119384       | 37982              | 295488
    }
}
