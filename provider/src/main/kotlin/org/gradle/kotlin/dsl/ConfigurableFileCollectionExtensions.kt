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

import kotlin.reflect.KProperty


/**
 * Property delegate for [ConfigurableFileCollection] instances.
 *
 * Example: `val aFileCollection by project.files()`
 */
operator fun ConfigurableFileCollection.getValue(receiver: Any?, property: KProperty<*>): ConfigurableFileCollection =
    this


/**
 * Property delegate for [ConfigurableFileCollection] instances.
 *
 * Example: `var aFileCollection by project.files()`
 */
operator fun ConfigurableFileCollection.setValue(receiver: Any?, property: KProperty<*>, value: Iterable<*>) =
    setFrom(value)
