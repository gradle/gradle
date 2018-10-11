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

package org.gradle.launcher.daemon

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.NOT_UNKNOWN_OS)
class DaemonJvmSettingsIntegrationTest extends DaemonIntegrationSpec {
    def "uses current JVM and default JVM args when none specified"() {
        file('build.gradle') << """
assert java.lang.management.ManagementFactory.runtimeMXBean.inputArguments.contains('-Xmx512m')
assert java.lang.management.ManagementFactory.runtimeMXBean.inputArguments.contains('-XX:+HeapDumpOnOutOfMemoryError')
"""

        given:
        executer.useOnlyRequestedJvmOpts()

        expect:
        succeeds()
    }
}
