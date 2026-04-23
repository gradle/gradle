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

package org.gradle.features.dsl

import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.DeclaredProjectFeatureBindingBuilder
import org.gradle.features.binding.Definition
import org.gradle.features.binding.ProjectFeatureApplicationContext
import org.gradle.features.binding.ProjectFeatureApplyAction
import org.gradle.features.binding.ProjectFeatureBindingBuilder
import org.gradle.features.binding.ProjectTypeApplyAction
import org.gradle.features.binding.ProjectTypeBindingBuilder
import org.gradle.internal.Cast
import kotlin.reflect.KClass
import kotlin.reflect.full.allSupertypes

/**
 * Binds a project feature to a target definition inferring the types from the provided feature apply action.
 *
 * <p>Example:</p>
 * <pre>
 * <code>
 *     bindProjectFeature("myFeature") { definition: MyProjectFeatureDefinition, buildModel: MyBuildModel, target: JavaSources ->
 *         // Configure the build model based on the definition
 *     }
 * </code>
 * </pre>
 *
 * @param name The name of the project feature.
 * @param block The project feature transform that maps the target definition to the build model and implements the feature logic.
 * @return A [org.gradle.features.binding.DeclaredProjectFeatureBindingBuilder] for further configuration if needed.
 * @param OwnDefinition The type of the project feature definition.
 * @param TargetDefinition The type of the target definition to bind to.
 * @param OwnBuildModel The type of the build model associated with the project feature definition.
 */
inline fun <
    reified OwnDefinition : org.gradle.features.binding.Definition<OwnBuildModel>,
    reified TargetDefinition : org.gradle.features.binding.Definition<*>,
    reified OwnBuildModel : BuildModel
    >
ProjectFeatureBindingBuilder.bindProjectFeature(
    name: String,
    noinline block: ProjectFeatureApplicationContext.(OwnDefinition, OwnBuildModel, TargetDefinition) -> Unit
): DeclaredProjectFeatureBindingBuilder<OwnDefinition, OwnBuildModel> =
    bindProjectFeature(
        name,
        ProjectFeatureBindingBuilder.bindingToTargetDefinition(OwnDefinition::class.java, TargetDefinition::class.java),
        block
    )

/**
 * Binds a project feature to a target definition inferring the types from the provided feature apply action.
 *
 * <p>Example:</p>
 * <pre>
 * <code>
 *     bindProjectFeature("myFeature", MyProjectFeatureApplyAction::class)
 * </code>
 * </pre>
 *
 * @param name The name of the project feature.
 * @param applyClass The project feature transform class that maps the target definition to the build model and implements the feature logic.
 * @return A [org.gradle.features.binding.DeclaredProjectFeatureBindingBuilder] for further configuration if needed.
 * @param OwnDefinition The type of the project feature definition.
 * @param TargetDefinition The type of the target definition to bind to.
 * @param OwnBuildModel The type of the build model associated with the project feature definition.
 */
inline fun <
    reified OwnDefinition : org.gradle.features.binding.Definition<OwnBuildModel>,
    reified TargetDefinition : org.gradle.features.binding.Definition<*>,
    reified OwnBuildModel : BuildModel
    >
ProjectFeatureBindingBuilder.bindProjectFeature(
    name: String,
    applyClass: KClass<out ProjectFeatureApplyAction<OwnDefinition, OwnBuildModel, TargetDefinition>>
): DeclaredProjectFeatureBindingBuilder<OwnDefinition, OwnBuildModel> =
    bindProjectFeature(
        name,
        ProjectFeatureBindingBuilder.bindingToTargetDefinition(OwnDefinition::class.java, TargetDefinition::class.java),
        applyClass.java
    )

/**
 * Binds a project feature to a target definition.
 *
 * <p>Example:</p>
 * <pre>
 * <code>
 *     bindProjectFeatureToDefinition("myFeature", MyFeatureDefinition::class, JavaSources::class) { definition, buildModel, target ->
 *         // Configure the build model based on the definition
 *     }
 * </code>
 * </pre>
 *
 * @param name The name of the project feature.
 * @param block The project feature apply action that maps the target definition to the build model and implements the feature logic.
 * @return A [DeclaredProjectFeatureBindingBuilder] for further configuration if needed.
 * @param OwnDefinition The type of the project feature definition.
 * @param TargetDefinition The type of the target definition to bind to.
 * @param OwnBuildModel The type of the build model associated with the project feature definition.
 */
fun <
    OwnDefinition : Definition<OwnBuildModel>,
    OwnBuildModel : BuildModel,
    TargetDefinition : Definition<out TargetBuildModel>,
    TargetBuildModel : BuildModel,
    >
