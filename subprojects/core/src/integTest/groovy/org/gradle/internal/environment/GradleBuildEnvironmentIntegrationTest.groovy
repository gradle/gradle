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

package org.gradle.internal.environment

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

class GradleBuildEnvironmentIntegrationTest extends AbstractIntegrationSpec {
    @Requires(IntegTestPreconditions.IsEmbeddedExecutor)
    def 'can know the current environment if daemon = #daemon'() {
        given:
        buildFile << """
            import org.gradle.internal.environment.GradleBuildEnvironment
            class DaemonJudge extends DefaultTask {
                @Internal
                GradleBuildEnvironment buildEnvironment

                @Inject
                DaemonJudge(GradleBuildEnvironment buildEnvironment) {
                    this.buildEnvironment = buildEnvironment
                }

                @TaskAction
                void run() {
                    assert buildEnvironment.isLongLivingProcess() == ${daemon}
                }
            }

            task judge(type: DaemonJudge)
        """
        // Force to start a new JVM
        file("gradle.properties") << "org.gradle.jvmargs=-Xmx32m"

        expect:
        succeeds('judge', arg)

        where:
        daemon | arg
        true   | '--daemon'
        false  | '--no-daemon'
    }
}
