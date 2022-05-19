/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File

import kotlin.reflect.KProperty


/**
 * Property delegate for [Property] instances.
 *
 * Example: `val someProperty by somePropertyState`
 */
operator fun <T> Property<T>.getValue(receiver: Any?, property: KProperty<*>): T = get()


/**
 * Property delegate for [Property] instances.
 *
 * Example: `var someProperty by somePropertyState`
 */
operator fun <T> Property<T>.setValue(receiver: Any?, property: KProperty<*>, value: T) = set(value)

/**
 * Property assign for [Property] instances.
 */
operator fun <T> Property<T>.assign(value: T) = set(value)

/**
 * Property assign for [Property] instances.
 */
operator fun <T> Property<T>.assign(value: Provider<T>) = set(value)

/**
 * Property assign for [File]s to [FileSystemLocationProperty] instances.
 */
operator fun <T : FileSystemLocation> FileSystemLocationProperty<T>.assign(value: File) = set(value)

/**
 * += for [ConfigurableFileCollection] instances.
 */
operator fun ConfigurableFileCollection.plusAssign(other: Any) { from(other) }

/**
 * Assignment for [ConfigurableFileCollection] instances.
 */
operator fun ConfigurableFileCollection.assign(other: Any) = setFrom(other)
