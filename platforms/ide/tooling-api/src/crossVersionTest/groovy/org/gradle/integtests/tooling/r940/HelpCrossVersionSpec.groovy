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

package org.gradle.integtests.tooling.r940

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.launcher.cli.HelpFixture
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.build.Help

@ToolingApiVersion(">=9.4.0")
@TargetGradleVersion(">=9.4.0")
class HelpCrossVersionSpec extends ToolingApiSpecification {

    @TargetGradleVersion('>=4.0 <9.4.0')
    def "cannot query old distribution for model"() {
        when:
        withConnection { connection ->
            connection.getModel(Help).renderedText
        }

        then:
        thrown(UnsupportedMethodException)
    }

    def "can fetch model via model query"() {
        when:
        def help = withConnection { connection ->
            connection.getModel(Help.class).renderedText
        }

        then:
        assertHelpText(help)
    }

    def "can fetch model via build action"() {
        when:
        def help = withConnection { connection ->
            connection.action(new FetchHelpTextAction()).run()
        }

        then:
        assertHelpText(help)
    }

    def "model from a build action is the same as from a model query"() {
        when:
        def resultFromQuery = withConnection { connection ->
            connection.getModel(Help.class).renderedText
        }
        def resultFromAction = withConnection { connection ->
            connection.action(new FetchHelpTextAction()).run()
        }

        then:
        resultFromQuery == resultFromAction
    }

    private static void assertHelpText(String help) {
        assert help.normalize() == HelpFixture.DEFAULT_OUTPUT
    }
}
