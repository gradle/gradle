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

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Specification

class MemoryInfoTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    // We can't exercise both paths at once because we have no control here over the JVM we're running on.
    // However, this will be fully exercised since we test across JVMs.
    def memTest(memCall) {
        try {
            memCall()
            Class<?> sunClass = ClassLoader.getSystemClassLoader().loadClass("com.sun.management.OperatingSystemMXBean")
            return sunClass != null
        } catch (UnsupportedOperationException e) {
            try {
                ClassLoader.getSystemClassLoader().loadClass("com.sun.management.OperatingSystemMXBean")
            } catch (ClassNotFoundException expected) {
                return true
            }
            return false
        }
    }

    def "getTotalPhysicalMemory only throws when memory management methods are unavailable"() {
        expect:
        memTest({ new MemoryInfo().getTotalPhysicalMemory() })
    }

    // We only use MX Bean methods on Windows
    @Requires(TestPrecondition.WINDOWS)
    def "getFreePhysicalMemory only throws when memory management methods are unavailable"() {
        expect:
        memTest({ new MemoryInfo().getFreePhysicalMemory() })
    }


}
