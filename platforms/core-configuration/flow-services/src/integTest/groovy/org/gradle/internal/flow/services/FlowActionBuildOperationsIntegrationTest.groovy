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

package org.gradle.internal.flow.services

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.RichConsoleStyling
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/39528")
class FlowActionBuildOperationsIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "runs each flow action in a build operation"() {
        given:
        buildFile << flowAction("println 'Flow action ran'")

        when:
        succeeds 'help'

        then:
        outputContains 'Flow action ran'
        operations.only("Run flow action PrintAction").failure == null
    }

    def "adds the path of an included build to the operations of its flow actions"() {
        given:
        settingsFile << "includeBuild 'included'"
        file('included/settings.gradle') << "rootProject.name = 'included'"
        file('included/build.gradle') << flowAction("println 'Flow action ran'") << """
            tasks.register('noop')
        """
        buildFile << """
            tasks.register('run') {
                dependsOn gradle.includedBuild('included').task(':noop')
            }
        """

        when:
        succeeds 'run'

        then:
        operations.only("Run flow action PrintAction (:included)").failure == null
    }

    def "fails the operation of a failing flow action"() {
        given:
        buildFile << flowAction("throw new RuntimeException('flow action failed on purpose')")

        when:
        fails 'help'

        then:
        failure.assertHasDescription('flow action failed on purpose')
        operations.only("Run flow action PrintAction").failure.contains('flow action failed on purpose')
    }

    def "shows a running flow action on a worker line"() {
        given:
        server.start()
        buildFile << flowAction("new URL('${server.uri("flow")}').text")
        def flow = server.expectAndBlock("flow")

        when:
        executer.withTestConsoleAttached()
        executer.withConsole(ConsoleOutput.Rich)
        def build = executer.withTasks('help').start()
        flow.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            RichConsoleStyling.assertHasWorkInProgress(build, "> Flow action PrintAction")
        }

        cleanup:
        flow?.releaseAll()
        build?.waitForFinish()
    }

    private static String flowAction(String body) {
        """
            import org.gradle.api.flow.*

            class FlowActionPlugin implements Plugin<Project> {
                final FlowScope flowScope

                @Inject
                FlowActionPlugin(FlowScope flowScope) {
                    this.flowScope = flowScope
                }

                void apply(Project target) {
                    flowScope.always(PrintAction) {}
                }
            }

            class PrintAction implements FlowAction<FlowParameters.None> {
                void execute(FlowParameters.None parameters) {
                    ${body}
                }
            }

            apply type: FlowActionPlugin
        """
    }
}
