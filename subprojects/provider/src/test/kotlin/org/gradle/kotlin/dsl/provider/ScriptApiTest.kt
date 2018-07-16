package org.gradle.kotlin.dsl.provider

import org.gradle.kotlin.dsl.KotlinBuildScript
import org.gradle.kotlin.dsl.KotlinInitScript
import org.gradle.kotlin.dsl.KotlinSettingsScript

import org.gradle.api.Action
import org.gradle.api.initialization.Settings

import org.gradle.kotlin.dsl.precompile.PrecompiledInitScript
import org.gradle.kotlin.dsl.precompile.PrecompiledProjectScript
import org.gradle.kotlin.dsl.precompile.PrecompiledSettingsScript

import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.valueParameters
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.jvmErasure

import org.hamcrest.CoreMatchers.equalTo

import org.junit.Assert.assertThat
import org.junit.Test


class ScriptApiTest {

    @Test
    fun `build script template implements script api`() =
        assertScriptApiOf<KotlinBuildScript>()

    @Test
    fun `settings script template implements script api`() =
        assertScriptApiOf<KotlinSettingsScript>()

    @Test
    fun `settings script template implements Settings#enableFeaturePreview`() =
        assert(KotlinSettingsScript::class.implements(Settings::enableFeaturePreview))

    @Test
    fun `init script template implements script api`() =
        assertScriptApiOf<KotlinInitScript>()

    @Test
    fun `precompiled project script template implements script api`() =
        assertScriptApiOf<PrecompiledProjectScript>()

    @Test
    fun `precompiled settings script template implements script api`() =
        assertScriptApiOf<PrecompiledSettingsScript>()

    @Test
    fun `precompiled init script template implements script api`() =
        assertScriptApiOf<PrecompiledInitScript>()
}


private
inline fun <reified T> assertScriptApiOf() =
    assertApiOf<T>(ScriptApi::class)


private
inline fun <reified T> assertApiOf(expectedApi: KClass<*>) =
    assertThat(
        expectedApi.apiMembers.missingMembersFrom(T::class),
        equalTo(emptyList()))


private
typealias ScriptApiMembers = Collection<KCallable<*>>


private
val KClass<*>.apiMembers: ScriptApiMembers
    get() = declaredMembers


private
fun ScriptApiMembers.missingMembersFrom(scriptTemplate: KClass<*>): List<KCallable<*>> =
    scriptTemplate.publicMembers.let { scriptTemplateMembers ->
        filterNot(scriptTemplateMembers::containsMemberCompatibleWith)
    }


private
fun KClass<*>.implements(api: KCallable<*>) =
    publicMembers.containsMemberCompatibleWith(api)


private
val KClass<*>.publicMembers
    get() = members.filter { it.visibility == KVisibility.PUBLIC }


private
fun List<KCallable<*>>.containsMemberCompatibleWith(api: KCallable<*>) =
    find { it.isCompatibleWith(api) } != null


private
fun KCallable<*>.isCompatibleWith(api: KCallable<*>) =
    when (this) {
        is KFunction -> isCompatibleWith(api)
        is KProperty -> isCompatibleWith(api)
        else -> false
    }


private
fun KProperty<*>.isCompatibleWith(api: KCallable<*>) =
    this::class == api::class
        && name == api.name
        && returnType == api.returnType


private
fun KFunction<*>.isCompatibleWith(api: KCallable<*>) =
    when {
        api is KProperty && api !is KMutableProperty && isCompatibleWithGetterOf(api) -> true
        api is KFunction && isCompatibleWith(api) -> true
        else -> false
    }


private
fun KFunction<*>.isCompatibleWithGetterOf(api: KProperty<*>) =
    name == api.javaGetter?.name
        && returnType == api.getter.returnType
        && valueParameters.isEmpty() && api.getter.valueParameters.isEmpty()


private
fun KFunction<*>.isCompatibleWith(api: KFunction<*>) =
    name == api.name
        && returnType == api.returnType
        && valueParameters.isCompatibleWith(api.valueParameters)


private
fun List<KParameter>.isCompatibleWith(api: List<KParameter>) =
    when {
        size != api.size -> false
        isEmpty() -> true
        else -> (0..(size - 1)).all { idx -> this[idx].isCompatibleWith(api[idx]) }
    }


private
fun KParameter.isCompatibleWith(api: KParameter) =
    when {
        isVarargCompatibleWith(api) -> true
        isGradleActionCompatibleWith(api) -> true
        type.isParameterTypeCompatibleWith(api.type) -> true
        else -> false
    }


private
fun KParameter.isGradleActionCompatibleWith(api: KParameter) =
    type.jvmErasure == Action::class
        && api.isSamWithReceiverReturningUnit()
        && type.arguments[0].type!!.isTypeArgumentCompatibleWith(api.type.arguments[0].type!!)


private
fun KParameter.isSamWithReceiverReturningUnit() =
    type.jvmErasure == Function1::class
        && type.arguments[1] == KTypeProjection(KVariance.INVARIANT, Unit::class.createType())


private
fun KParameter.isVarargCompatibleWith(api: KParameter) =
    isVararg && api.isVararg && type.isParameterTypeCompatibleWith(api.type)


private
fun KType.isParameterTypeCompatibleWith(apiParameterType: KType) =
    when {
        this == apiParameterType -> true
        classifier != apiParameterType.classifier -> false
        hasCompatibleTypeArguments(apiParameterType) -> true
        else -> false
    }


private
fun KType.hasCompatibleTypeArguments(api: KType) =
    arguments.size == api.arguments.size && (0..(arguments.size - 1)).all { idx ->
        arguments[idx].type!!.isTypeArgumentCompatibleWith(api.arguments[idx].type!!)
    }


private
fun KType.isTypeArgumentCompatibleWith(api: KType) =
    withNullability(false) == api
