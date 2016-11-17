package org.gradle.script.lang.kotlin.support

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.capture
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.isNull
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify

import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat

import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies
import org.jetbrains.kotlin.script.ScriptContents

import org.junit.Test

import java.io.File

class KotlinBuildScriptDependenciesResolverTest {

    private val assemblerMock = scriptDependenciesAssemblerMock()
    private val subject = resolverFor(assemblerMock)

    @Test
    fun `given an environment with a 'getScriptSectionTokens' entry, when no buildscript change, it will not try to retrieve the model`() {

        val environment = environmentWithGetScriptSectionTokensReturning(sequenceOf(""))

        val res1 = resolve(environment, null)
        val res2 = resolve(environment, res1)
        assertThat(res2!!, sameInstance(res1!!))

        verify(assemblerMock, times(1)).assembleDependenciesFrom(eq(environment), isNull(), any())
    }

    @Test
    fun `given an environment with a 'getScriptSectionTokens' entry, when buildscript changes, it will try to retrieve the model again`() {

        val res1 = resolve(environmentWithGetScriptSectionTokensReturning(sequenceOf("foo")), null)
        val res2 = resolve(environmentWithGetScriptSectionTokensReturning(sequenceOf("bar")), res1)
        assertThat(res2!!, not(sameInstance(res1!!)))

        verify(assemblerMock, times(2)).assembleDependenciesFrom(any(), isNull(), any())
    }

    @Test
    fun `given an environment lacking a 'getScriptSectionTokens' entry, it will always try to retrieve the model`() {

        val environment: Environment = emptyMap()
        val res1 = resolve(environment, null)
        val res2 = resolve(environment, res1)
        assertThat(res2!!, not(sameInstance(res1!!)))

        verify(assemblerMock, times(2)).assembleDependenciesFrom(eq(environment), isNull(), isNull())
    }

    private fun scriptDependenciesAssemblerMock() =
        mock<KotlinBuildScriptDependenciesAssembler> {
            val buildscriptBlockHash = argumentCaptor<ByteArray>()
            on { assembleDependenciesFrom(any(), isNull(), capture(buildscriptBlockHash)) }.then {
                KotlinBuildScriptDependencies(
                    emptyList(), emptyList(), emptyList(), buildscriptBlockHash.value)
            }
        }

    private fun resolverFor(customAssembler: KotlinBuildScriptDependenciesAssembler): KotlinBuildScriptDependenciesResolver =
        KotlinBuildScriptDependenciesResolver().apply {
            assembler = customAssembler
        }

    private fun resolve(environment: Environment, previousDependencies: KotlinScriptExternalDependencies?) =
        subject.resolve(EmptyScriptContents, environment, { s, m, p -> }, previousDependencies).get()

    private fun environmentWithGetScriptSectionTokensReturning(sequence: Sequence<String>): Environment =
        environmentWithGetScriptSectionTokens { charSequence, sectionName -> sequence }

    private fun environmentWithGetScriptSectionTokens(function: (CharSequence, String) -> Sequence<String>): Environment =
        mapOf("getScriptSectionTokens" to function)
}

private
object EmptyScriptContents : ScriptContents {
    override val file: File? = null
    override val text: CharSequence? = ""
    override val annotations: Iterable<Annotation> = emptyList()
}
