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

package org.gradle.internal.declarativedsl.project

import org.gradle.internal.declarativedsl.common.UnsupportedSyntaxFeatureCheck
import org.gradle.internal.declarativedsl.evaluationSchema.SimpleInterpretationSequenceStepWithConversion
import org.gradle.internal.declarativedsl.evaluator.defaults.ApplyModelDefaults
import org.gradle.plugin.software.internal.SoftwareTypeRegistry


/**
 * A step in the interpretation sequence that processes the project build file and applies Software Type conventions
 * configured in the Settings DSL.
 */
internal
fun projectInterpretationSequenceStep(
    softwareTypeRegistry: SoftwareTypeRegistry,
) = SimpleInterpretationSequenceStepWithConversion(
    "project",
    features = setOf(ApplyModelDefaults(), UnsupportedSyntaxFeatureCheck.feature),
) {
    projectEvaluationSchema(softwareTypeRegistry)
}
