package org.gradle.script.lang.kotlin.support

import org.gradle.internal.classpath.ClassPath

import org.gradle.tooling.ProjectConnection

import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies
import org.jetbrains.kotlin.script.ScriptDependenciesResolver

import java.io.File

class GradleKotlinScriptDependenciesResolver : ScriptDependenciesResolver {

    override fun resolve(projectRoot: File?, scriptFile: File?, annotations: Iterable<KtAnnotationEntry>, context: Any?): KotlinScriptExternalDependencies? =
        when (context) {
            is ClassPath -> makeDependencies(context.asFiles) // Gradle compilation path
            is Map<*, *> -> retrieveDependenciesFrom(projectConnectionOf(context)) // IDEA content assist path
            else -> null
        }

    private fun retrieveDependenciesFrom(connection: ProjectConnection): KotlinScriptExternalDependencies =
        connection
            .getModel(KotlinBuildScriptModel::class.java)
            .classPath
            .let {
                makeDependencies(it)
            }

    private fun projectConnectionOf(context: Map<*, *>) =
        context["projectConnection"] as ProjectConnection

    private fun makeDependencies(classPath: Iterable<File>): KotlinScriptExternalDependencies =
        object : KotlinScriptExternalDependencies {
            override val classpath = classPath
            override val imports = implicitImports
            override val sources = classPath
        }

    companion object {
        val implicitImports = listOf(
            "org.gradle.api.plugins.*",
            "org.gradle.script.lang.kotlin.*")
    }
}