ProjectFeatureBindingBuilder.bindProjectFeatureToDefinition(
    name: String,
    ownDefinitionType: KClass<OwnDefinition>,
    targetDefinitionType: KClass<TargetDefinition>,
    block: ProjectFeatureApplicationContext.(OwnDefinition, OwnBuildModel, TargetDefinition) -> Unit
): DeclaredProjectFeatureBindingBuilder<OwnDefinition, OwnBuildModel> =
    bindProjectFeature(
        name,
        bindingToTargetDefinition(ownDefinitionType.asDefinitionType(), targetDefinitionType.asDefinitionType()),
        block
    )

/**
 * Binds a project feature to a target definition.
 *
 * <p>Example:</p>
 * <pre>
 * <code>
 *     bindProjectFeatureToDefinition("myFeature", MyFeatureDefinition::class, JavaSources::class, MyFeatureApplyAction::class)
 * </code>
 * </pre>
 *
 * @param name The name of the project feature.
 * @param applyClass The project feature apply action that maps the target definition to the build model and implements the feature logic.
 * @return A [DeclaredProjectFeatureBindingBuilder] for further configuration if needed.
 * @param OwnDefinition The type of the project feature definition.
 * @param TargetDefinition The type of the target definition to bind to.
 * @param OwnBuildModel The type of the build model associated with the project feature definition.
 */
fun <
    OwnDefinition : Definition<OwnBuildModel>,
    OwnBuildModel : BuildModel,
    TargetDefinition : Definition<out TargetBuildModel>,
    TargetBuildModel : BuildModel,
    >
ProjectFeatureBindingBuilder.bindProjectFeatureToDefinition(
    name: String,
    ownDefinitionType: KClass<OwnDefinition>,
    targetDefinitionType: KClass<TargetDefinition>,
    applyClass: KClass<out ProjectFeatureApplyAction<OwnDefinition, OwnBuildModel, TargetDefinition>>
): DeclaredProjectFeatureBindingBuilder<OwnDefinition, OwnBuildModel> =
    bindProjectFeature(
        name,
        bindingToTargetDefinition(ownDefinitionType.asDefinitionType(), targetDefinitionType.asDefinitionType()),
        applyClass.java
    )

/**
 * Binds a project feature to a target build model.  In other words, bind the feature to any definition that implements
 * {@link Definition} for the specified target build model.
 *
 * <p>Example:</p>
 * <pre>
 * <code>
 *     bindProjectFeatureToBuildModel("myFeature", MyFeatureDefinition::class, JavaClasses::class) { definition, buildModel, target ->
 *         // Configure the build model based on the definition
 *     }
 * </code>
 * </pre>
 *
 * @param name The name of the project feature.
 * @param block The project feature transform that maps the target build model to the build model and implements the feature logic.
 * @return A [DeclaredProjectFeatureBindingBuilder] for further configuration if needed.
 * @param OwnDefinition The type of the project feature definition.
 * @param TargetBuildModel The type of the target build model to bind to.
 * @param OwnBuildModel The type of the build model associated with the project feature definition.
 */
fun <
    OwnDefinition : Definition<OwnBuildModel>,
    OwnBuildModel : BuildModel,
    TargetBuildModel : BuildModel,
    >
ProjectFeatureBindingBuilder.bindProjectFeatureToBuildModel(
    name: String,
    ownDefinitionType: KClass<OwnDefinition>,
    targetBuildModelType: KClass<TargetBuildModel>,
    block: ProjectFeatureApplicationContext.(OwnDefinition, OwnBuildModel, Definition<TargetBuildModel>) -> Unit
): DeclaredProjectFeatureBindingBuilder<OwnDefinition, OwnBuildModel> =
    bindProjectFeature(
        name,
        bindingToTargetBuildModel(ownDefinitionType.asDefinitionType(), targetBuildModelType.java),
        block
    )

/**
 * Binds a project feature to a target build model.  In other words, bind the feature to any definition that implements
 * {@link Definition} for the specified target build model.
 *
 * <p>Example:</p>
 * <pre>
 * <code>
 *     bindProjectFeatureToBuildModel("myFeature", MyFeatureDefinition::class, JavaClasses::class, MyFeatureApplyAction::class)
 * </code>
 * </pre>
 *
 * @param name The name of the project feature.
 * @param applyClass The project feature transform that maps the target build model to the build model and implements the feature logic.
 * @return A [DeclaredProjectFeatureBindingBuilder] for further configuration if needed.
 * @param OwnDefinition The type of the project feature definition.
 * @param TargetBuildModel The type of the target build model to bind to.
 * @param OwnBuildModel The type of the build model associated with the project feature definition.
 */
fun <
    OwnDefinition : Definition<OwnBuildModel>,
    OwnBuildModel : BuildModel,
    TargetBuildModel : BuildModel,
    >
