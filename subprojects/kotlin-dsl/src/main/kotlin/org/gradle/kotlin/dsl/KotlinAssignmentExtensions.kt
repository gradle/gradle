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

/**
 * @since 8.1
 */
@file:Incubating
package org.gradle.kotlin.dsl

import org.gradle.api.Incubating
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.provider.HasMultipleValues
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.util.internal.IncubationLogger
import java.io.File


/**
 * Assign value: T to a property with assign operator
 *
 * @since 8.1
 */
@Incubating
fun <T> Property<T>.assign(value: T?) {
    emitIncubatingLogMessage()
    this.set(value)
}


/**
 * Assign value: Provider<T> to a property with assign operator
 *
 * @since 8.1
 */
@Incubating
fun <T> Property<T>.assign(value: Provider<out T?>) {
    emitIncubatingLogMessage()
    this.set(value)
}


/**
 * Assign file to a FileSystemLocationProperty with assign operator
 *
 * @since 8.1
 */
@Incubating
fun <T : FileSystemLocation> FileSystemLocationProperty<T>.assign(file: File?) {
    emitIncubatingLogMessage()
    this.set(file)
}


/**
 * Assign file provided by a Provider to a FileSystemLocationProperty with assign operator
 *
 * @since 8.1
 */
@Incubating
fun <T : FileSystemLocation> FileSystemLocationProperty<T>.assign(provider: Provider<File?>) {
    emitIncubatingLogMessage()
    this.fileProvider(provider)
}


/**
 * Sets the value of the property to the elements of the given iterable, and replaces any existing value
 *
 * @since 8.1
 */
@Incubating
fun <T> HasMultipleValues<T>.assign(elements: Iterable<T?>?) {
    emitIncubatingLogMessage()
    this.set(elements)
}


/**
 * Sets the property to have the same value of the given provider, and replaces any existing value
 *
 * @since 8.1
 */
@Incubating
fun <T> HasMultipleValues<T>.assign(provider: Provider<out Iterable<T?>?>) {
    emitIncubatingLogMessage()
    this.set(provider)
}


/**
 * Sets the value of this property to the entries of the given Map, and replaces any existing value
 *
 * @since 8.1
 */
@Incubating
fun <K, V> MapProperty<K, V>.assign(entries: Map<out K?, V?>?) {
    emitIncubatingLogMessage()
    this.set(entries)
}


/**
 * Sets the property to have the same value of the given provider, and replaces any existing value
 *
 * @since 8.1
 */
@Incubating
fun <K, V> MapProperty<K, V>.assign(provider: Provider<out Map<out K?, V?>?>) {
    emitIncubatingLogMessage()
    this.set(provider)
}


/**
 * Sets the ConfigurableFileCollection to contain the source paths of passed collection.
 * This is the same as calling ConfigurableFileCollection.setFrom(fileCollection).
 *
 * @since 8.1
 */
@Incubating
fun ConfigurableFileCollection.assign(fileCollection: FileCollection) {
    emitIncubatingLogMessage()
    this.setFrom(fileCollection)
}


private
fun emitIncubatingLogMessage() = IncubationLogger.incubatingFeatureUsed("Kotlin DSL property assignment")
