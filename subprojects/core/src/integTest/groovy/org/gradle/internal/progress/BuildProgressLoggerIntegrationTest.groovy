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
package org.gradle.internal.progress

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.RichConsoleStyling
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

class BuildProgressLoggerIntegrationTest extends AbstractIntegrationSpec implements RichConsoleStyling {
    private static final String SERVER_RESOURCE = 'test-resource'

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        executer.withConsole(ConsoleOutput.Rich)
        server.start()
    }

    def "buildSrc task progress is displayed in initialization phase"() {
        given:
        file("buildSrc/build.gradle") << """
            // NOTE: groovy plugin is automatically applied to buildSrc
            
            ${jcenterRepository()}
            dependencies {
                testCompile 'junit:junit:4.12'
            }
"""
        file("buildSrc/src/test/groovy/org/gradle/integtest/BuildSrcTest.groovy") << """
            package org.gradle.integtest
            import org.junit.Test
            
            public class BuildSrcTest {
                @Test
                public void test() {
                    ${server.callFromBuild(SERVER_RESOURCE)}
                }
            }
        """

        def testExecution = server.expectAndBlock(SERVER_RESOURCE)

        buildFile << "task hello"

        when:
        def gradleHandle = executer.withTasks('hello').start()
        testExecution.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            // This asserts that we see incremental progress, not just 0% => 100%
            gradleHandle.standardOutput.matches(/(?s).*-> \d{2}% INITIALIZING \[.*$/)
        }

        testExecution.releaseAll()
        gradleHandle.waitForFinish()
    }
}
