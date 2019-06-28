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
import kotlin.reflect.KClass


fun IsolateContext.logPropertyWarning(action: String, message: String) {
    logPropertyFailure(action, PropertyFailure.Warning(trace, message))
}


fun IsolateContext.logPropertyError(action: String, message: String, error: Throwable) {
    logPropertyFailure(action, PropertyFailure.Error(trace, message, error))
}


fun IsolateContext.logPropertyFailure(action: String, failure: PropertyFailure) {
    logger.debug("instant-execution > failed to {} {} because {}", action, failure.trace, failure.message)
    failures.add(failure)
}


fun IsolateContext.logPropertyInfo(action: String, value: Any?) {
    logger.info("instant-execution > {}d {} with value {}", action, trace, value)
}


fun IsolateContext.logUnsupported(baseType: KClass<*>, actualType: Class<*>) {
    logPropertyWarning("cannot serialize object of type '${GeneratedSubclasses.unpack(actualType).name}', a subtype of '${baseType.qualifiedName}', as these are not supported with instant execution.")
}


fun IsolateContext.logUnsupported(baseType: KClass<*>) {
    logPropertyWarning("cannot serialize object of type '${baseType.qualifiedName}' as these are not supported with instant execution.")
}


private
fun IsolateContext.logPropertyWarning(message: String) {
    val failure = PropertyFailure.Warning(trace, message)
    failures.add(failure)
    logger.warn("instant-execution > {}", failure.message)
}
