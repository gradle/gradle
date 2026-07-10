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
import org.gradle.api.file.FileCollection
import org.gradle.internal.deprecation.DeprecationLogger
import kotlin.reflect.KProperty


/**
 * Property delegate for [ConfigurableFileCollection] instances.
 *
 * Example: `val aFileCollection by project.files()`
 */
@Deprecated("Use 'val files = configurableFileCollection.getFiles()' instead. See the Gradle 9.6 upgrading guide.")
operator fun ConfigurableFileCollection.getValue(receiver: Any?, property: KProperty<*>): ConfigurableFileCollection {
    DeprecationLogger.deprecate("The 'val files by configurableFileCollection' property delegate syntax")
        .withAdvice("Use 'val files = configurableFileCollection.getFiles()' instead.")
        .willBeRemovedInGradle10()
        .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
        .nagUser()
    return this
}


/**
 * Property delegate for [ConfigurableFileCollection] instances.
 *
 * Example: `var aFileCollection by project.files()`
 */
@Deprecated("Use 'configurableFileCollection.setFrom(...)' instead. See the Gradle 9.6 upgrading guide.")
operator fun ConfigurableFileCollection.setValue(receiver: Any?, property: KProperty<*>, value: Iterable<*>) {
    DeprecationLogger.deprecate("The 'val files by configurableFileCollection; files = ...' property delegate syntax")
        .withAdvice("Use 'configurableFileCollection.setFrom(...)' instead.")
        .willBeRemovedInGradle10()
        .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
        .nagUser()
    setFrom(value)
}


/**
 * Sets the ConfigurableFileCollection to contain the source paths of passed collection.
 * This is the same as calling ConfigurableFileCollection.setFrom(fileCollection: FileCollection).
 *
 * @since 8.2
 */
fun ConfigurableFileCollection.assign(fileCollection: FileCollection) {
    this.setFrom(fileCollection)
}
