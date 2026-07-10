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

import org.gradle.api.Action
import org.gradle.internal.declarativedsl.schemaBuilder.ConfigureLambdaHandler
import org.gradle.internal.declarativedsl.schemaBuilder.kotlinFunctionAsConfigureLambda
import org.gradle.internal.declarativedsl.schemaBuilder.plus
import org.gradle.internal.declarativedsl.schemaBuilder.treatInterfaceAsConfigureLambda


/**
 * Defines configuring lambdas for schema building purposes as one of:
 * * Kotlin function types, such as `(Foo) -> Unit` or `Foo.() -> Unit`
 * * Gradle type `Action<in Foo>`
 *
 * This should be the only [ConfigureLambdaHandler] used across Declarative DSL schema building code in Gradle,
 * so that all Declarative DSL schemas are consistent.
 */
internal
val gradleConfigureLambdas: ConfigureLambdaHandler =
    treatInterfaceAsConfigureLambda(Action::class) + kotlinFunctionAsConfigureLambda
