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


import org.gradle.internal.jvm.Jvm
import spock.lang.Specification

class MBeanOsMemoryInfoTest extends Specification {
    private static final long TEST_TOTAL_MEMORY = 1024L * 1024 * 1024
    private static final long TEST_FREE_MEMORY = 512L * 1024 * 1024
    private static final String MBEAN_OS = "java.lang:type=OperatingSystem"
    private static final String TOTAL_MEMORY_ATTRIBUTE = Jvm.current().isIbmJvm() ? "TotalPhysicalMemory" : "TotalPhysicalMemorySize"
    private static final TestMBeanAttributeProvider.AttributeKey TOTAL_MEMORY_KEY = new TestMBeanAttributeProvider.AttributeKey(MBEAN_OS, TOTAL_MEMORY_ATTRIBUTE, Long.class)
    private static final TestMBeanAttributeProvider.AttributeKey FREE_MEMORY_KEY = new TestMBeanAttributeProvider.AttributeKey(MBEAN_OS, "FreePhysicalMemorySize", Long.class)

    private enum MemoryAvailability {
        AVAILABLE,
        UNAVAILABLE,
        INVALID_VALUE,
        ;

        String toString() {
            switch (this) {
                case AVAILABLE:
                    return "available"
                case UNAVAILABLE:
                    return "unavailable"
                case INVALID_VALUE:
                    return "an invalid value"
                default:
                    throw new IllegalArgumentException("Unknown availability: " + this)
            }
        }
    }

    def "getTotalPhysicalMemory works when memory MBeans are available"() {
        given:
        def osMemoryInfo = createMemoryInfo(MemoryAvailability.AVAILABLE)

        when:
        def total = osMemoryInfo.getOsSnapshot().getPhysicalMemory().getTotal()

        then:
        total == TEST_TOTAL_MEMORY
    }

    def "getTotalPhysicalMemory fails when memory MBeans are #availability"() {
        given:
        def osMemoryInfo = createMemoryInfo(availability)

        when:
        osMemoryInfo.getOsSnapshot().getPhysicalMemory().getTotal()

        then:
        thrown UnsupportedOperationException

        where:
        availability << [MemoryAvailability.UNAVAILABLE, MemoryAvailability.INVALID_VALUE]
    }

    def "getFreePhysicalMemory works when memory MBeans are available"() {
        given:
        def osMemoryInfo = createMemoryInfo(MemoryAvailability.AVAILABLE)

        when:
        def free = osMemoryInfo.getOsSnapshot().getPhysicalMemory().getFree()

        then:
        free == TEST_FREE_MEMORY
    }

    def "getFreePhysicalMemory fails when memory MBeans are #availability"() {
        given:
        def osMemoryInfo = createMemoryInfo(availability)

        when:
        osMemoryInfo.getOsSnapshot().getPhysicalMemory().getFree()

        then:
        thrown UnsupportedOperationException

        where:
        availability << [MemoryAvailability.UNAVAILABLE, MemoryAvailability.INVALID_VALUE]
    }

    OsMemoryInfo createMemoryInfo(MemoryAvailability availability) {
        Map<TestMBeanAttributeProvider.AttributeKey, Object> attributes
        switch (availability) {
            case MemoryAvailability.AVAILABLE:
                attributes = [
                    (TOTAL_MEMORY_KEY): TEST_TOTAL_MEMORY,
                    (FREE_MEMORY_KEY): TEST_FREE_MEMORY,
                ]
                break
            case MemoryAvailability.UNAVAILABLE:
                attributes = [:]
                break
            case MemoryAvailability.INVALID_VALUE:
                attributes = [
                    (TOTAL_MEMORY_KEY): -1L,
                    (FREE_MEMORY_KEY): -1L,
                ]
                break
            default:
                throw new IllegalArgumentException("Unknown availability: " + availability)
        }
        return new MBeanOsMemoryInfo(new TestMBeanAttributeProvider(attributes))
    }
}
