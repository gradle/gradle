package org.gradle.script.lang.kotlin.support

import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies
import org.jetbrains.kotlin.script.ScriptContents

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever

import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.File

class GradleKotlinScriptDependenciesResolverTest {

    val modelProviderMock = modelProviderMock()
    val subject = resolverFor(modelProviderMock)

    @Test
    fun `given an environment with a 'getScriptSectionTokens' entry, when no buildscript change, it will not try to retrieve the model`() {

        val environment = environmentWithGetScriptSectionTokensReturning(sequenceOf(""))

        val res1 = resolve(environment, null)
        val res2 = resolve(environment, res1)
        assertThat(res2!!, sameInstance(res1!!))

        verify(modelProviderMock, times(1)).modelFor(environment)
    }

    @Test
    fun `given an environment with a 'getScriptSectionTokens' entry, when buildscript changes, it will try to retrieve the model again`() {

        val res1 = resolve(environmentWithGetScriptSectionTokensReturning(sequenceOf("foo")), null)
        val res2 = resolve(environmentWithGetScriptSectionTokensReturning(sequenceOf("bar")), res1)
        assertThat(res2!!, not(sameInstance(res1!!)))

        verify(modelProviderMock, times(2)).modelFor(any())
    }

    @Test
    fun `given an environment lacking a 'getScriptSectionTokens' entry, it will always try to retrieve the model`() {

        val environment: Environment = emptyMap()
        val res1 = resolve(environment, null)
        val res2 = resolve(environment, res1)
        assertThat(res2!!, not(sameInstance(res1!!)))

        verify(modelProviderMock, times(2)).modelFor(environment)
    }

    private fun modelProviderMock() =
        mock<KotlinBuildScriptModelProvider>().apply {
            whenever(modelFor(any())).thenReturn(EmptyKotlinBuildScriptModel)
        }

    private fun resolverFor(customModelProvider: KotlinBuildScriptModelProvider): GradleKotlinScriptDependenciesResolver =
        GradleKotlinScriptDependenciesResolver().apply {
            modelProvider = customModelProvider
            sourcePathProvider = EmptySourcePathProvider
        }

    private fun resolve(environment: Environment, previousDependencies: KotlinScriptExternalDependencies?) =
        subject.resolve(EmptyScriptContents, environment, { s, m, p -> }, previousDependencies).get()

    private fun environmentWithGetScriptSectionTokensReturning(sequence: Sequence<String>): Environment =
        environmentWithGetScriptSectionTokens { charSequence, sectionName -> sequence }

    private fun environmentWithGetScriptSectionTokens(function: (CharSequence, String) -> Sequence<String>): Environment =
        mapOf("getScriptSectionTokens" to function)
}

object EmptyScriptContents : ScriptContents {
    override val file: File? = null
    override val text: CharSequence? = ""
    override val annotations: Iterable<Annotation> = emptyList()
}

object EmptySourcePathProvider : SourcePathProvider {
    override fun sourcePathFor(model: KotlinBuildScriptModel, environment: Map<String, Any?>): Collection<File> =
        emptyList()
}

val EmptyKotlinBuildScriptModel = StandardKotlinBuildScriptModel(emptyList())
