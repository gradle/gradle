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

package org.gradle.plugin.software.internal

import org.gradle.api.internal.plugins.Definition
import org.gradle.api.internal.plugins.TargetTypeInformation
import org.gradle.api.internal.plugins.TargetTypeInformation.BuildModelTargetTypeInformation
import org.gradle.api.internal.plugins.TargetTypeInformation.DefinitionTargetTypeInformation
import kotlin.reflect.KClass
import kotlin.reflect.full.allSupertypes

object TargetTypeInformationChecks {
    @JvmStatic
    fun isValidBindingType(expectedTargetDefinitionType: TargetTypeInformation<*>, actualTargetDefinitionType: Class<*>): Boolean =
        when (expectedTargetDefinitionType) {
            is DefinitionTargetTypeInformation ->
                expectedTargetDefinitionType.definitionType.isAssignableFrom(actualTargetDefinitionType)

            is BuildModelTargetTypeInformation<*> ->
                Definition::class.java.isAssignableFrom(actualTargetDefinitionType) &&
                    actualTargetDefinitionType.kotlin.allSupertypes.find { it.classifier == Definition::class }
                        ?.let { (it.arguments.singleOrNull()?.type?.classifier as? KClass<*>)?.java?.let(expectedTargetDefinitionType.buildModelType::isAssignableFrom) }
                    ?: false

            else -> true
        }
}
