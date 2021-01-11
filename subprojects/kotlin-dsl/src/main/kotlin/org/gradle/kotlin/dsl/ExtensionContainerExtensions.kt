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

package org.gradle.kotlin.dsl

import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.plugins.ExtensionContainer

import kotlin.reflect.KProperty


/**
 * Looks for the extension of a given name. If none found it will throw an exception.
 *
 * @param name extension name
 * @return extension
 * @throws [UnknownDomainObjectException] When the given extension is not found.
 *
 * @see [ExtensionContainer.getByName]
 */
operator fun ExtensionContainer.get(name: String): Any =
    getByName(name)


/**
 * Looks for the extension of a given name and casts it to the expected type [T].
 *
 * If none found it will throw an [UnknownDomainObjectException].
 * If the extension is found but cannot be cast to the expected type it will throw an [IllegalStateException].
 *
 * @param name extension name
 * @return extension, never null
 * @throws [UnknownDomainObjectException] When the given extension is not found.
 * @throws [IllegalStateException] When the given extension cannot be cast to the expected type.
 */
@Suppress("extension_shadowed_by_member")
inline fun <reified T : Any> ExtensionContainer.getByName(name: String) =
    getByName(name).let {
        it as? T
            ?: throw IllegalStateException(
                "Element '$name' of type '${it::class.java.name}' from container '$this' cannot be cast to '${T::class.qualifiedName}'."
            )
    }


/**
 * Delegated property getter that locates extensions.
 */
inline operator fun <reified T : Any> ExtensionContainer.getValue(thisRef: Any?, property: KProperty<*>): T =
    getByName<T>(property.name)


/**
 * Adds a new extension to this container.
 *
 * @param T the public type of the added extension
 * @param name the name of the extension
 * @param extension the extension instance
 *
 * @throws IllegalArgumentException When an extension with the given name already exists.
 *
 * @see [ExtensionContainer.add]
 * @since 5.0
 */
@Suppress("extension_shadowed_by_member")
inline fun <reified T : Any> ExtensionContainer.add(name: String, extension: T) {
    add(typeOf<T>(), name, extension)
}


/**
 * Creates and adds a new extension to this container.
 *
 * @param T the instance type of the new extension
 * @param name the extension's name
 * @param constructionArguments construction arguments
 * @return the created instance
 *
 * @see [ExtensionContainer.create]
 * @since 5.0
 */
inline fun <reified T : Any> ExtensionContainer.create(name: String, vararg constructionArguments: Any): T =
    create(name, T::class.java, *constructionArguments)


/**
 * Looks for the extension of a given type.
 *
 * @param T the extension type
 * @return the extension
 * @throws UnknownDomainObjectException when no matching extension can be found
 *
 * @see [ExtensionContainer.getByType]
 * @since 5.0
 */
inline fun <reified T : Any> ExtensionContainer.getByType(): T =
    getByType(typeOf<T>())


/**
 * Looks for the extension of a given type.
 *
 * @param T the extension type
 * @return the extension or null if not found
 *
 * @see [ExtensionContainer.findByType]
 * @since 5.0
 */
inline fun <reified T : Any> ExtensionContainer.findByType(): T? =
    findByType(typeOf<T>())


/**
 * Looks for the extension of the specified type and configures it with the supplied action.
 *
 * @param T the extension type
 * @param action the configuration action
 *
 * @see [ExtensionContainer.configure]
 * @since 5.0
 */
inline fun <reified T : Any> ExtensionContainer.configure(noinline action: T.() -> Unit) {
    configure(typeOf<T>(), action)
}
