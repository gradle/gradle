package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.internal.classloader.DefaultClassLoaderFactory
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.concurrent.CompositeStoppable

import org.gradle.kotlin.dsl.KotlinBuildScript
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest

import org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependenciesResolver
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptTemplateModel

import org.gradle.test.fixtures.file.LeaksFileHandles

import org.gradle.tooling.GradleConnector

import org.junit.Test

import java.io.File


class KotlinBuildScriptTemplateModelIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    @LeaksFileHandles
    fun `can load script template using classpath model`() {

        withDefaultSettings()

        val model = fetchKotlinScriptTemplateClassPathModelFor(projectRoot)

        loadClassesFrom(
            model.classPath,
            KotlinBuildScript::class.qualifiedName!!,
            KotlinBuildScriptDependenciesResolver::class.qualifiedName!!)
    }

    private
    fun fetchKotlinScriptTemplateClassPathModelFor(projectDir: File): KotlinBuildScriptTemplateModel {
        val connection = GradleConnector.newConnector()
            .forProjectDirectory(projectDir)
            .useGradleUserHomeDir(File(projectDir, "gradle-user-home"))
            .useInstallation(distribution.gradleHomeDir)
            .connect()
        try {
            return connection.getModel(KotlinBuildScriptTemplateModel::class.java)
        } finally {
            connection.close()
        }
    }

    private
    fun loadClassesFrom(classPath: List<File>, vararg classNames: String) {
        val loader = isolatedClassLoaderFor(classPath)
        try {
            classNames.forEach {
                loader.loadClass(it)
            }
        } finally {
            stop(loader)
        }
    }

    private
    fun isolatedClassLoaderFor(classPath: List<File>) =
        DefaultClassLoaderFactory().createIsolatedClassLoader(
            "kotlin-dsl-script-templates",
            DefaultClassPath.of(classPath)
        )

    private
    fun stop(loader: ClassLoader) {
        CompositeStoppable().add(loader).stop()
    }
}
