@file:Incubating

package org.gradle.kotlin.dsl

import org.gradle.api.Incubating
import org.gradle.api.Named
import org.gradle.api.attributes.AttributeContainer

/**
 * Creates a simple immutable [Named] object of the given type and name.
 *
 * @param T The type of object to create
 * @param name The name of the created object
 * @return the created named object
 *
 * @see [AttributeContainer.named]
 *
 * @since 9.3.0
 */
@Incubating
inline fun <reified T : Named> AttributeContainer.named(name: String): T =
    named(T::class.java, name)
