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

package org.gradle.process.internal.daemon.health.memory

import spock.lang.Specification
import spock.lang.Unroll

class MaximumHeapHelperTest extends Specification {

    def memoryInfo = Mock(MemoryInfo) { getTotalPhysicalMemory() >> gb(8) }

    def "can parse maximum heap null"() {
        given:
        def maximumHeapHelper = new MaximumHeapHelper(memoryInfo);

        expect:
        maximumHeapHelper.getMaximumHeapSize(null) == maximumHeapHelper.getDefaultMaximumHeapSize()
    }

    @Unroll
    def "can parse maximum heap '#maxHeap'"() {
        given:
        def maximumHeapHelper = new MaximumHeapHelper(memoryInfo);

        expect:
        maximumHeapHelper.getMaximumHeapSize(maxHeap) == maxHeapBytes

        where:
        maxHeap | maxHeapBytes
        ''      | new MaximumHeapHelper(Stub(MemoryInfo) { getTotalPhysicalMemory() >> gb(8) }).getDefaultMaximumHeapSize()
        '512m'  | mb(512)
        '768M'  | mb(768)
        '64g'   | gb(64)
        '4G'    | gb(4)
    }

    private static long gb(long gb) {
        gb * 1024 * 1024 * 1024
    }

    private static long mb(long mb) {
        mb * 1024 * 1024
    }
}
