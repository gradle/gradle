/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.provider.HasMultipleValues
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File


/**
 * Assign value: T to a property with assign operator
 *
 * @since 8.2
 */
fun <T> Property<T>.assign(value: T?) {
    this.set(value)
}


/**
 * Assign value: Provider<T> to a property with assign operator
 *
 * @since 8.2
 */
fun <T> Property<T>.assign(value: Provider<out T?>) {
    this.set(value)
}


/**
 * Assign file to a FileSystemLocationProperty with assign operator
 *
 * @since 8.2
 */
fun <T : FileSystemLocation> FileSystemLocationProperty<T>.assign(file: File?) {
    this.set(file)
}


/**
 * Assign file provided by a Provider to a FileSystemLocationProperty with assign operator
 *
 * @since 8.2
 */
fun <T : FileSystemLocation> FileSystemLocationProperty<T>.assign(provider: Provider<File?>) {
    this.fileProvider(provider)
}


/**
 * Sets the value of the property to the elements of the given iterable, and replaces any existing value
 *
 * @since 8.2
 */
fun <T> HasMultipleValues<T>.assign(elements: Iterable<T?>?) {
    this.set(elements)
}


/**
 * Sets the property to have the same value of the given provider, and replaces any existing value
 *
 * @since 8.2
 */
fun <T> HasMultipleValues<T>.assign(provider: Provider<out Iterable<T?>?>) {
    this.set(provider)
}


/**
 * Sets the value of this property to the entries of the given Map, and replaces any existing value
 *
 * @since 8.2
 */
fun <K, V> MapProperty<K, V>.assign(entries: Map<out K?, V?>?) {
    this.set(entries)
}


/**
 * Sets the property to have the same value of the given provider, and replaces any existing value
 *
 * @since 8.2
 */
fun <K, V> MapProperty<K, V>.assign(provider: Provider<out Map<out K?, V?>?>) {
    this.set(provider)
}
