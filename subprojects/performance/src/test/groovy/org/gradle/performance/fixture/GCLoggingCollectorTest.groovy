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

import org.gradle.performance.measure.DataAmount
import org.gradle.performance.measure.MeasuredOperation
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Resources
import org.junit.Rule
import spock.lang.Specification

class GCLoggingCollectorTest extends Specification {
    @Rule Resources resources = new Resources()
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def collector = new GCLoggingCollector()

    def "parses GC Log"() {
        def operation = new MeasuredOperation()
        def projectDir = tmpDir.createDir("project")
        resources.getResource("gc.txt").copyTo(projectDir.file("build/gc.txt"))

        when:
        collector.collect(projectDir, operation)

        then:
        operation.totalHeapUsage == DataAmount.kbytes(76639)
        operation.maxHeapUsage == DataAmount.kbytes(33334)
        operation.maxUncollectedHeap == DataAmount.kbytes(20002)
        operation.maxCommittedHeap == DataAmount.kbytes(44092)
    }
}
