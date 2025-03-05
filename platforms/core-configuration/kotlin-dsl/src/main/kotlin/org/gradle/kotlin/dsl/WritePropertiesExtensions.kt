/*
 * Copyright 2024 the original author or authors.
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
@file:Incubating

package org.gradle.kotlin.dsl

import org.gradle.api.Incubating
import org.gradle.api.internal.provider.ProviderApiDeprecationLogger
import org.gradle.api.tasks.WriteProperties

/**
 * Backward-compatibility for {@link org.gradle.api.tasks.WriteProperties#setProperties(Map)}
 * @since 9.0
 */
@Incubating
@Suppress("NOTHING_TO_INLINE")
@Deprecated("Use properties property instead")
inline fun WriteProperties.setProperties(vararg properties: Pair<String, Any?>) {
    ProviderApiDeprecationLogger.logDeprecation(WriteProperties::class.java, "setProperties(kotlin.Pair[])", "properties")
    this.properties.set(properties.toMap())
}
