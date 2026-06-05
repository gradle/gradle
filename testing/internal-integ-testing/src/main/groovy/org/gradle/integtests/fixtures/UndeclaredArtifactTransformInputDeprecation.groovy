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
    void expectUndeclaredArtifactTransformInputDeprecation() {
        executer.expectDocumentedDeprecationWarning(
            "Querying the output of an artifact transform of a project artifact from a task action without declaring it as a task input has been deprecated. " +
            "This is scheduled to be removed in Gradle 10. " +
            "Declare the FileCollection as a task input (for example via inputs.files(view)) so the transform is wired into the execution plan. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#undeclared_artifact_transform_input"
        )
    }
}
