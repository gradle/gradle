/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.declarativedsl.defaults

import org.gradle.api.file.ProjectLayout
import org.gradle.declarative.dsl.model.annotations.AccessFromCurrentReceiverOnly
import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.internal.declarativedsl.evaluator.defaults.DefaultsConfiguringBlock


/**
 * Represents a top-level receiver for processing the defaults block in the Settings DSL.
 */
interface DefaultsTopLevelReceiver {
    @Configuring
    @AccessFromCurrentReceiverOnly
    fun defaults(defaults: DefaultsConfiguringBlock.() -> Unit)

    @get:Restricted
    val layout: ProjectLayout
}
