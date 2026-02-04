/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.initialization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.launcher.cli.HelpFixture

class GradleHelpIntegrationTest extends AbstractIntegrationSpec {
    def "gradle -h is useful"() {
        // This test doesn't actually start a daemon, but we need to require a daemon to avoid the in-process executer
        executer.requireDaemon().requireIsolatedDaemons()

        when:
        succeeds("-h")
        then:
        output == HelpFixture.DEFAULT_OUTPUT
    }
}
