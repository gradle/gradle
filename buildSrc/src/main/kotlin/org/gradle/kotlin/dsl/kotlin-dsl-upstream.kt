package org.gradle.kotlin.dsl

import org.gradle.api.Incubating
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty


// This file contains members intended to be pulled upstream into the next Gradle Kotlin DSL release


/**
 * Creates a [ListProperty] that holds a [List] of the given element type [T].
 *
 * @see [ObjectFactory.listProperty]
 */
@Incubating
inline
fun <reified T> ObjectFactory.listProperty(): ListProperty<T> =
    listProperty(T::class.java)
