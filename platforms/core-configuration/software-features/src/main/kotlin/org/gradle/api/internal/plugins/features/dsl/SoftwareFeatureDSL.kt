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
