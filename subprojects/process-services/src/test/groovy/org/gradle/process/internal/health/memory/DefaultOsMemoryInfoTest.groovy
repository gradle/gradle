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

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class DefaultOsMemoryInfoTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def "getTotalPhysicalMemory only throws when memory management methods are unavailable"() {
        expect:
        memTest({ new DefaultOsMemoryInfo().getOsSnapshot().getTotalPhysicalMemory() })
    }

    // We can't exercise both paths at once because we have no control here over the JVM we're running on.
    // However, this will be fully exercised since we test across JVMs.
    def memTest(memCall) {
        try {
            memCall()
            return areSupportedJmxBeansTypesPresent()
        } catch (UnsupportedOperationException ex) {
            return !areSupportedJmxBeansTypesPresent()
        }
    }

    def areSupportedJmxBeansTypesPresent() {
        try {
            def sunClass = ClassLoader.getSystemClassLoader().loadClass("com.sun.management.OperatingSystemMXBean")
            if (sunClass != null) {
                return true
            }
        } catch (ClassNotFoundException ex) {
        }
        try {
            def ibmClass = ClassLoader.getSystemClassLoader().loadClass("com.ibm.lang.management.OperatingSystemMXBean")
            if (ibmClass != null) {
                return true
            }
        } catch (ClassNotFoundException ex) {
        }
        return false
    }
}
