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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class MemoryInfoIntegrationTest extends AbstractIntegrationSpec {
    @Requires(TestPrecondition.WINDOWS)
    def "gets available memory on a real live Windows system"() {
        when:
        new MemoryInfo().getFreePhysicalMemory()

        then:
        notThrown UnsupportedOperationException
    }

    @Requires(TestPrecondition.LINUX)
    def "gets available memory on a real live Linux system"() {
        when:
        new MemoryInfo().getFreePhysicalMemory()

        then:
        notThrown UnsupportedOperationException
    }

    @Requires(TestPrecondition.MAC_OS_X)
    def "gets available memory on a real live MacOS system"() {
        when:
        new MemoryInfo().getFreePhysicalMemory()

        then:
        notThrown UnsupportedOperationException
    }
}
