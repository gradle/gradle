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

package org.gradle.declarative.dsl.evaluation

import org.gradle.declarative.dsl.evaluation.InterpretationStepFeature.DocumentChecks
import org.gradle.declarative.dsl.evaluation.InterpretationStepFeature.ResolutionResultPostprocessing
import org.gradle.declarative.dsl.evaluation.InterpretationStepFeature.ResolutionResultPostprocessing.ApplyModelDefaults
import org.gradle.declarative.dsl.evaluation.InterpretationStepFeature.ResolutionResultPostprocessing.DefineModelDefaults
import org.gradle.declarative.dsl.evaluation.InterpretationStepFeature.RunsBeforeClassScopeIsReady
import org.gradle.tooling.ToolingModelContract
import java.io.Serializable


@ToolingModelContract(subTypes = [
    DocumentChecks::class,
    RunsBeforeClassScopeIsReady::class,
    ResolutionResultPostprocessing::class,
        DefineModelDefaults::class,
        ApplyModelDefaults::class
])
sealed interface InterpretationStepFeature : Serializable {
    interface DocumentChecks : InterpretationStepFeature {
        val checkKeys: Iterable<String>
    }

    interface RunsBeforeClassScopeIsReady : InterpretationStepFeature

    sealed interface ResolutionResultPostprocessing : InterpretationStepFeature {
        interface DefineModelDefaults : ResolutionResultPostprocessing
        interface ApplyModelDefaults : ResolutionResultPostprocessing
    }
}
