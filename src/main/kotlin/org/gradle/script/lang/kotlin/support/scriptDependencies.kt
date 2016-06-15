package org.gradle.script.lang.kotlin.support

import org.gradle.api.internal.ClassPathRegistry
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.script.GetScriptDependencies
import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies
import org.jetbrains.kotlin.script.SimpleUntypedAst
import org.jetbrains.kotlin.script.parseAnnotation
import java.io.File

class GetGradleKotlinScriptDependencies : GetScriptDependencies {

    override operator fun invoke(annotations: Iterable<KtAnnotationEntry>, context: Any?): KotlinScriptExternalDependencies? {
        return when (context) {
            is ClassPathRegistry -> makeDependencies(annotations, KotlinScriptDefinitionProvider.selectGradleApiJars(context))
            is File -> {
                val libDir = File(context, "lib").let { if (it.exists() && it.isDirectory) it else null }
                val classpath = libDir?.listFiles { file -> Companion.defaultDependenciesJarsPrefixes.any { file.name.startsWith(it) } }
                if (classpath == null || classpath.size < defaultDependenciesJarsPrefixes.size) {
                    // log
                    null
                }
                else {
                    makeDependencies(annotations, classpath.asIterable())
                }
            }
            else -> null
        }
    }

    private fun makeDependencies(annotations: Iterable<KtAnnotationEntry>, defaultClasspath: Iterable<File>): KotlinScriptExternalDependencies {
        val anns = annotations.map { parseAnnotation(it) }.filter { it.name == dependsOn::class.simpleName }
        val cp = anns.flatMap {
            it.value.mapNotNull {
                when (it) {
                    is SimpleUntypedAst.Node.str -> File(it.value)
                    else -> null
                }
            }
        }
        return object : KotlinScriptExternalDependencies {
            override val classpath = defaultClasspath + cp
            override val imports = implicitImports
        }
    }

    companion object {
        val implicitImports = listOf(
            "org.gradle.script.lang.kotlin.support.depends",
            "org.gradle.api.plugins.*",
            "org.gradle.script.lang.kotlin.*")

        val defaultDependenciesJarsPrefixes = listOf(
                "kotlin-stdlib-",
                "kotlin-reflect-",
                "kotlin-runtime-",
                "ant-",
                "gradle-")
    }
}

@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.RUNTIME)
annotation class dependsOn(val path: String)

