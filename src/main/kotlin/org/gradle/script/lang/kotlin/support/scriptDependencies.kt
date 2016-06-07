package org.gradle.script.lang.kotlin.support

import org.gradle.api.internal.ClassPathRegistry
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.script.GetScriptDependencies
import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies

class GetGradleKotlinScriptDependencies : GetScriptDependencies {

    override operator fun invoke(annotations: Iterable<KtAnnotationEntry>, context: Any?): KotlinScriptExternalDependencies? =
        when (context) {
            is ClassPathRegistry ->
                object : KotlinScriptExternalDependencies {
                    override val classpath = KotlinScriptDefinitionProvider.selectGradleApiJars(context).map { it.absolutePath }
                    override val imports = emptyList<String>()
                }
            else -> null
        }
}


