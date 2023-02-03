package org.gradle.kotlin.dsl.resolver

import org.gradle.kotlin.dsl.fixtures.assertInstanceOf

import org.junit.Test


class ResolverCoordinatorTest {

    @Test
    fun `given an environment with a 'getScriptSectionTokens' entry, when no buildscript change, it will not try to retrieve the model`() {

        val environment =
            environmentWithGetScriptSectionTokensReturning(
                "buildscript" to sequenceOf(""),
                "plugins" to sequenceOf("")
            )

        val action1 = resolverActionFor(environment, null)
        org.gradle.kotlin.dsl.fixtures.withInstanceOf<ResolverAction.RequestNew>(action1) {
            val action2 = resolverActionFor(environment, scriptDependencies())
            assertInstanceOf<ResolverAction.ReturnPrevious>(action2)
        }
    }

    @Test
    fun `given an environment with a 'getScriptSectionTokens' entry, when buildscript changes, it will try to retrieve the model again`() {

        val env1 = environmentWithGetScriptSectionTokensReturning("buildscript" to sequenceOf("foo"))
        val env2 = environmentWithGetScriptSectionTokensReturning("buildscript" to sequenceOf("bar"))

        val action1 = resolverActionFor(env1, null)
        org.gradle.kotlin.dsl.fixtures.withInstanceOf<ResolverAction.RequestNew>(action1) {
            val action2 = resolverActionFor(env2, scriptDependencies())
            assertInstanceOf<ResolverAction.RequestNew>(action2)
        }
    }

    @Test
    fun `given an environment with a 'getScriptSectionTokens' entry, when plugins block changes, it will try to retrieve the model again`() {

        val env1 = environmentWithGetScriptSectionTokensReturning("plugins" to sequenceOf("foo"))
        val env2 = environmentWithGetScriptSectionTokensReturning("plugins" to sequenceOf("bar"))

        val action1 = resolverActionFor(env1, null)
        org.gradle.kotlin.dsl.fixtures.withInstanceOf<ResolverAction.RequestNew>(action1) {
            val action2 = resolverActionFor(env2, scriptDependencies())
            assertInstanceOf<ResolverAction.RequestNew>(action2)
        }
    }

    @Test
    fun `given an environment lacking a 'getScriptSectionTokens' entry, it will always try to retrieve the model`() {

        val environment = emptyMap<String, Any?>()
        val action1 = resolverActionFor(environment, null)
        org.gradle.kotlin.dsl.fixtures.withInstanceOf<ResolverAction.RequestNew>(action1) {
            val action2 = resolverActionFor(environment, scriptDependencies())
            assertInstanceOf<ResolverAction.RequestNew>(action2)
        }
    }

    private
    fun resolverActionFor(environment: Map<String, Any?>, previousDependencies: kotlin.script.dependencies.KotlinScriptExternalDependencies?) =
        ResolverCoordinator.selectNextActionFor(EmptyScriptContents, environment, previousDependencies)

    private
    fun ResolverAction.RequestNew.scriptDependencies() =
        KotlinBuildScriptDependencies(emptyList(), emptyList(), emptyList(), null, classPathBlocksHash)

    private
    fun environmentWithGetScriptSectionTokensReturning(vararg sections: Pair<String, Sequence<String>>) =
        environmentWithGetScriptSectionTokens { _, section -> sections.find { it.first == section }?.second ?: emptySequence() }

    private
    fun environmentWithGetScriptSectionTokens(function: (CharSequence, String) -> Sequence<String>) =
        mapOf<String, Any?>("getScriptSectionTokens" to function)
}


private
object EmptyScriptContents : kotlin.script.dependencies.ScriptContents {
    override val file: java.io.File? = null
    override val text: CharSequence? = ""
    override val annotations: Iterable<Annotation> = emptyList()
}
