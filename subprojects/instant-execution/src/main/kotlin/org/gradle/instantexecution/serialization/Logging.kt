/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution.serialization

import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.instantexecution.problems.DocumentationSection
import org.gradle.instantexecution.problems.DocumentationSection.NotYetImplemented
import org.gradle.instantexecution.problems.DocumentationSection.RequirementsDisallowedTypes

import org.gradle.instantexecution.problems.PropertyProblem
import org.gradle.instantexecution.problems.StructuredMessage
import org.gradle.instantexecution.problems.StructuredMessage.Companion.build

import kotlin.reflect.KClass


typealias StructuredMessageBuilder = StructuredMessage.Builder.() -> Unit


fun IsolateContext.logPropertyProblem(
    action: String,
    exception: Throwable? = null,
    documentationSection: DocumentationSection? = null,
    message: StructuredMessageBuilder
) {
    logPropertyProblem(action, PropertyProblem(trace, build(message), exception, documentationSection))
}


fun IsolateContext.logPropertyInfo(action: String, value: Any?) {
    logger.debug("configuration-cache > {}d {} with value {}", action, trace, value)
}


internal
fun IsolateContext.logUnsupported(
    action: String,
    baseType: KClass<*>,
    actualType: Class<*>,
    documentationSection: DocumentationSection = RequirementsDisallowedTypes
) {
    logPropertyProblem(action, PropertyProblem(trace,
        build {
            text("cannot ")
            text(action)
            text(" object of type ")
            reference(GeneratedSubclasses.unpack(actualType))
            text(", a subtype of ")
            reference(baseType)
            text(", as these are not supported with the configuration cache.")
        }, null, documentationSection))
}


internal
fun IsolateContext.logUnsupported(
    action: String,
    baseType: KClass<*>,
    documentationSection: DocumentationSection = RequirementsDisallowedTypes
) {
    logPropertyProblem(action, PropertyProblem(trace,
        build {
            text("cannot ")
            text(action)
            text(" object of type ")
            reference(baseType)
            text(" as these are not supported with the configuration cache.")
        }, null, documentationSection))
}


fun IsolateContext.logNotImplemented(baseType: Class<*>) {
    logPropertyProblem {
        text("objects of type ")
        reference(baseType)
        text(" are not yet supported with the configuration cache.")
    }
}


internal
fun IsolateContext.logNotImplemented(feature: String, documentationSection: DocumentationSection = NotYetImplemented) {
    onProblem(PropertyProblem(trace, build {
        text("support for $feature is not yet implemented with the configuration cache.")
    }, null, documentationSection))
}


private
fun IsolateContext.logPropertyProblem(documentationSection: DocumentationSection? = null, message: StructuredMessageBuilder) {
    val problem = PropertyProblem(trace, build(message), null, documentationSection)
    logPropertyProblem("serialize", problem)
}


private
fun IsolateContext.logPropertyProblem(action: String, problem: PropertyProblem) {
    logger.debug("configuration-cache > failed to {} {} because {}", action, problem.trace, problem.message)
    onProblem(problem)
}


internal
inline fun <T : WriteContext, R> T.withDebugFrame(name: () -> String, writeAction: T.() -> R): R {
    val frameName: String? = if (logger.isDebugEnabled) name() else null
    try {
        frameName?.let {
            logger.debug("[{}] {\"type\":\"O\",\"frame\":\"{}\",\"at\":{}}", category, it, writePosition)
        }
        return writeAction()
    } finally {
        frameName?.let {
            logger.debug("[{}] {\"type\":\"C\",\"frame\":\"{}\",\"at\":{}}", category, it, writePosition)
        }
    }
}
