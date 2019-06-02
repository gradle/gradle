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

import kotlin.reflect.KClass


enum class PropertyKind {
    Field {
        override fun toString() = "field"
    },
    InputProperty {
        override fun toString() = "input property"
    },
    OutputProperty {
        override fun toString() = "output property"
    }
}


fun IsolateContext.logPropertyWarning(action: String, kind: PropertyKind, type: Class<*>, fieldName: String, message: String) {
    logger.warn(
        "instant-execution > task '{}' {} '{}.{}' cannot be {}d because {}.",
        isolate.owner.path, kind, type.name, fieldName, action, message
    )
}


fun IsolateContext.logProperty(action: String, kind: PropertyKind, type: Class<*>, name: String, value: Any?) {
    logger.info(
        "instant-execution > task '{}' {} '{}.{}' {}d value {}",
        isolate.owner.path, kind, type.name, name, action, value
    )
}


fun IsolateContext.logUnsupported(type: KClass<*>) {
    logger.warn("instant-execution > Cannot serialize object of type ${type.java.name} as these are not supported with instant execution.")
}
