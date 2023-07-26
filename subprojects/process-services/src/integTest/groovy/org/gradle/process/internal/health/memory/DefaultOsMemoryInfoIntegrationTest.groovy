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

import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

@UsesNativeServices
class DefaultOsMemoryInfoIntegrationTest extends Specification {

    @Requires(UnitTestPreconditions.Windows)
    def "gets OS total memory on a Windows system"() {
        when:
        new DefaultOsMemoryInfo().getOsSnapshot().getPhysicalMemory().getTotal()

        then:
        notThrown UnsupportedOperationException
    }

    @Requires(UnitTestPreconditions.Windows)
    def "gets OS free memory on a Windows system"() {
        when:
        new DefaultOsMemoryInfo().getOsSnapshot().getPhysicalMemory().getFree()

        then:
        notThrown UnsupportedOperationException
    }

    @Requires(UnitTestPreconditions.Windows)
    def "gets OS virtual memory on a Windows system"() {
        when:
        def virtualMemory = new DefaultOsMemoryInfo().getOsSnapshot().getVirtualMemory()

        then:
        virtualMemory instanceof OsMemoryCategory.Limited
        virtualMemory.getFree() > 0
        virtualMemory.getTotal() > 0
    }

    @Requires(UnitTestPreconditions.Linux)
    def "gets OS total memory on a Linux system"() {
        when:
        new DefaultOsMemoryInfo().getOsSnapshot().getPhysicalMemory().getTotal()

        then:
        notThrown UnsupportedOperationException
    }

    @Requires(UnitTestPreconditions.Linux)
    def "gets OS free memory on a Linux system"() {
        when:
        new DefaultOsMemoryInfo().getOsSnapshot().getPhysicalMemory().getFree()

        then:
        notThrown UnsupportedOperationException
    }

    @Requires(UnitTestPreconditions.Linux)
    def "reports unknown OS virtual memory on a Linux system"() {
        when:
        def virtualMemory = new DefaultOsMemoryInfo().getOsSnapshot().getVirtualMemory()

        then:
        virtualMemory instanceof OsMemoryCategory.Unknown
    }

    @Requires(UnitTestPreconditions.MacOs)
    def "gets OS total memory on a MacOS system"() {
        when:
        new DefaultOsMemoryInfo().getOsSnapshot().getPhysicalMemory().getTotal()

        then:
        notThrown UnsupportedOperationException
    }

    @Requires(UnitTestPreconditions.MacOs)
    def "gets OS free memory on a MacOS system"() {
        when:
        new DefaultOsMemoryInfo().getOsSnapshot().getPhysicalMemory().getFree()

        then:
        notThrown UnsupportedOperationException
    }

    @Requires(UnitTestPreconditions.MacOs)
    def "reports unknown OS virtual memory on a MacOs system"() {
        when:
        def virtualMemory = new DefaultOsMemoryInfo().getOsSnapshot().getVirtualMemory()

        then:
        virtualMemory instanceof OsMemoryCategory.Unknown
    }
}
