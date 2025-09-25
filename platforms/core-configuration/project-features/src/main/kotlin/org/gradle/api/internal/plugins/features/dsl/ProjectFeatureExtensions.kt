package org.gradle.api.internal.plugins.features.dsl

import org.gradle.api.internal.plugins.BuildModel
import org.gradle.api.internal.plugins.DslBindingBuilder
import org.gradle.api.internal.plugins.HasBuildModel
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
    reified Definition : HasBuildModel<OwnBuildModel>,
    reified TargetDefinition : HasBuildModel<*>,
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
 * @param Definition The type of the project feature definition.
 * @param TargetDefinition The type of the target definition to bind to.
 * @param OwnBuildModel The type of the build model associated with the project feature definition.
 */
fun <
    Definition : HasBuildModel<OwnBuildModel>,
    OwnBuildModel : BuildModel,
    TargetDefinition : HasBuildModel<out TargetBuildModel>,
    TargetBuildModel : BuildModel,
    >
        ProjectFeatureBindingBuilder.bindProjectFeatureToDefinition(
    name: String,
    ownDefinitionType: KClass<Definition>,
    targetDefinitionType: KClass<TargetDefinition>,
    block: ProjectFeatureApplicationContext.(Definition, OwnBuildModel, TargetDefinition) -> Unit
): DslBindingBuilder<Definition, OwnBuildModel> {
    val bindingTypeInformation = bindingToTargetDefinition(ownDefinitionType.asDefinitionType(), targetDefinitionType.asDefinitionType())
    return bindProjectFeature(name, bindingTypeInformation, block)
}

/**
 * Binds a project feature to a target build model.  In other words, bind the feature to any definition that implements
 * {@link HasBuildModel} for the specified target build model.
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
 * @param Definition The type of the project feature definition.
 * @param TargetBuildModel The type of the target build model to bind to.
 * @param OwnBuildModel The type of the build model associated with the project feature definition.
 */
fun <
    Definition : HasBuildModel<OwnBuildModel>,
    OwnBuildModel : BuildModel,
    TargetBuildModel : BuildModel,
    >
        ProjectFeatureBindingBuilder.bindProjectFeatureToBuildModel(
    name: String,
    ownDefinitionType: KClass<Definition>,
    targetBuildModelType: KClass<TargetBuildModel>,
    block: ProjectFeatureApplicationContext.(Definition, OwnBuildModel, HasBuildModel<TargetBuildModel>) -> Unit
): DslBindingBuilder<Definition, OwnBuildModel> {
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
 * @param Definition The type of the project type definition.
 * @param OwnBuildModel The type of the build model associated with the project type definition.
 */
inline fun <reified Definition: HasBuildModel<OwnBuildModel>, reified OwnBuildModel: BuildModel> ProjectTypeBindingBuilder.bindProjectType(
    name: String,
    noinline block: ProjectFeatureApplicationContext.(Definition, OwnBuildModel) -> Unit
): DslBindingBuilder<Definition, OwnBuildModel> = bindProjectType(name, Definition::class.java, OwnBuildModel::class.java, block)

@PublishedApi
internal fun <T : HasBuildModel<out V>, V : BuildModel> KClass<T>.asDefinitionType() = DefinitionType<T, V>(
    Cast.uncheckedNonnullCast(this.java),
    Cast.uncheckedNonnullCast<KClass<V>>(this.allSupertypes.single { it.classifier == HasBuildModel::class }.classifier as KClass<*>).java
)

@Suppress("UNUSED_PARAMETER")
inline fun <reified Definition : HasBuildModel<OwnBuildModel>, reified OwnBuildModel : BuildModel>
    definitionOf(classToken: KClass<Definition>) = DefinitionType(Definition::class.java, OwnBuildModel::class.java)

data class DefinitionType<Definition : HasBuildModel<out OwnBuildModel>, OwnBuildModel : BuildModel>(
    val definition: Class<Definition>,
    val ownBuildModel: Class<OwnBuildModel>
)

internal fun <Definition : HasBuildModel<OwnBuildModel>, TargetDefinition : HasBuildModel<out BuildModel>, OwnBuildModel : BuildModel>
    bindingToTargetDefinition(definitionType: DefinitionType<Definition, OwnBuildModel>, targetDefinition: DefinitionType<TargetDefinition, *>) =
    ProjectFeatureBindingBuilder.bindingToTargetDefinition(definitionType.definition, targetDefinition.definition)

internal fun <Definition : HasBuildModel<OwnBuildModel>, TargetBuildModel : BuildModel, OwnBuildModel : BuildModel>
    bindingToTargetBuildModel(definitionType: DefinitionType<Definition, OwnBuildModel>, targetBuildModel: Class<TargetBuildModel>) =
    ProjectFeatureBindingBuilder.bindingToTargetBuildModel(definitionType.definition, targetBuildModel)

internal inline fun <reified T: HasBuildModel<V>, reified V: BuildModel> ProjectTypeBindingBuilder.bindProjectFeature(name: String, noinline block: ProjectFeatureApplicationContext.(T, V) -> Unit): DslBindingBuilder<T, V> {
    return this.bindProjectType(name, T::class.java, V::class.java, block)
}
