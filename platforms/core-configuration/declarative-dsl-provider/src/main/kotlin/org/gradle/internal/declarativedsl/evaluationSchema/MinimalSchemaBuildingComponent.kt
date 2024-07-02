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

package org.gradle.internal.declarativedsl.evaluationSchema

import org.gradle.internal.declarativedsl.schemaBuilder.DefaultFunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.DefaultPropertyExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.PropertyExtractor


/**
 * Defines a minimal set of features for Declarative DSL evaluation. The only Gradle-related customization in this component is [gradleConfigureLambdas].
 * Besides, no custom Gradle APIs are considered as schema contributors.
 */
class MinimalSchemaBuildingComponent : AnalysisSchemaComponent {
    override fun propertyExtractors(): List<PropertyExtractor> = listOf(DefaultPropertyExtractor())
    override fun functionExtractors(): List<FunctionExtractor> = listOf(DefaultFunctionExtractor(configureLambdas = gradleConfigureLambdas))
}
