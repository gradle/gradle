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

package org.gradle.process.internal.health.memory

import spock.lang.Specification

class MaximumHeapHelperTest extends Specification {

    def "default max heap on IBM #testCase is #expected"() {
        expect:
        maximumHeapHelper.getDefaultMaximumHeapSize(osTotalMemory.bytes) == expected.bytes

        where:
        osTotalMemory            | expected
        MemoryAmount.of('2g')    | MemoryAmount.of('512m')
        MemoryAmount.of('1g')    | MemoryAmount.of('512m')
        MemoryAmount.of('1000m') | MemoryAmount.of('500m')

        maximumHeapHelper = testMaximumHeapHelper(true)

        testCase = "JVM with ${osTotalMemory} OS total memory"
    }

    def "default max heap on #testCase is #expected"() {
        expect:
        maximumHeapHelper.getDefaultMaximumHeapSize(osTotalMemory.bytes) == expected.bytes

        where:
        osTotalMemory           | bitMode | server | expected
        MemoryAmount.of('512g') | 64      | true   | MemoryAmount.of('32g')
        MemoryAmount.of('8g')   | 64      | true   | MemoryAmount.of('2g')
        MemoryAmount.of('512g') | 64      | false  | MemoryAmount.of('1g')
        MemoryAmount.of('2g')   | 64      | false  | MemoryAmount.of('512m')
        MemoryAmount.of('8g')   | 32      | false  | MemoryAmount.of('1g')
        MemoryAmount.of('2g')   | 32      | false  | MemoryAmount.of('512m')

        maximumHeapHelper = testMaximumHeapHelper(false, bitMode, server)

        testCase = "${bitMode}bits ${server ? 'server' : 'client'} JVM with ${osTotalMemory} OS total memory"
    }

    private static MaximumHeapHelper testMaximumHeapHelper(ibm, bitMode = 64, server = true) {
        new MaximumHeapHelper() {
            @Override
            boolean isIbmJvm() { return ibm }

            @Override
            int getJvmBitMode() { return bitMode }

            @Override
            boolean isServerJvm() { return server }
        }
    }
}
