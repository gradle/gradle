/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.testkit.runner

import org.gradle.testkit.runner.fixtures.CustomDaemonDirectory
import org.gradle.testkit.runner.fixtures.NoDebug

class GradleRunnerEnvironmentVariablesIntegrationTest extends BaseGradleRunnerIntegrationTest {

    @CustomDaemonDirectory //avoid the daemon to be reused to avoid leaking env variable
    @NoDebug //avoid in-process execution so that we can set the env variable
    def "use can provide environment variables"() {
        given:
        buildScript "file('env.txt') << System.getenv('dummyEnvVar')"

        when:
        runner().withEnvironment(dummyEnvVar: "env var OK").build()

        then:
        file('env.txt').text == "env var OK"
    }
}
