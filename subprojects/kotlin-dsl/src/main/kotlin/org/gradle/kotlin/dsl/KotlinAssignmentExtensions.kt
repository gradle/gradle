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
 * @since 8.0
 */
@file:Incubating
package org.gradle.kotlin.dsl

import org.gradle.api.Incubating
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File


/**
 * Assign value: T to a property with assign operator
 *
 * @since 8.0
 */
@Incubating
fun <T> Property<T>.assign(value: T) = this.set(value)


/**
 * Assign value: Provider<T> to a property with assign operator
 *
 * @since 8.0
 */
@Incubating
fun <T> Property<T>.assign(value: Provider<T>) = this.set(value)


/**
 * Assign file to a FileSystemLocationProperty with assign operator
 *
 * @since 8.0
 */
@Incubating
fun <T : FileSystemLocation> FileSystemLocationProperty<T>.assign(file: File) = this.set(file)
