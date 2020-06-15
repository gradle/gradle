package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.classloader.DefaultClassLoaderFactory
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.concurrent.CompositeStoppable

import org.gradle.kotlin.dsl.KotlinBuildScript
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest

import org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependenciesResolver
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptTemplateModel

import org.gradle.test.fixtures.file.LeaksFileHandles

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.internal.consumer.DefaultGradleConnector

import org.junit.Test

import java.io.File


class KotlinBuildScriptTemplateModelIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    @LeaksFileHandles("ad-hoc TAPI usage, to be ported to proper cross-version tests")
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
        val connector = GradleConnector.newConnector()
            .forProjectDirectory(projectDir)
            .useGradleUserHomeDir(buildContext.gradleUserHomeDir)
        if (GradleContextualExecuter.isEmbedded()) {
            (connector as DefaultGradleConnector).apply {
                embedded(true)
                useClasspathDistribution()
            }
        } else {
            connector.useInstallation(distribution.gradleHomeDir)
        }
        connector.connect().use {
            return it.getModel(KotlinBuildScriptTemplateModel::class.java)
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
