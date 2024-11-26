/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl.isolated

import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

class IsolatedProjectsParallelConfigurationIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    @Rule
    BlockingHttpServer server = new BlockingHttpServer(5_000)

    def setup() {
        server.start()
        settingsFile """
            include(":a")
            include(":b")
            gradle.lifecycle.beforeProject {
                tasks.register("build")
            }
        """
        buildFile """
            ${server.callFromBuildUsingExpression("'configure-root'")}
        """
        buildFile "a/build.gradle", """
            ${server.callFromBuildUsingExpression("'configure-' + project.name")}
        """
        buildFile "b/build.gradle", """
            ${server.callFromBuildUsingExpression("'configure-' + project.name")}
        """
    }

    def 'all projects are configured in parallel for #invocation'() {
        given:
        server.expect("configure-root")
        server.expectConcurrent("configure-a", "configure-b")

        when:
        isolatedProjectsRun(*invocation)

        then:
        result.assertTasksExecuted(expectedTasks)

        where:
        invocation                             | expectedTasks
        ["build"]                              | [":a:build", ":b:build", ":build"]
        ["build", "--configure-on-demand"]     | [":a:build", ":b:build", ":build"]
        ["build", "--no-configure-on-demand"]  | [":a:build", ":b:build", ":build"]
        [":build"]                             | [":build"]
        [":build", "--configure-on-demand"]    | [":build"]
        [":build", "--no-configure-on-demand"] | [":build"]
    }

    def 'parallel configuration can be disabled in favor of configure-on-demand'() {
        given:
        server.expect("configure-root")
        server.expect("configure-a")

        buildFile("b/build.gradle","""
            println "Configure :b"
        """)

        when:
        isolatedProjectsRun(":a:build", "-Dorg.gradle.internal.isolated-projects.configure-on-demand.tasks=true")

        then:
        result.assertTaskExecuted(":a:build")
        outputDoesNotContain("Configure :b")
    }

    // TODO Test -x behavior
}
