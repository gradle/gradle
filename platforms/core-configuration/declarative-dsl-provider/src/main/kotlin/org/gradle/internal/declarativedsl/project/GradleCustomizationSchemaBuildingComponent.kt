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

import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaComponent
import org.gradle.internal.declarativedsl.evaluationSchema.MinimalEvaluationSchemaComponent
import org.gradle.internal.declarativedsl.evaluationSchema.plus


/**
 * Provides declarative schema building features for a general-purpose Gradle DSL.
 *
 * The features are:
 * * importing properties using the [org.gradle.api.provider.Property] API,
 * * importing types from functions that return or configure custom types.
 */
internal
fun gradleDslGeneralSchemaComponent(): EvaluationSchemaComponent =
    GradlePropertyApiEvaluationSchemaComponent() + /** This should go before [MinimalEvaluationSchemaComponent], as it needs to claim the properties */
        MinimalEvaluationSchemaComponent() +
        TypeDiscoveryFromRestrictedFunctions()
