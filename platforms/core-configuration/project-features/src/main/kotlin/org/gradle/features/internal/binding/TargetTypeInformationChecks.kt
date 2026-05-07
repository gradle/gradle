/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.internal.binding

import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.Definition
import org.gradle.features.binding.TargetTypeInformation
import kotlin.reflect.KClass
import kotlin.reflect.full.allSupertypes

object TargetTypeInformationChecks {
    @JvmStatic
    fun isValidBindingType(expectedTargetDefinitionType: TargetTypeInformation<*>, actualTargetDefinitionType: Class<*>): Boolean =
        when (expectedTargetDefinitionType) {
            is TargetTypeInformation.DefinitionTargetTypeInformation ->
                expectedTargetDefinitionType.definitionType.isAssignableFrom(actualTargetDefinitionType)

            is TargetTypeInformation.BuildModelTargetTypeInformation<*> ->
                Definition::class.java.isAssignableFrom(actualTargetDefinitionType) &&
                    isValidBuildModelForDefinition(expectedTargetDefinitionType.buildModelType, actualTargetDefinitionType)

            else -> true
        }


    private
    fun isValidBuildModelForDefinition(buildModel: Class<out BuildModel>, definition: Class<*>): Boolean =
        definition.kotlin.allSupertypes.find { it.classifier == Definition::class }
            ?.let { (it.arguments.singleOrNull()?.type?.classifier as? KClass<*>)?.java?.let(buildModel::isAssignableFrom) } ?: false

    private
    fun isValidDefinitionForBuildModel(definition: Class<*>, buildModel: Class<out BuildModel>): Boolean =
        definition.kotlin.allSupertypes.find { it.classifier == Definition::class }
            ?.let { (it.arguments.singleOrNull()?.type?.classifier as? KClass<*>)?.java?.let { it.isAssignableFrom(buildModel) } } ?: false

    @JvmStatic
    fun isOverlappingBindingType(targetType: TargetTypeInformation<*>, otherTargetType: TargetTypeInformation<*>): Boolean =
        when {
            // Returns true if (targetType: Definition) is the same or subtype of (otherTargetType: Definition) or vice versa
            targetType is TargetTypeInformation.DefinitionTargetTypeInformation<*> &&
                otherTargetType is TargetTypeInformation.DefinitionTargetTypeInformation<*> ->
                targetType.definitionType.isAssignableFrom(otherTargetType.definitionType) || otherTargetType.definitionType.isAssignableFrom(targetType.definitionType)

            // Returns true if (targetType: BuildModel) is the same or subtype of (otherTargetType: BuildModel) or vice versa
            targetType is TargetTypeInformation.BuildModelTargetTypeInformation<*> &&
                otherTargetType is TargetTypeInformation.BuildModelTargetTypeInformation<*> ->
                targetType.buildModelType.isAssignableFrom(otherTargetType.buildModelType) || otherTargetType.buildModelType.isAssignableFrom(targetType.buildModelType)

            // Returns true if (targetType: Definition<Foo>) and (otherTargetType: Foo) or is in the hierarchy of Foo
            targetType is TargetTypeInformation.DefinitionTargetTypeInformation<*> &&
                otherTargetType is TargetTypeInformation.BuildModelTargetTypeInformation<*> ->
                    isValidBuildModelForDefinition(otherTargetType.buildModelType, targetType.definitionType)
                        || isValidDefinitionForBuildModel(targetType.definitionType, otherTargetType.buildModelType)

            // Returns true if (targetType: Foo) or is in the hierarchy of Foo and (otherTargetType: Definition<Foo>)
            targetType is TargetTypeInformation.BuildModelTargetTypeInformation<*> &&
                otherTargetType is TargetTypeInformation.DefinitionTargetTypeInformation<*> ->
                    isValidBuildModelForDefinition(targetType.buildModelType, otherTargetType.definitionType) ||
                        isValidDefinitionForBuildModel(otherTargetType.definitionType, targetType.buildModelType)

            else -> false
        }
}
