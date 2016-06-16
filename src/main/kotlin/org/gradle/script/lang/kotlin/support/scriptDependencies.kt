package org.gradle.script.lang.kotlin.support

import org.gradle.internal.classpath.ClassPath

import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.script.GetScriptDependencies
import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies

import java.io.File

class GetGradleKotlinScriptDependencies : GetScriptDependencies {

    override operator fun invoke(annotations: Iterable<KtAnnotationEntry>, context: Any?): KotlinScriptExternalDependencies? {
        fun File.existingOrNull() = let { if (it.exists() && it.isDirectory) it else null }
        fun File.listPrefixedJars(prefixes: Iterable<String>): Iterable<File> = listFiles { file -> prefixes.any { file.name.startsWith(it) } }.asIterable()
        return when (context) {
            is ClassPath -> makeDependencies(context.asFiles)
            is File -> {
                val libDir = File(context, "lib").existingOrNull()
                val pluginsDir = libDir?.let { File(libDir, "plugins") }?.existingOrNull()
                val classpath = (libDir?.listPrefixedJars(defaultDependenciesJarsPrefixes) ?: emptyList()) +
                                (pluginsDir?.listPrefixedJars(defaultDependenciesJarsPrefixes) ?: emptyList())
                if (classpath.size < defaultDependenciesJarsPrefixes.size) {
                    // log
                    null
                } else {
                    makeDependencies(classpath)
                }
            }
            else -> null
        }
    }

    private fun makeDependencies(defaultClasspath: Iterable<File>): KotlinScriptExternalDependencies {
        return object : KotlinScriptExternalDependencies {
            override val classpath = defaultClasspath
            override val imports = implicitImports
            override val sources = defaultClasspath
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
                "gradle-",
                "groovy-all-")
    }
}

@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.RUNTIME)
annotation class dependsOn(val path: String)