ProjectFeatureBindingBuilder.bindProjectFeatureToBuildModel(
    name: String,
    ownDefinitionType: KClass<OwnDefinition>,
    targetBuildModelType: KClass<TargetBuildModel>,
    applyClass: KClass<out ProjectFeatureApplyAction<OwnDefinition, OwnBuildModel, Definition<TargetBuildModel>>>
): DeclaredProjectFeatureBindingBuilder<OwnDefinition, OwnBuildModel> =
    bindProjectFeature(
        name,
        bindingToTargetBuildModel(ownDefinitionType.asDefinitionType(), targetBuildModelType.java),
        applyClass.java
    )

/**
 * Binds a project type for the given name.  The types are inferred from the provided transform function.
 *
 * <p>Example:</p>
 * <pre>
 * <code>
 *     bindProjectType("myProjectType") { definition: MyProjectTypeDefinition, buildModel: MyBuildModel ->
 *         // Configure the build model based on the definition
 *     }
 * </code>
 * </pre>
 *
 * @param name The name of the project type.
 * @param block The project type transform that maps the project type definition to the build model and implements the type logic.
 * @return A [DeclaredProjectFeatureBindingBuilder] for further configuration if needed.
 * @param OwnDefinition The type of the project type definition.
 * @param OwnBuildModel The type of the build model associated with the project type definition.
 */
inline fun <reified OwnDefinition: Definition<OwnBuildModel>, reified OwnBuildModel: BuildModel> ProjectTypeBindingBuilder.bindProjectType(
    name: String,
    noinline block: ProjectFeatureApplicationContext.(OwnDefinition, OwnBuildModel) -> Unit
): DeclaredProjectFeatureBindingBuilder<OwnDefinition, OwnBuildModel> = bindProjectType(name, OwnDefinition::class.java, block)

/**
 * Binds a project type for the given name.  The types are inferred from the provided apply action class.
 *
 * <p>Example:</p>
 * <pre>
 * <code>
 *     bindProjectType("myProjectType", MyProjectTypeApplyAction::class)
 * </code>
 * </pre>
 *
 * @param name The name of the project type.
 * @param applyClass The project type apply action that maps the project type definition to the build model and implements the type logic.
 * @return A [DeclaredProjectFeatureBindingBuilder] for further configuration if needed.
 * @param OwnDefinition The type of the project type definition.
 * @param OwnBuildModel The type of the build model associated with the project type definition.
 */
inline fun <reified OwnDefinition: Definition<OwnBuildModel>, reified OwnBuildModel: BuildModel> ProjectTypeBindingBuilder.bindProjectType(
    name: String,
    applyClass: KClass<out ProjectTypeApplyAction<OwnDefinition, OwnBuildModel>>
): DeclaredProjectFeatureBindingBuilder<OwnDefinition, OwnBuildModel> = bindProjectType(name, OwnDefinition::class.java, applyClass.java)


@PublishedApi
internal fun <T : Definition<out V>, V : BuildModel> KClass<T>.asDefinitionType() = DefinitionType<T, V>(
    Cast.uncheckedNonnullCast(this.java),
    Cast.uncheckedNonnullCast<KClass<V>>(this.allSupertypes.single { it.classifier == Definition::class }.classifier as KClass<*>).java
)

@Suppress("UNUSED_PARAMETER")
inline fun <reified OwnDefinition : Definition<OwnBuildModel>, reified OwnBuildModel : BuildModel>
    definitionOf(classToken: KClass<OwnDefinition>) = DefinitionType(OwnDefinition::class.java, OwnBuildModel::class.java)

data class DefinitionType<OwnDefinition : Definition<out OwnBuildModel>, OwnBuildModel : BuildModel>(
    val definition: Class<OwnDefinition>,
    val ownBuildModel: Class<OwnBuildModel>
)

internal fun <OwnDefinition : Definition<OwnBuildModel>, TargetDefinition : Definition<out BuildModel>, OwnBuildModel : BuildModel>
    bindingToTargetDefinition(definitionType: DefinitionType<OwnDefinition, OwnBuildModel>, targetDefinition: DefinitionType<TargetDefinition, *>) =
    ProjectFeatureBindingBuilder.bindingToTargetDefinition(definitionType.definition, targetDefinition.definition)

internal fun <OwnDefinition : Definition<OwnBuildModel>, TargetBuildModel : BuildModel, OwnBuildModel : BuildModel>
    bindingToTargetBuildModel(definitionType: DefinitionType<OwnDefinition, OwnBuildModel>, targetBuildModel: Class<TargetBuildModel>) =
    ProjectFeatureBindingBuilder.bindingToTargetBuildModel(definitionType.definition, targetBuildModel)
