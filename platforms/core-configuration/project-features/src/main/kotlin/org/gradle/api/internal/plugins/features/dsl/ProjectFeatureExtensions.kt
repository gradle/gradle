package org.gradle.api.internal.plugins.features.dsl

import org.gradle.api.internal.plugins.BuildModel
import org.gradle.api.internal.plugins.DslBindingBuilder
import org.gradle.api.internal.plugins.Definition
import org.gradle.api.internal.plugins.ProjectFeatureApplicationContext
import org.gradle.api.internal.plugins.ProjectFeatureBindingBuilder
import org.gradle.api.internal.plugins.ProjectTypeBindingBuilder
import org.gradle.internal.Cast
import kotlin.reflect.KClass
import kotlin.reflect.full.allSupertypes

/**
 * Binds a project feature to a target definition inferring the types from the provided transform function.
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
 * @return A [DslBindingBuilder] for further configuration if needed.
 * @param Definition The type of the project feature definition.
 * @param TargetDefinition The type of the target definition to bind to.
 * @param OwnBuildModel The type of the build model associated with the project feature definition.
 */
inline fun <
    reified Definition : org.gradle.api.internal.plugins.Definition<OwnBuildModel>,
    reified TargetDefinition : org.gradle.api.internal.plugins.Definition<*>,
    reified OwnBuildModel : BuildModel
    >
        ProjectFeatureBindingBuilder.bindProjectFeature(
    name: String,
    noinline block: ProjectFeatureApplicationContext.(Definition, OwnBuildModel, TargetDefinition) -> Unit
): DslBindingBuilder<Definition, OwnBuildModel> {
    val bindingTypeInformation = ProjectFeatureBindingBuilder.bindingToTargetDefinition(Definition::class.java, TargetDefinition::class.java)
    return bindProjectFeature(name, bindingTypeInformation, block)
}

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
 * @param block The project feature transform that maps the target definition to the build model and implements the feature logic.
 * @return A [DslBindingBuilder] for further configuration if needed.
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
): DslBindingBuilder<OwnDefinition, OwnBuildModel> {
    val bindingTypeInformation = bindingToTargetDefinition(ownDefinitionType.asDefinitionType(), targetDefinitionType.asDefinitionType())
    return bindProjectFeature(name, bindingTypeInformation, block)
}

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
 * @return A [DslBindingBuilder] for further configuration if needed.
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
): DslBindingBuilder<OwnDefinition, OwnBuildModel> {
    val bindingTypeInformation = bindingToTargetBuildModel(ownDefinitionType.asDefinitionType(), targetBuildModelType.java)
    return bindProjectFeature(name, bindingTypeInformation, block)
}

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
 * @return A [DslBindingBuilder] for further configuration if needed.
 * @param OwnDefinition The type of the project type definition.
 * @param OwnBuildModel The type of the build model associated with the project type definition.
 */
inline fun <reified OwnDefinition: Definition<OwnBuildModel>, reified OwnBuildModel: BuildModel> ProjectTypeBindingBuilder.bindProjectType(
    name: String,
    noinline block: ProjectFeatureApplicationContext.(OwnDefinition, OwnBuildModel) -> Unit
): DslBindingBuilder<OwnDefinition, OwnBuildModel> = bindProjectType(name, OwnDefinition::class.java, OwnBuildModel::class.java, block)

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

internal inline fun <reified T: Definition<V>, reified V: BuildModel> ProjectTypeBindingBuilder.bindProjectFeature(name: String, noinline block: ProjectFeatureApplicationContext.(T, V) -> Unit): DslBindingBuilder<T, V> {
    return this.bindProjectType(name, T::class.java, V::class.java, block)
}
