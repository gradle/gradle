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

package org.gradle.internal.enterprise

import org.apache.tools.ant.util.TeeOutputStream
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.tooling.model.GradleProject

class DevelocityPluginConfigToolingApiIntegrationTest extends AbstractIntegrationSpec {

    def toolingApi = new ToolingApi(distribution, temporaryFolder)

    def "indicates task-executing builds in config"() {
        settingsFile """
            def config = settings.gradle.services.get(org.gradle.internal.enterprise.GradleEnterprisePluginConfig)
            println("Config: isTaskExecutingBuild=\${config.isTaskExecutingBuild()}")
        """

        buildFile """
            tasks.register("foo")
        """

        when:
        def output = toolingApi.withConnection { connection ->

            def output = new ByteArrayOutputStream()
            def error = new ByteArrayOutputStream()

            connection.newBuild()
                .setStandardOutput(new TeeOutputStream(output, System.out))
                .setStandardError(new TeeOutputStream(error, System.err))
                .forTasks('foo')
                .run()

            OutputScrapingExecutionResult.from(output.toString(), error.toString())
        }

        then:
        output.assertOutputContains("Config: isTaskExecutingBuild=true")
    }

    def "indicates task-executing builds in config when fetching models"() {
        settingsFile """
            def config = settings.gradle.services.get(org.gradle.internal.enterprise.GradleEnterprisePluginConfig)
            println("Config: isTaskExecutingBuild=\${config.isTaskExecutingBuild()}")
        """

        buildFile """
            tasks.register("foo")
        """

        when:
        def output = toolingApi.withConnection { connection ->

            def output = new ByteArrayOutputStream()
            def error = new ByteArrayOutputStream()

            connection.model(GradleProject)
                .setStandardOutput(new TeeOutputStream(output, System.out))
                .setStandardError(new TeeOutputStream(error, System.err))
                .forTasks(tasks)
                .get()

            OutputScrapingExecutionResult.from(output.toString(), error.toString())
        }

        then:
        output.assertOutputContains("Config: isTaskExecutingBuild=$executing")

        where:
        tasks   | executing
        ["foo"] | true
        null    | false
    }

}
