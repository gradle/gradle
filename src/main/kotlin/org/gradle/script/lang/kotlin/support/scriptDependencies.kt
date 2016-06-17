package org.gradle.script.lang.kotlin.support

import org.gradle.internal.classpath.ClassPath

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.GradleConnector.newConnector
import org.gradle.tooling.ProjectConnection

import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies
import org.jetbrains.kotlin.script.ScriptDependenciesResolver

import java.io.File

class GradleKotlinScriptDependenciesResolver : ScriptDependenciesResolver {

    override fun resolve(projectRoot: File?, scriptFile: File?, annotations: Iterable<KtAnnotationEntry>, context: Any?): KotlinScriptExternalDependencies? =
        when (context) {
            is ClassPath -> makeDependencies(context.asFiles) // Gradle compilation path
            is File -> retrieveDependenciesFromModelOf(context, projectRoot!!) // IDEA content assist path
            else -> null
        }

    private fun retrieveDependenciesFromModelOf(gradleInstallation: File, projectRoot: File): KotlinScriptExternalDependencies =
        withConnectionFrom(connectorFor(gradleInstallation, projectRoot)) {
            model(KotlinBuildScriptModel::class.java)
//                .setJavaHome(File("/Library/Java/JavaVirtualMachines/jdk1.8.0_77.jdk/Contents/Home"))
                .get()
                .classPath
                .let {
                    makeDependencies(it)
                }
        }

    private fun connectorFor(installation: File, projectDirectory: File) =
        newConnector().useInstallation(installation).forProjectDirectory(projectDirectory)

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

inline fun <T> withConnectionFrom(connector: GradleConnector, block: ProjectConnection.() -> T): T =
    connector.connect().use(block)

inline fun <T> ProjectConnection.use(block: (ProjectConnection) -> T): T {
    try {
        return block(this)
    } finally {
        close()
    }
}
