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

    def "gets OS total memory on any system"() {
        when:
        new DefaultOsMemoryInfo().getOsSnapshot().getPhysicalMemory().getTotal()

        then:
        notThrown UnsupportedOperationException
    }

    def "gets OS free memory on any system"() {
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
        virtualMemory instanceof OsMemoryStatusAspect.Available
    }


    @Requires(UnitTestPreconditions.NotWindows)
    def "reports unknown OS virtual memory on a non-Windows system"() {
        when:
        def virtualMemory = new DefaultOsMemoryInfo().getOsSnapshot().getVirtualMemory()

        then:
        virtualMemory instanceof OsMemoryStatusAspect.Unavailable
    }
}
