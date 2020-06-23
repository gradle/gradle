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

import org.gradle.instantexecution.problems.PropertyProblem
import org.gradle.instantexecution.problems.StructuredMessage
import org.gradle.instantexecution.problems.StructuredMessage.Companion.build

import kotlin.reflect.KClass


typealias StructuredMessageBuilder = StructuredMessage.Builder.() -> Unit


fun IsolateContext.logPropertyProblem(action: String, exception: Throwable? = null, message: StructuredMessageBuilder) {
    logPropertyProblem(action, PropertyProblem(trace, build(message), exception))
}


fun IsolateContext.logPropertyInfo(action: String, value: Any?) {
    logger.debug("configuration-cache > {}d {} with value {}", action, trace, value)
}


fun IsolateContext.logUnsupported(
    action: String,
    baseType: KClass<*>,
    actualType: Class<*>,
    documentationSection: String = "config_cache:requirements:disallowed_types"
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


fun IsolateContext.logUnsupported(
    action: String,
    baseType: KClass<*>,
    documentationSection: String = "config_cache:requirements:disallowed_types"
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


fun IsolateContext.logNotImplemented(feature: String, documentationSection: String = "config_cache:not_yet_implemented") {
    onProblem(PropertyProblem(trace, build {
        text("support for $feature is not yet implemented with the configuration cache.")
    }, null, documentationSection))
}


private
fun IsolateContext.logPropertyProblem(documentationSection: String? = null, message: StructuredMessageBuilder) {
    val problem = PropertyProblem(trace, build(message), null, documentationSection)
    logPropertyProblem("serialize", problem)
}


private
fun IsolateContext.logPropertyProblem(action: String, problem: PropertyProblem) {
    logger.debug("configuration-cache > failed to {} {} because {}", action, problem.trace, problem.message)
    onProblem(problem)
}
