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

package org.gradle.launcher

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@LeaksFileHandles // Cannot delete `native-platform.dll`
class GradleNativeIntegrationTest extends AbstractIntegrationSpec {
    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "explicitly requests a daemon")
    def "caches native binaries in specified user home"() {
        given:
        executer.withNoExplicitNativeServicesDir()
        executer.requireOwnGradleUserHomeDir()
        executer.requireDaemon().requireIsolatedDaemons()

        when:
        succeeds "help"

        then:
        executer.gradleUserHomeDir.file("native").assertIsDir()
    }
}
