package org.gradle.script.lang.kotlin.support

import org.gradle.api.internal.ClassPathRegistry
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.script.GetScriptDependencies
import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies
import org.jetbrains.kotlin.script.SimpleUntypedAst
import org.jetbrains.kotlin.script.parseAnnotation

class GetGradleKotlinScriptDependencies : GetScriptDependencies {

    override operator fun invoke(annotations: Iterable<KtAnnotationEntry>, context: Any?): KotlinScriptExternalDependencies? {
        val anns = annotations.map { parseAnnotation(it) }.filter { it.name == dependsOn::class.simpleName }
        val cp = anns.flatMap {
            it.value.mapNotNull {
                when (it) {
                    is SimpleUntypedAst.Node.str -> it.value
                    else -> null
                }
            }
        }
        return when (context) {
            is ClassPathRegistry ->
                object : KotlinScriptExternalDependencies {
                    override val classpath = KotlinScriptDefinitionProvider.selectGradleApiJars(context).map { it.absolutePath } + cp
                    override val imports = implicitImports
                }
            else -> null
        }
    }

    companion object {
        val implicitImports = listOf(
            "org.gradle.script.lang.kotlin.support.depends",
            "org.gradle.api.plugins.*",
            "org.gradle.script.lang.kotlin.*")
    }
}

@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.RUNTIME)
annotation class dependsOn(val path: String)

