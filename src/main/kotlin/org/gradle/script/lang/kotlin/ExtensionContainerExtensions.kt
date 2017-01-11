/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.script.lang.kotlin

import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.plugins.ExtensionContainer

import kotlin.reflect.KProperty


/**
 * Looks for the extension of a given name. If none found it will throw an exception.
 *
 * @param name extension name
 * @return extension
 * @throws UnknownDomainObjectException When the given extension is not found.
 *
 * @see ExtensionContainer.getByName
 */
operator fun ExtensionContainer.get(name: String): Any =
    getByName(name)


inline operator fun <reified T : Any> ExtensionContainer.getValue(thisRef: Any?, property: KProperty<*>): T =
    getByName(property.name).let {
        it as? T
            ?: throw IllegalStateException(
                "Element '${property.name}' of type '${it.javaClass.name}' from container '$this' cannot be cast to '${T::class.qualifiedName}'.")
    }
