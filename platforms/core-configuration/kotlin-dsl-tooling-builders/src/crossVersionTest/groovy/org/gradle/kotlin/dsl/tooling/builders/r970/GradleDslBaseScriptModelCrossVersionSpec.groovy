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

package org.gradle.kotlin.dsl.tooling.builders.r970

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.BuildException
import org.gradle.tooling.model.dsl.GradleDslBaseScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

/**
 * From Gradle 9.7 onwards, a configuration failure observed while resiliently fetching a model (the {@code fetch}
 * API) is propagated to the client as a {@link BuildException} when the build finishes. Querying the same model with
 * the classic {@code getModel} API still throws at the call site (so the build action can catch it) and does not
 * fail the build, which keeps the base script model obtainable for IDE script editing.
 *
 * See the {@code <9.7.0} version in {@code r93.GradleDslBaseScriptModelCrossVersionSpec} for the previous behaviour,
 * where the {@code fetch} path also kept the build successful.
 */
@ToolingApiVersion(">=9.3.0")
@TargetGradleVersion(">=9.7.0")
class GradleDslBaseScriptModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    def "fetching a model after a project #typeOfFailure failure fails the build"() {
        given:
        buildFileKts << error

        when:
        fails {
            action(new FetchBaseModelAfterProjectConfigurationAction(ApiType.FETCH))
                .run()
        }

        then:
        def e = thrown(BuildException)
        collectCauseMessages(e).any { it?.contains("broken !!!") }

        where:
        typeOfFailure | error
        "compilation" | "broken !!!"
        "runtime"     | "throw RuntimeException(\"broken !!!\")"
    }

    def "GradleDslBaseScriptModel is still obtained via getModel even after a project #typeOfFailure failure"() {
        given:
        buildFileKts << error

        when:
        def result = succeeds {
            action(new FetchBaseModelAfterProjectConfigurationAction(ApiType.GET_MODEL))
                .run()
        }

        then:
        result.errorsBeforeBaseModel.size() == 1
        result.errorsBeforeBaseModel[0].contains("A problem occurred configuring root project")
        result.baseModel != null
        result.baseModel.groovyDslBaseScriptModel != null
        result.baseModel.kotlinDslBaseScriptModel != null

        where:
        typeOfFailure | error
        "compilation" | "broken !!!"
        "runtime"     | "throw RuntimeException(\"broken !!!\")"
    }

    private static List<String> collectCauseMessages(Throwable throwable) {
        def messages = []
        Throwable current = throwable
        int depth = 0
        while (current != null && depth++ < 50) {
            messages << current.message
            current = current.cause
        }
        return messages
    }

    static class FetchBaseModelAfterProjectConfigurationAction implements BuildAction<FetchBaseModelLastActionResult>, Serializable {

        final ApiType apiType

        FetchBaseModelAfterProjectConfigurationAction(ApiType apiType) {
            this.apiType = apiType
        }

        @Override
        FetchBaseModelLastActionResult execute(BuildController controller) {
            def model
            List<String> failures = []
            if (apiType == ApiType.FETCH) {
                def result = controller.fetch(KotlinDslScriptsModel)
                failures = result.failures.collect { it.message }
                model = controller.fetch(GradleDslBaseScriptModel).model
            } else {
                try {
                    controller.getModel(KotlinDslScriptsModel)
                } catch (Exception e) {
                    failures.add(e.message)
                }
                model = controller.getModel(GradleDslBaseScriptModel)
            }
            return new FetchBaseModelLastActionResult(failures, model)
        }
    }

    static class FetchBaseModelLastActionResult implements Serializable {
        final List<String> errorsBeforeBaseModel
        final GradleDslBaseScriptModel baseModel

        FetchBaseModelLastActionResult(List<String> errorsBeforeBaseModel, GradleDslBaseScriptModel baseModel) {
            this.errorsBeforeBaseModel = errorsBeforeBaseModel
            this.baseModel = baseModel
        }
    }

    enum ApiType {
        FETCH,
        GET_MODEL
    }
}
