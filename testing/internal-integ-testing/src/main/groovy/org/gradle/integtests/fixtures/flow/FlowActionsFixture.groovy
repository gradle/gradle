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

package org.gradle.integtests.fixtures.flow

import groovy.transform.SelfType
import org.gradle.api.flow.BuildWorkResult
import org.gradle.api.flow.FlowAction
import org.gradle.api.flow.FlowParameters
import org.gradle.api.flow.FlowProviders
import org.gradle.api.flow.FlowScope
import org.gradle.api.internal.GradleInternal
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage

import java.util.concurrent.atomic.AtomicInteger


/**
 * Helper class to define simple {@code buildFinished}-like FlowActions.
 */
@SelfType(AbstractIntegrationSpec)
trait FlowActionsFixture {
    private final AtomicInteger buildFinishedCount = new AtomicInteger()

    /**
     * Builds a simple flow action that doesn't capture anything from the environment except the build result itself.
     * This method builds Groovy DSL code.
     *
     * @param buildFinished the implementation of the callback
     * @param resultVar the variable in which {@link BuildWorkResult} is stored
     * @return the Groovy DSL snippet that register a flow action
     */
    String buildFinishedFlowAction(@GroovyBuildScriptLanguage buildFinished, String resultVar = "result") {
        def actionClassName = "BuildFinished${buildFinishedCount.getAndIncrement()}"
        """
        abstract class $actionClassName implements ${FlowAction.name}<${actionClassName}.Params> {
            interface Params extends ${FlowParameters.name} {
                @${Input.name}
                ${Property.name}<${BuildWorkResult.name}> getBuildResult()

                @${Input.name}
                org.gradle.api.provider.Property<String> getBuildPath()
            }

            @Override
            public void execute(Params params) {
                def $resultVar = params.buildResult.get()
                def buildPath = params.buildPath.get()
                ${buildFinished}
            }
        }

        def gradleServices = (gradle as ${GradleInternal.name}).services
        gradleServices.get(${FlowScope.name}).always($actionClassName, {
            parameters {
                buildResult = gradleServices.get(${FlowProviders.name}).buildWorkResult
                buildPath = gradle.buildPath
            }
        })
        """
    }

    /**
     * Builds a {@link org.gradle.api.invocation.Gradle#buildFinished(groovy.lang.Closure)} callback implementation.
     * To make the implementation compatible with FlowAction, wraps the result into {@link BuildWorkResult}
     * This method builds Groovy DSL code.
     *
     * @param buildFinished the implementation of the callback
     * @param resultVar the variable in which {@link BuildWorkResult} is stored
     * @return the Groovy DSL snippet that registers a callback
     */
    String legacyBuildFinishedCallback(@GroovyBuildScriptLanguage buildFinished, String resultVar = "result") {
        """
        gradle.buildFinished { originalResult ->
            def $resultVar = new ${BuildWorkResult.name}() {
                @Override
                ${Optional.name}<Throwable> getFailure() { ${Optional.name}.ofNullable(originalResult.failure) }
            }

            ${buildFinished}
        }
        """
    }

    /**
     * Builds a {@link org.gradle.api.invocation.Gradle#addBuildListener(org.gradle.BuildListener buildListener)} callback implementation.
     * This method builds Groovy DSL code.
     *
     * @param buildFinished the implementation of the callback
     * @param resultVar the variable in which {@link BuildWorkResult} is stored
     * @return the Groovy DSL snippet that registers a callback
     */
    String listenerBuildFinishedCallback(@GroovyBuildScriptLanguage buildFinished, String resultVar = "result") {
        """
        gradle.addBuildListener(new LoggingBuildListener(gradle.buildPath))
        class LoggingBuildListener extends BuildAdapter {
            private final String buildPath

            LoggingBuildListener(String buildPath) {
                this.buildPath = buildPath
            }

            void buildFinished(BuildResult originalResult) {
                def $resultVar = new ${BuildWorkResult.name}() {
                    @Override
                    ${Optional.name}<Throwable> getFailure() { ${Optional.name}.ofNullable(originalResult.failure) }
                }

                $buildFinished
            }
        }
        """
    }

    enum BuildFinishCallbackType {
        FLOW_ACTION,
        BUILD_FINISHED,
        BUILD_LISTENER,
    }

    static List<BuildFinishCallbackType> buildFinishCallbackTypes() {
        return BuildFinishCallbackType.values()
    }

    /**
     * Helper that builds an appropriate snippet with {@link #buildFinishedFlowAction(java.lang.Object)} or {@link #legacyBuildFinishedCallback(java.lang.Object)}
     * depending on the provided callback type enum.
     * This method builds Groovy DSL code.
     *
     * @param callbackType the type of the callback
     * @param buildFinished the implementation of the callback
     * @param resultVar the variable in which {@link BuildWorkResult} is stored
     * @return the Groovy DSL snippet that registers a callback
     */
    String buildFinishCallback(BuildFinishCallbackType callbackType, @GroovyBuildScriptLanguage buildFinished, String resultVar = "result") {
        switch (callbackType) {
            case BuildFinishCallbackType.FLOW_ACTION -> buildFinishedFlowAction(buildFinished)
            case BuildFinishCallbackType.BUILD_FINISHED -> legacyBuildFinishedCallback(buildFinished)
            case BuildFinishCallbackType.BUILD_LISTENER -> listenerBuildFinishedCallback(buildFinished)
            default -> throw new IllegalArgumentException("Unexpected callback type $callbackType")
        }
    }
}
