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

package org.gradle.internal.flow.services

import com.google.common.collect.ImmutableList
import org.gradle.api.flow.FlowParameters
import org.gradle.api.internal.tasks.AbstractTaskDependencyResolveContext
import org.gradle.api.internal.tasks.properties.InspectionSchemeFactory
import org.gradle.api.problems.Problem
import org.gradle.api.problems.ProblemReporter
import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.GradleCoreProblemGroup
import org.gradle.api.problems.internal.InternalProblems
import org.gradle.api.services.BuildService
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.properties.PropertyValue
import org.gradle.internal.properties.PropertyVisitor
import org.gradle.internal.reflect.DefaultTypeValidationContext
import org.gradle.internal.reflect.ProblemRecordingTypeValidationContext
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.util.Optional


@ServiceScope(Scope.Build::class)
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
        val problems = ImmutableList.builder<Problem>()
        inspection.propertyWalker.visitProperties(
            parameters,
            object : ProblemRecordingTypeValidationContext(type, { Optional.empty() }, problemsService) {
                override fun recordProblem(problem: Problem) {
                    problems.add(problem)
                }
            },
            object : PropertyVisitor {
                override fun visitServiceReference(propertyName: String, optional: Boolean, value: PropertyValue, serviceName: String?, buildServiceType: Class<out BuildService<*>>) {
                    value.maybeFinalizeValue()
                }

                override fun visitInputProperty(propertyName: String, value: PropertyValue, optional: Boolean) {
                    value.taskDependencies.visitDependencies(
                        object : AbstractTaskDependencyResolveContext() {
                            override fun add(dependency: Any) {
                                problems.add(
                                    problemReporter.create {
                                        id("invalid-dependency", "Property cannot carry dependency", GradleCoreProblemGroup.validation().property())
                                        contextualLabel("Property '$propertyName' cannot carry a dependency on $dependency as these are not yet supported.")
                                        severity(Severity.ERROR)
                                    }
                                )
                            }
                        }
                    )
                }
            }
        )
        DefaultTypeValidationContext.throwOnProblemsOf(type, problems.build())
    }

    private
    val problemReporter: ProblemReporter
        get() = problemsService.reporter

    private
    val instantiator by lazy {
        instantiatorFactory.decorateScheme().withServices(services).instantiator()
    }

    private
    val problemsService = services.get(InternalProblems::class.java)

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
            emptyList(),
            instantiatorFactory.decorateScheme()
        )
    }
}
