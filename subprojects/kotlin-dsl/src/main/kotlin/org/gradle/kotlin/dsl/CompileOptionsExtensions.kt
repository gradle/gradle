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

import org.gradle.api.provider.Property
import org.gradle.api.tasks.compile.CompileOptions

/**
 * TODO: Migrate to a special project
 */
val CompileOptions.isFailOnError: Property<Boolean>
    get() = this.failOnError

val CompileOptions.isVerbose: Property<Boolean>
    get() = this.verbose

val CompileOptions.isListFiles: Property<Boolean>
    get() = this.listFiles

val CompileOptions.isDeprecation: Property<Boolean>
    get() = this.deprecation

val CompileOptions.isWarnings: Property<Boolean>
    get() = this.warnings

val CompileOptions.isDebug: Property<Boolean>
    get() = this.debug

val CompileOptions.isFork: Property<Boolean>
    get() = this.fork

val CompileOptions.isIncremental: Property<Boolean>
    get() = this.incremental
