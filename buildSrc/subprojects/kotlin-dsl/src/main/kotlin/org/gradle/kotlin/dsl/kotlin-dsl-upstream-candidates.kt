// This file contains members that we should consider to pull upstream
// and make available to all Kotlin DSL users
package org.gradle.kotlin.dsl

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import java.io.File
import java.util.concurrent.Callable


/**
 * `dir / "sub"` is the same as `dir.resolve("sub")`.
 *
 * @see [File.resolve]
 */
operator fun File.div(child: String): File =
    resolve(child)


/**
 * Defers the evaluation of an expression until its value is required by the consumer.
 */
fun <T> deferred(value: () -> T): Any =
    Callable { value() }


/**
 * See [ObjectFactory.named].
 */
inline fun <reified T : Named> ObjectFactory.named(name: String): T =
    named(T::class.java, name)


fun Project.stringPropertyOrNull(projectPropertyName: String): String? =
    project.findProperty(projectPropertyName) as? String


fun Project.selectStringProperties(vararg propertyNames: String): Map<String, String> =
    propertyNames.mapNotNull { propertyName ->
        stringPropertyOrNull(propertyName)?.let { propertyValue ->
            propertyName to propertyValue
        }
    }.toMap()
