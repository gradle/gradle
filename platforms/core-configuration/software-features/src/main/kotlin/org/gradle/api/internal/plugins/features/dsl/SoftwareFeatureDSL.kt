package org.gradle.api.internal.plugins.features.dsl

import org.gradle.api.internal.plugins.BuildModel
import org.gradle.api.internal.plugins.DslBindingBuilder
import org.gradle.api.internal.plugins.HasBuildModel
import org.gradle.api.internal.plugins.SoftwareFeatureApplicationContext
import org.gradle.api.internal.plugins.SoftwareFeatureBindingBuilder
import org.gradle.api.internal.plugins.SoftwareTypeBindingBuilder
import org.gradle.internal.Cast
import kotlin.reflect.KClass
import kotlin.reflect.full.allSupertypes

/**
 * Binds a software feature to a target definition inferring the types from the provided transform function.
 *
 * <p>Example:</p>
 * <pre>
 * <code>
 *     bindSoftwareFeature("myFeature") { definition: MySoftwareFeatureDefinition, buildModel: MyBuildModel, target: JavaSources ->
 *         // Configure the build model based on the definition
 *     }
 * </code>
 * </pre>
 *
 * @param name The name of the software feature.
 * @param block The software feature transform that maps the target definition to the build model and implements the feature logic.
 * @return A [DslBindingBuilder] for further configuration if needed.
 * @param Definition The type of the software feature definition.
 * @param TargetDefinition The type of the target definition to bind to.
 * @param OwnBuildModel The type of the build model associated with the software feature definition.
 */
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

/**
 * Binds a software feature to a target definition.
 *
 * <p>Example:</p>
 * <pre>
 * <code>
 *     bindSoftwareFeatureToDefinition("myFeature", MyFeatureDefinition::class, JavaSources::class) { definition, buildModel, target ->
 *         // Configure the build model based on the definition
 *     }
 * </code>
 * </pre>
 *
 * @param name The name of the software feature.
 * @param block The software feature transform that maps the target definition to the build model and implements the feature logic.
 * @return A [DslBindingBuilder] for further configuration if needed.
 * @param Definition The type of the software feature definition.
 * @param TargetDefinition The type of the target definition to bind to.
 * @param OwnBuildModel The type of the build model associated with the software feature definition.
 */
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

/**
 * Binds a software feature to a target build model.  In other words, bind the feature to any definition that implements
 * {@link HasBuildModel} for the specified target build model.
 *
 * <p>Example:</p>
 * <pre>
 * <code>
 *     bindSoftwareFeatureToBuildModel("myFeature", MyFeatureDefinition::class, JavaClasses::class) { definition, buildModel, target ->
 *         // Configure the build model based on the definition
 *     }
 * </code>
 * </pre>
 *
 * @param name The name of the software feature.
 * @param block The software feature transform that maps the target build model to the build model and implements the feature logic.
 * @return A [DslBindingBuilder] for further configuration if needed.
 * @param Definition The type of the software feature definition.
 * @param TargetBuildModel The type of the target build model to bind to.
 * @param OwnBuildModel The type of the build model associated with the software feature definition.
 */
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

/**
 * Binds a software type for the given name.  The types are inferred from the provided transform function.
 *
 * <p>Example:</p>
 * <pre>
 * <code>
 *     bindSoftwareType("mySoftwareType") { definition: MySoftwareTypeDefinition, buildModel: MyBuildModel ->
 *         // Configure the build model based on the definition
 *     }
 * </code>
 * </pre>
 *
 * @param name The name of the software type.
 * @param block The software type transform that maps the software type definition to the build model and implements the type logic.
 * @return A [DslBindingBuilder] for further configuration if needed.
 * @param Definition The type of the software type definition.
 * @param OwnBuildModel The type of the build model associated with the software type definition.
 */
inline fun <reified Definition: HasBuildModel<OwnBuildModel>, reified OwnBuildModel: BuildModel> SoftwareTypeBindingBuilder.bindSoftwareType(
    name: String,
    noinline block: SoftwareFeatureApplicationContext.(Definition, OwnBuildModel) -> Unit
): DslBindingBuilder<Definition, OwnBuildModel> = bindSoftwareType(name, Definition::class.java, OwnBuildModel::class.java, block)

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
    SoftwareFeatureBindingBuilder.bindingToTargetDefinition(definitionType.definition, targetDefinition.definition)

internal fun <Definition : HasBuildModel<OwnBuildModel>, TargetBuildModel : BuildModel, OwnBuildModel : BuildModel>
    bindingToTargetBuildModel(definitionType: DefinitionType<Definition, OwnBuildModel>, targetBuildModel: Class<TargetBuildModel>) =
    SoftwareFeatureBindingBuilder.bindingToTargetBuildModel(definitionType.definition, targetBuildModel)

internal inline fun <reified T: HasBuildModel<V>, reified V: BuildModel> SoftwareTypeBindingBuilder.bindSoftwareFeature(name: String, noinline block: SoftwareFeatureApplicationContext.(T, V) -> Unit): DslBindingBuilder<T, V> {
    return this.bindSoftwareType(name, T::class.java, V::class.java, block)
}
