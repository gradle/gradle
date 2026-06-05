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

package org.gradle.integtests.fixtures

import groovy.transform.SelfType

/**
 * Apply this trait to tests that emit the deprecation introduced for issue #37219
 * when an artifact transform output of a project artifact is queried from a task
 * action without being declared as a task input.
 *
 * <p>See {@code org.gradle.api.internal.artifacts.transform.TransformedProjectArtifactSet#nagIfUndeclared}
 * for the production emitter. The expected message string here must stay in sync with
 * the {@code DeprecationLogger.deprecate(...).willBeRemovedInGradle10()} call there.
 */
@SelfType(HasGradleExecutor)
trait UndeclaredArtifactTransformInputDeprecation {
    /**
     * Expect the deprecation without contextual task/configuration detail.
     * Use when the offending transform has no resolvable configuration identity
     * (e.g. ad-hoc artifact views).
     */
    void expectUndeclaredArtifactTransformInputDeprecation() {
        executer.expectDocumentedDeprecationWarning(deprecationSummary() + " " + deprecationAdvice())
    }

    /**
     * Expect the deprecation with the contextual line identifying the offending
     * task and configuration.
     *
     * @param taskPath full task path, e.g. ":consumer:resolve"
     * @param configName name of the configuration being queried, e.g. "implementation"
     */
    void expectUndeclaredArtifactTransformInputDeprecation(String taskPath, String configName) {
        executer.expectDocumentedDeprecationWarning(
            deprecationSummary() + " " +
                "Task '${taskPath}' queried artifact transform output of configuration '${configName}' without declaring it as an input. " +
                deprecationAdvice()
        )
    }

    private static String deprecationSummary() {
        "Querying the output of an artifact transform from a task action without declaring it as a task input has been deprecated. " +
            "This is scheduled to be removed in Gradle 10."
    }

    private static String deprecationAdvice() {
        "Declare the files or artifacts produced by the configuration using the transform as a task input to properly wire it into the execution plan. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#undeclared_artifact_transform_input"
    }
}
