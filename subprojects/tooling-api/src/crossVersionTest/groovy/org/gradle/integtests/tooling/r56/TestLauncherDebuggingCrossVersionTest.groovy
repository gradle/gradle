/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.tooling.r56

import org.gradle.integtests.tooling.TestLauncherSpec
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.tooling.TestLauncher


@ToolingApiVersion(">=5.6")
@TargetGradleVersion(">=5.6")
class TestLauncherDebuggingCrossVersionTest extends TestLauncherSpec {

    def "can launch tests in debug mode"() {
        when:
        Thread.start {
            launchTests { TestLauncher launcher ->
                launcher.withJvmTestMethods("example.MyTest", "foo").withDebugOptions(4008)
            }
        }

        then:
        ConcurrentTestUtil.poll {
            assert stdout.toString().contains("Listening for transport dt_socket at address: 4008")
        }
    }

    // TODO test cancellation
    // TODO test what happens in port is taken
    // TODO test what happens if the test configuration already contains debug options
}
