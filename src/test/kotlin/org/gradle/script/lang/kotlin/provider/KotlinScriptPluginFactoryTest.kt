package org.gradle.script.lang.kotlin.provider

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ClassPathProvider
import org.gradle.api.internal.DefaultClassPathRegistry
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory.ClassPathNotation.GRADLE_API
import org.gradle.api.internal.classpath.EffectiveClassPath
import org.gradle.api.internal.hash.DefaultHasher
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.initialization.loadercache.DefaultClassLoaderCache
import org.gradle.api.internal.initialization.loadercache.HashClassPathSnapshotter

import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.UriScriptSource

import org.gradle.initialization.DefaultClassLoaderRegistry
import org.gradle.initialization.DefaultClassLoaderScopeRegistry

import org.gradle.internal.classloader.DefaultHashingClassLoaderFactory
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import org.junit.Test

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class KotlinScriptPluginFactoryTest {

    @Test
    fun `Can apply SAM conversions`() {

        val script = """
        import org.gradle.script.lang.kotlin.*
        import org.gradle.api.tasks.bundling.Zip
        import java.util.concurrent.Callable

        task<Zip>("zip") {
            from(Callable {
                val files = configurations.getByName("compile").files
                zipTree(files.single { it.name.startsWith(baseName) })
            })
        }
        """

        val project: Project = mock()
        whenever(project.tasks).thenReturn(mock())

        applyBuildScript(script, project)
    }

    private fun applyBuildScript(script: String, project: Project) {
        val testClassPath = EffectiveClassPath(javaClass.classLoader)
        val gradleScriptKotlinJar = tempJarFor(mainSourceSetOutputDirFrom(testClassPath.asFiles))
        val gradleApiClassPath = testClassPath + DefaultClassPath.of(listOf(gradleScriptKotlinJar))

        val classPathProvider = classPathProviderFor(gradleApiClassPath)
        val classPathRegistry = DefaultClassPathRegistry(classPathProvider)
        val classLoaderScopeRegistry = classLoaderScopeRegistryFor(classPathRegistry)
        val targetScope = classLoaderScopeRegistry.coreAndPluginsScope.createChild("buildSrc").createChild("rootProject")

        val scriptPlugin = KotlinScriptPluginFactory(classPathRegistry).create(
            scriptSourceFor(script), scriptHandlerStub(), targetScope, targetScope.parent, true)

        (scriptPlugin as KotlinScriptPlugin).instantiateScriptClass(project)
    }

    private fun scriptHandlerStub() =
        mock<ScriptHandlerInternal>().apply {
            val files = arrayListOf<File>()
            whenever(addScriptClassPathDependency(any())).then {
                files.addAll(it.arguments[0] as FileCollection)
            }
            whenever(scriptClassPath).then {
                DefaultClassPath.of(files)
            }
        }

    private fun classPathProviderFor(gradleApi: ClassPath) =
        ClassPathProvider {
            when (it) {
                GRADLE_API.name -> gradleApi
                else -> DefaultClassPath.EMPTY
            }
        }

    private fun mainSourceSetOutputDirFrom(files: Collection<File>) =
        files.single { it.isDirectory && it.path.endsWith("classes/main") }

    private fun tempJarFor(baseDir: File): File {
        val jarFile = createTempFile("gradle-script-kotlin-", ".jar")
        zipTo(jarFile, baseDir)
        return jarFile
    }

    private fun classLoaderScopeRegistryFor(classPathRegistry: DefaultClassPathRegistry): DefaultClassLoaderScopeRegistry {
        val classPathSnapshotter = HashClassPathSnapshotter(DefaultHasher())
        val classLoaderFactory = DefaultHashingClassLoaderFactory(classPathSnapshotter)
        return DefaultClassLoaderScopeRegistry(
            DefaultClassLoaderRegistry(classPathRegistry, classLoaderFactory),
            DefaultClassLoaderCache(classLoaderFactory, classPathSnapshotter))
    }

    private fun scriptSourceFor(code: String): ScriptSource =
        UriScriptSource.file("script", createTempFile("script", ".gradle.kts").apply {
            writeText(code)
        })

    private fun zipTo(zipFile: File, baseDir: File) {
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            baseDir.walkTopDown().filter { it.isFile }.forEach {
                val path = it.relativeTo(baseDir).path
                val bytes = it.readBytes()
                zos.putNextEntry(ZipEntry(path).apply { size = bytes.size.toLong() })
                zos.write(bytes)
                zos.closeEntry()
            }
        }
    }
}
