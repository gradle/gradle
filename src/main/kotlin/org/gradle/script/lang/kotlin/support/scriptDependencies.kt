package org.gradle.script.lang.kotlin.support

import org.gradle.internal.classpath.ClassPath
import org.gradle.tooling.GradleConnector

import org.gradle.tooling.ProjectConnection

import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies
import org.jetbrains.kotlin.script.ScriptDependenciesResolver

import java.io.File
import java.util.*

class GradleKotlinScriptDependenciesResolver : ScriptDependenciesResolver {

    override fun resolve(projectRoot: File?, scriptFile: File?, annotations: Iterable<KtAnnotationEntry>, context: Any?): KotlinScriptExternalDependencies? =
        when (context) {
            is ClassPath -> makeDependencies(context.asFiles) // Gradle compilation path
            is Map<*, *> -> retrieveDependenciesFromProject(projectRoot!!, gradleHomeOf(context)) // IDEA content assist path
            else -> null
        }

    private fun retrieveDependenciesFromProject(projectRoot: File, gradleInstallation: File): KotlinScriptExternalDependencies {
        return withConnectionFrom(connectorFor(gradleInstallation, projectRoot)) {
            model(KotlinBuildScriptModel::class.java)
                .setJavaHome(javaHomeForDaemonOf(projectRoot))
                .get()
                .classPath
                .let {
                    makeDependencies(it)
                }
        }
    }

    private fun gradleHomeOf(context: Map<*, *>) =
        context["gradleHome"] as File

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

private fun javaHomeForDaemonOf(projectRoot: File): File =
    File(daemonPropertiesOf(projectRoot)["java.home"] as String)

private fun daemonPropertiesOf(projectRoot: File): Properties =
    daemonPropertiesFileOf(projectRoot).inputStream().use { input ->
        Properties().apply {
            load(input)
        }
    }

private fun connectorFor(gradleInstallation: File, projectRoot: File) =
    GradleConnector.newConnector().forProjectDirectory(projectRoot).useInstallation(gradleInstallation)

inline fun <T> withConnectionFrom(connector: GradleConnector, block: ProjectConnection.() -> T): T =
    connector.connect().use(block)

inline fun <T> ProjectConnection.use(block: (ProjectConnection) -> T): T {
    try {
        return block(this)
    } finally {
        close()
    }
}
