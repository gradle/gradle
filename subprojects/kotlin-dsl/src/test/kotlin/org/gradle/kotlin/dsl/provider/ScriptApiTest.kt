package org.gradle.kotlin.dsl.provider

import org.gradle.api.Action
import org.gradle.api.initialization.Settings
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.precompile.v1.PrecompiledInitScript
import org.gradle.kotlin.dsl.precompile.v1.PrecompiledProjectScript
import org.gradle.kotlin.dsl.precompile.v1.PrecompiledSettingsScript
import org.gradle.kotlin.dsl.support.CompiledKotlinBuildScript
import org.gradle.kotlin.dsl.support.CompiledKotlinBuildscriptAndPluginsBlock
import org.gradle.kotlin.dsl.support.CompiledKotlinInitScript
import org.gradle.kotlin.dsl.support.CompiledKotlinInitscriptBlock
import org.gradle.kotlin.dsl.support.CompiledKotlinSettingsPluginManagementBlock
import org.gradle.kotlin.dsl.support.CompiledKotlinSettingsScript
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
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


class ScriptApiTest {

    @Test
    fun `IDE build script template implements script api`() =
        assertScriptApiOf<KotlinProjectScriptTemplate>()

    @Test
    fun `IDE settings script template implements script api`() =
        assertScriptApiOf<KotlinSettingsScriptTemplate>()

    @Test
    fun `IDE init script template implements script api`() =
        assertScriptApiOf<KotlinGradleScriptTemplate>()

    @Test
    fun `legacy IDE build script template implements script api`() =
        @Suppress("deprecation")
        assertScriptApiOf<KotlinBuildScript>()

    @Test
    fun `legacy IDE settings script template implements script api`() =
        @Suppress("deprecation")
        assertScriptApiOf<KotlinSettingsScript>()

    @Test
    fun `legacy IDE init script template implements script api`() =
        @Suppress("deprecation")
        assertScriptApiOf<KotlinInitScript>()

    @Test
    fun `IDE build script template is backwards compatible`() {
        @Suppress("deprecation")
        assertThat(
            KotlinBuildScript::class.declaredMembers.filter { it.isPublic }.missingMembersFrom(
                KotlinProjectScriptTemplate::class
            ),
            equalTo(emptyList())
        )
    }


    @Test
    fun `IDE settings script template is backwards compatible`() {
        @Suppress("deprecation")
        assertThat(
            KotlinSettingsScript::class.declaredMembers.filter { it.isPublic }.missingMembersFrom(
                KotlinSettingsScriptTemplate::class
            ),
            equalTo(emptyList())
        )
    }

    @Test
    fun `IDE init script template is backwards compatible`() {
        @Suppress("deprecation")
        assertThat(
            KotlinInitScript::class.declaredMembers.filter { it.isPublic }.missingMembersFrom(
                KotlinGradleScriptTemplate::class
            ),
            equalTo(emptyList())
        )
    }

    @Test
    fun `compiled init script template implements script api`() =
        assertScriptApiOf<CompiledKotlinInitScript>()

    @Test
    fun `compiled settings script template implements script api`() =
        assertScriptApiOf<CompiledKotlinSettingsScript>()

    @Test
    fun `compiled settings pluginManagement block template implements script api`() =
        assertScriptApiOf<CompiledKotlinSettingsPluginManagementBlock>()

    @Test
    fun `compiled project script template implements script api`() =
        assertScriptApiOf<CompiledKotlinBuildScript>()

    @Test
    fun `compiled project buildscript and plugins block template implements script api`() =
        assertScriptApiOf<CompiledKotlinBuildscriptAndPluginsBlock>()

    @Test
    fun `precompiled project script template implements script api`() =
        assertScriptApiOf<PrecompiledProjectScript>()

    @Test
    fun `precompiled settings script template implements script api`() =
        assertScriptApiOf<PrecompiledSettingsScript>()

    @Test
    fun `precompiled init script template implements script api`() =
        assertScriptApiOf<PrecompiledInitScript>()

    @Test
    fun `init script template is backward compatible`() {
        @Suppress("deprecation")
        assertThat(
            InitScriptApi::class.declaredMembers.filter { it.isPublic }.missingMembersFrom(
                CompiledKotlinInitscriptBlock::class
            ),
            equalTo(emptyList())
        )
    }

    @Test
    fun `settings script template is backward compatible`() {
        @Suppress("deprecation")
        assertThat(
            SettingsScriptApi::class.declaredMembers.filter { it.isPublic }.missingMembersFrom(
                CompiledKotlinSettingsPluginManagementBlock::class
            ).missingMembersFrom(
                Settings::class
            ),
            equalTo(emptyList())
        )
    }
}


private
inline fun <reified T> assertScriptApiOf() {
    if (!KotlinScript::class.java.isAssignableFrom(T::class.java))
        assertApiOf<T>(KotlinScript::class)
}


private
inline fun <reified T> assertApiOf(expectedApi: KClass<*>) =
    assertThat(
        expectedApi.apiMembers.missingMembersFrom(T::class),
        equalTo(emptyList())
    )


private
typealias ScriptApiMembers = Collection<KCallable<*>>


private
val KClass<*>.apiMembers: ScriptApiMembers
    get() = declaredMembers


private
fun ScriptApiMembers.missingMembersFrom(scriptTemplate: KClass<*>): List<KCallable<*>> =
    filterNot(scriptTemplate.publicMembers::containsMemberCompatibleWith)


private
fun KClass<*>.implements(api: KCallable<*>) =
    publicMembers.containsMemberCompatibleWith(api)


private
val KClass<*>.publicMembers
    get() = members.filter { it.isPublic }


private
val KCallable<*>.isPublic
    get() = visibility == KVisibility.PUBLIC


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
        else -> indices.all { idx -> get(idx).isCompatibleWith(api[idx]) }
    }


private
fun KParameter.isCompatibleWith(api: KParameter) =
    when {
        isVarargCompatibleWith(api) -> true
        isGradleActionCompatibleWith(api) || api.isGradleActionCompatibleWith(this) -> true
        type.isParameterTypeCompatibleWith(api.type) -> true
        else -> false
    }


private
fun KParameter.isGradleActionCompatibleWith(api: KParameter) =
    api.type.jvmErasure == Action::class
        && isSamWithReceiverReturningUnit()
        && api.type.arguments[0].type!!.isTypeArgumentCompatibleWith(type.arguments[0].type!!)


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
    arguments.size == api.arguments.size && arguments.indices.all { idx ->
        val expectedType = arguments[idx].type
        val actualType = api.arguments[idx].type
        expectedType?.let { e ->
            actualType?.let { a -> e.isTypeArgumentCompatibleWith(a) }
        } ?: false
    }


private
fun KType.isTypeArgumentCompatibleWith(api: KType) =
    withNullability(false) == api
