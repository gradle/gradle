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

package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.launcher.cli.HelpFixture
import org.gradle.tooling.model.build.Help

class HelpModelIntegrationTest extends AbstractIntegrationSpec {

    final GradleDistribution dist = new UnderDevelopmentGradleDistribution()
    final ToolingApi toolingApi = new ToolingApi(dist, temporaryFolder)

    def setup() {
        settingsFile.touch()
    }

    def "can fetch model via model query"() {
        when:
        def help = toolingApi.withConnection { connection ->
            connection.getModel(Help.class).renderedText
        }

        then:
        assertHelpText(help)
    }

    def "can fetch model via build action"() {
        when:
        def help = toolingApi.withConnection { connection ->
            connection.action(new FetchHelpTextAction()).run()
        }

        then:
        assertHelpText(help)
    }

    private static void assertHelpText(String help) {
        assert help.normalize() == HelpFixture.DEFAULT_OUTPUT
    }

}
