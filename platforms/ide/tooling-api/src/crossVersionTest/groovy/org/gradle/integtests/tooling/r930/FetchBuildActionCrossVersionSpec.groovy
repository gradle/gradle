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

package org.gradle.integtests.tooling.r930

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.GradleBuild

import static java.util.Collections.emptyList

interface UnknownModel {}

@TargetGradleVersion(">=9.3.0")
@ToolingApiVersion(">=9.3.0")
class FetchBuildActionCrossVersionSpec extends ToolingApiSpecification {

    static class FetchGradleBuildAction implements BuildAction<Integer> {
        @Override
        Integer execute(BuildController controller) {
            return controller
                .fetch(emptyList(), GradleBuild.class, null, null)
                .filter { it.model instanceof GradleBuild }
                .count()
        }
    }

    static class FetchUnknownModelAction implements BuildAction<List<String>> {
        @Override
        List<String> execute(BuildController controller) {
            return controller
                .fetch(emptyList(), UnknownModel.class, null, null)
                .filter { it.model == null }
                .flatMap { it.failures.stream() }
                .flatMap { it.causes.stream() }
                .map { it.message }
                .toList()
        }
    }

    def "can request GradleBuild model"() {
        when:
        def result = withConnection { connection ->
            connection
                .action(new FetchGradleBuildAction())
                .run()
        }

        then:
        result == 1
    }

    def "can request unknown model"() {
        when:
        def causes = withConnection { connection ->
            connection
                .action(new FetchUnknownModelAction())
                .run()
        }

        then:
        causes == ["No builders are available to build a model of type 'org.gradle.integtests.tooling.r930.UnknownModel'."]
    }
}
