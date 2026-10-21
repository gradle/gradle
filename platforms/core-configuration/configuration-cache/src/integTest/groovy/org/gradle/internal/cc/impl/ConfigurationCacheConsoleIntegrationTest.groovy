/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.RichConsoleStyling
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/39539")
class ConfigurationCacheConsoleIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def "shows the check of the inputs on a worker line"() {
        given:
        server.start()
        buildFile << """
            import org.gradle.api.provider.ValueSourceParameters

            abstract class RemoteValue implements ValueSource<String, ValueSourceParameters.None> {
                String obtain() {
                    ${server.callFromBuild("value")}
                    return "value"
                }
            }

            providers.of(RemoteValue) {}.get()
            tasks.register('work')
        """
        server.expect("value")
        richConsole()
        run ENABLE_CLI_OPT, 'work'

        when:
        def check = server.expectAndBlock("value")
        richConsole()
        def build = executer.withArguments(ENABLE_CLI_OPT).withTasks('work').start()
        check.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            RichConsoleStyling.assertHasWorkInProgress(build, "> Checking configuration cache fingerprint")
        }

        cleanup:
        check?.releaseAll()
        build?.waitForFinish()
    }

    private void richConsole() {
        executer.withTestConsoleAttached()
        executer.withConsole(ConsoleOutput.Rich)
    }
}
