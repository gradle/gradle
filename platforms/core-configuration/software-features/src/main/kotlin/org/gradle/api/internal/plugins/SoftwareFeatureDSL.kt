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

package org.gradle.api.internal.plugins

import org.gradle.internal.Cast
import kotlin.reflect.KClass
import kotlin.reflect.full.allSupertypes

inline fun <
    reified Definition : HasBuildModel<OwnBuildModel>,
    reified TargetDefinition : HasBuildModel<*>,
    reified OwnBuildModel : BuildModel
    >
    SoftwareFeatureBindingBuilder.bindSoftwareFeature(
    name: String,
    noinline block: SoftwareFeatureApplicationContext.(Definition, OwnBuildModel, TargetDefinition) -> Unit
): DslBindingBuilder<Definition, OwnBuildModel> {
    val bindingTypeInformation = SoftwareFeatureBindingBuilder.bindingToTargetDefinition(Definition::class.java, TargetDefinition::class.java)
    return bindSoftwareFeature(name, bindingTypeInformation, block)
}

fun <
    Definition : HasBuildModel<OwnBuildModel>,
    OwnBuildModel : BuildModel,
    TargetDefinition : HasBuildModel<out TargetBuildModel>,
    TargetBuildModel : BuildModel,
    >
    SoftwareFeatureBindingBuilder.bindSoftwareFeatureToDefinition(
    name: String,
    ownDefinitionType: KClass<Definition>,
    targetDefinitionType: KClass<TargetDefinition>,
    block: SoftwareFeatureApplicationContext.(Definition, OwnBuildModel, TargetDefinition) -> Unit
): DslBindingBuilder<Definition, OwnBuildModel> {
    val bindingTypeInformation = bindingToTargetDefinition(ownDefinitionType.asDefinitionType(), targetDefinitionType.asDefinitionType())
    return bindSoftwareFeature(name, bindingTypeInformation, block)
}

fun <
    Definition : HasBuildModel<OwnBuildModel>,
    OwnBuildModel : BuildModel,
    TargetBuildModel : BuildModel,
    >
    SoftwareFeatureBindingBuilder.bindSoftwareFeatureToBuildModel(
    name: String,
    ownDefinitionType: KClass<Definition>,
    targetBuildModelType: KClass<TargetBuildModel>,
    block: SoftwareFeatureApplicationContext.(Definition, OwnBuildModel, HasBuildModel<TargetBuildModel>) -> Unit
): DslBindingBuilder<Definition, OwnBuildModel> {
    val bindingTypeInformation = bindingToTargetBuildModel(ownDefinitionType.asDefinitionType(), targetBuildModelType.java)
    return bindSoftwareFeature(name, bindingTypeInformation, block)
}

@Suppress("UNUSED_PARAMETER")
inline fun <reified Definition : HasBuildModel<OwnBuildModel>, reified OwnBuildModel : BuildModel>
    definitionOf(classToken: KClass<Definition>) = DefinitionType(Definition::class.java, OwnBuildModel::class.java)

data class DefinitionType<Definition : HasBuildModel<out OwnBuildModel>, OwnBuildModel : BuildModel>(
    val definition: Class<Definition>,
    val ownBuildModel: Class<OwnBuildModel>
)

internal fun <Definition : HasBuildModel<OwnBuildModel>, TargetDefinition : HasBuildModel<out BuildModel>, OwnBuildModel : BuildModel>
    bindingToTargetDefinition(definitionType: DefinitionType<Definition, OwnBuildModel>, targetDefinition: DefinitionType<TargetDefinition, *>) =
    SoftwareFeatureBindingBuilder.bindingToTargetDefinition(definitionType.definition, targetDefinition.definition)

internal fun <Definition : HasBuildModel<OwnBuildModel>, TargetBuildModel : BuildModel, OwnBuildModel : BuildModel>
    bindingToTargetBuildModel(definitionType: DefinitionType<Definition, OwnBuildModel>, targetBuildModel: Class<TargetBuildModel>) =
    SoftwareFeatureBindingBuilder.bindingToTargetBuildModel(definitionType.definition, targetBuildModel)

internal inline fun <reified T: HasBuildModel<V>, reified V: BuildModel> SoftwareTypeBindingBuilder.bindSoftwareFeature(name: String, noinline block: SoftwareFeatureApplicationContext.(T, V) -> Unit): DslBindingBuilder<T, V> {
    return this.bindSoftwareType(name, T::class.java, V::class.java, block)
}

inline fun <reified T: HasBuildModel<V>, reified V: BuildModel> SoftwareTypeBindingBuilder.bindSoftwareType(
    name: String,
    @Suppress("UNUSED_PARAMETER") definitionClass: KClass<T>,
    noinline block: SoftwareFeatureApplicationContext.(T, V) -> Unit
): DslBindingBuilder<T, V> = bindSoftwareType(name, T::class.java, V::class.java, block)

@PublishedApi
internal fun <T : HasBuildModel<out V>, V : BuildModel> KClass<T>.asDefinitionType() = DefinitionType<T, V>(
    Cast.uncheckedNonnullCast(this.java),
    Cast.uncheckedNonnullCast<KClass<V>>(this.allSupertypes.single { it.classifier == HasBuildModel::class }.classifier as KClass<*>).java
)
