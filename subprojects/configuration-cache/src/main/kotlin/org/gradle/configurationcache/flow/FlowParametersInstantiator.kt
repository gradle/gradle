/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache.flow

import org.gradle.api.flow.FlowParameters
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.tasks.properties.InspectionSchemeFactory
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.properties.PropertyValue
import org.gradle.internal.properties.PropertyVisitor
import org.gradle.internal.reflect.ProblemRecordingTypeValidationContext
import org.gradle.internal.reflect.problems.ValidationProblemId
import org.gradle.internal.reflect.validation.TypeValidationProblem
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import java.util.Optional


@ServiceScope(Scopes.Build::class)
internal
class FlowParametersInstantiator(
    inspectionSchemeFactory: InspectionSchemeFactory,
    instantiatorFactory: InstantiatorFactory,
    services: ServiceRegistry
) {
    fun <P : FlowParameters> newInstance(parametersType: Class<P>, configure: (P) -> Unit): P {
        return instantiator.newInstance(parametersType).also {
            configure(it)
            validate(parametersType, it)
        }
    }

    private
    fun <P : FlowParameters> validate(type: Class<P>, parameters: P) {
        inspection.propertyWalker.visitProperties(
            parameters,
            object : ProblemRecordingTypeValidationContext(DocumentationRegistry(), type, { Optional.empty() }) {
                override fun recordProblem(problem: TypeValidationProblem) {
                    if (problem.id != ValidationProblemId.MISSING_ANNOTATION) {
                        TODO("${where(problem)}: ${problem.shortDescription}")
                    }
                }
            },
            object : PropertyVisitor {
                override fun visitServiceReference(propertyName: String, optional: Boolean, value: PropertyValue, serviceName: String?) {
                    value.maybeFinalizeValue()
                }

                override fun visitInputProperty(propertyName: String, value: PropertyValue, optional: Boolean) {
                }
            }
        )
    }

    private
    val instantiator by lazy {
        instantiatorFactory.decorateScheme().withServices(services).instantiator()
    }

    private
    val inspection by lazy {
        inspectionSchemeFactory.inspectionScheme(
            listOf(
                Input::class.java,
                ServiceReference::class.java,
            ),
            listOf(
                org.gradle.api.tasks.Optional::class.java
            ),
            instantiatorFactory.decorateScheme()
        )
    }

    private
    fun where(problem: TypeValidationProblem): String = problem.where.run {
        type.map { type ->
            propertyName.map { propName ->
                "${type.name}.$propName"
            }.orElse(type.name)
        }.orElse("unknown location")
    }
}
