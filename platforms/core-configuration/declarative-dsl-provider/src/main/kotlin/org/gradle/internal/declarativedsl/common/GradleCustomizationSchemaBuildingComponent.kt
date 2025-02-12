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

package org.gradle.internal.declarativedsl.common

import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluationSchema.MinimalSchemaBuildingComponent
import org.gradle.internal.declarativedsl.evaluationSchema.ObjectConversionComponent
import org.gradle.internal.declarativedsl.evaluationSchema.gradleConfigureLambdas
import org.gradle.internal.declarativedsl.evaluationSchema.ifConversionSupported
import org.gradle.internal.declarativedsl.mappingToJvm.DefaultRuntimeFunctionCandidatesProvider
import org.gradle.internal.declarativedsl.mappingToJvm.DefaultRuntimeFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.ReflectionRuntimePropertyResolver
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimePropertyResolver
import org.gradle.internal.declarativedsl.ndoc.namedDomainObjectContainers


/**
 * Provides declarative schema building features for a general-purpose Gradle DSL.
 *
 * The features are:
 * * importing properties using the [org.gradle.api.provider.Property] API,
 * * importing types from functions that return or configure custom types.
 * * for every type included in the schema, importing all supertypes that might potentially be declarative.
 * * support for [org.gradle.api.NamedDomainObjectContainer]: configuring functions from properties, and element factories.
 * * the top-level standard library functions that should be available by default in DCL
 *
 * If object conversion is supported by the schema, also brings the basic DCL conversion capabilities
 * for resolving properties and member functions, see [conversionSupport]
 */
internal
fun EvaluationSchemaBuilder.gradleDslGeneralSchema() {
    /** This should go before [MinimalSchemaBuildingComponent], as it needs to claim the properties */
    registerAnalysisSchemaComponent(GradlePropertyApiAnalysisSchemaComponent())

    registerAnalysisSchemaComponent(MinimalSchemaBuildingComponent())

    registerAnalysisSchemaComponent(TypeDiscoveryFromRestrictedFunctions())

    registerAnalysisSchemaComponent(SupertypeTypeDiscovery())

    registerAnalysisSchemaComponent(StandardLibraryComponent)

    ifConversionSupported {
        /** This should go before the default runtime function resolution implementation by [conversionSupport] in order to intercept the calls to the intrinsics. */
        registerObjectConversionComponent(StandardLibraryComponent)

        registerObjectConversionComponent(conversionSupport)
    }

    namedDomainObjectContainers()
}


/**
 * Adds DCL-to-JVM object conversion support capabilities to the resulting evaluation schema.
 */
private
val conversionSupport: ObjectConversionComponent = object : ObjectConversionComponent {
    override fun runtimePropertyResolvers(): List<RuntimePropertyResolver> = listOf(ReflectionRuntimePropertyResolver)
    override fun runtimeFunctionResolvers(): List<RuntimeFunctionResolver> = listOf(DefaultRuntimeFunctionResolver(gradleConfigureLambdas, DefaultRuntimeFunctionCandidatesProvider))
}
