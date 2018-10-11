/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.kotlin.dsl.accessors

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.only
import com.nhaarman.mockito_kotlin.verify

import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.jvm.KotlinModuleMetadata

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer

import org.gradle.kotlin.dsl.accessors.AccessorBytecodeEmitter.emitExtensionsSingleThreaded
import org.gradle.kotlin.dsl.accessors.AccessorBytecodeEmitter.emitExtensionsWithOneClassPerConfiguration

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.classLoaderFor
import org.gradle.kotlin.dsl.fixtures.eval
import org.gradle.kotlin.dsl.fixtures.testCompilationClassPath

import org.gradle.kotlin.dsl.support.compileToDirectory
import org.gradle.kotlin.dsl.support.loggerFor
import org.gradle.kotlin.dsl.support.useToRun

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.File


val ConfigurationContainer.api: NamedDomainObjectProvider<Configuration>
    inline get() = named("api")


class AccessorBytecodeEmitterSpike : TestWithTempFiles() {

    private
    val benchmarkConfig = BenchmarkConfig(0, 5)

    @Test
    fun `verify bytecode accessors (one class per configuration)`() {
        verifyAccessorsProducedBy { accessors, srcDir, binDir ->
            val internalNamesOfEmittedClasses =
                emitExtensionsWithOneClassPerConfiguration(
                    configOnlySchemaWith(
                        accessors.filterIsInstance<Accessor.ForConfiguration>().map { it.configurationName }.toList()
                    ),
                    srcDir,
                    binDir
                )
            internalNamesOfEmittedClasses.map {
                it.replace('/', '.')
            }
        }
    }

    @Test
    fun `verify bytecode accessors (single-threaded)`() {
        verifyAccessorsProducedBy { accessors, _, binDir ->
            emitExtensionsSingleThreaded(accessors, binDir)
            listOf("org.gradle.kotlin.dsl.ConfigurationAccessorsKt")
        }
    }

    @Test
    fun `benchmark bytecode accessors (one class per configuration)`() {
        benchmarkWithConfigOnlySchema(::bytecodeAccessorsWithOneClassPerConfiguration)
    }

    @Test
    fun `benchmark bytecode accessors (single-threaded)`() {
        benchmarkWithConfigOnlySchema(::bytecodeAccessorsSingledThreaded)
    }

    @Test
    fun `benchmark compiled accessors strategy (baseline)`() {
        benchmarkWithConfigOnlySchema(::compiledAccessors)
    }

    @Test
    fun `benchmark overhead`() {
        benchmarkWithConfigOnlySchema { _, _, _ -> }
    }

    private
    fun bytecodeAccessorsWithOneClassPerConfiguration(projectSchema: ProjectSchema<String>, srcDir: File, binDir: File) {
        emitExtensionsWithOneClassPerConfiguration(projectSchema, srcDir, binDir)
    }

    private
    fun compiledAccessors(projectSchema: ProjectSchema<String>, srcDir: File, binDir: File) {
        buildAccessorsFor(projectSchema, testCompilationClassPath, srcDir, binDir)
    }

    private
    fun bytecodeAccessorsSingledThreaded(projectSchema: ProjectSchema<String>, srcDir: File, binDir: File) {
        emitSourcesFor(projectSchema, srcDir)
        emitExtensionsSingleThreaded(accessorsForConfigurationsOf(projectSchema), binDir)
    }

    private
    fun benchmarkWithConfigOnlySchema(experiment: (ProjectSchema<String>, File, File) -> Unit) {
        var run = 0
        val configOnlySchema = loadConfigurationSchema()
        val benchmarkResult = benchmark(benchmarkConfig) {
            configOnlySchema.forEach { (projectPath, projectSchema) ->
                val outputDir = newFolder("accessors", run.toString(), projectPath.replace(':', '_'))
                val srcDir = outputDir.resolve("src").apply { mkdir() }
                val binDir = outputDir.resolve("bin").apply { mkdir() }
                experiment(projectSchema, srcDir, binDir)
                run += 1
            }
        }
        prettyPrint(benchmarkResult)
    }

    /**
     * Account for source code generation time.
     */
    private
    fun emitSourcesFor(projectSchema: ProjectSchema<String>, file: File) {
        val srcDir = file
        writeAccessorsTo(
            srcDir.resolve("ConfigurationAccessors.kt"),
            projectSchema.configurationAccessors()
        )
    }

    // Compare only the time required to generate the configuration accessors
    // it's still not completely fair because of all the overloads
    // in the source code based version but it's enough for a ballpark figure
    private
    fun loadConfigurationSchema(): Map<String, ProjectSchema<String>> =
        configOnlySchemaFrom(
            multiProjectSchemaFromResource(
//                "/gradle-project-schema.json"
                "/hello-android-project-schema.json"
            )
        )

    private
    fun prettyPrint(result: BenchmarkResult) {
        println("${result.median.ms}ms (${result.points.map { it.ms }})")
    }

    private
    fun configOnlySchemaFrom(schema: Map<String, ProjectSchema<String>>): Map<String, ProjectSchema<String>> =
        schema.mapValues { (_, v) -> configOnlySchemaWith(v.configurations) }

    private
    fun configOnlySchemaWith(configurations: List<String>) = ProjectSchema<String>(
        configurations = configurations,
        extensions = emptyList(),
        conventions = emptyList(),
        tasks = emptyList(),
        containerElements = emptyList()
    )

    private
    fun multiProjectSchemaFromResource(resourceName: String): Map<String, ProjectSchema<String>> =
        loadMultiProjectSchemaFrom(File(javaClass.getResource(resourceName).toURI()))

    private
    fun verifyAccessorsProducedBy(f: (Sequence<Accessor>, File, File) -> List<String>) {

        // given:
        val outputDir = newFolder("accessors")
        val binDir = outputDir.resolve("bin").apply { mkdir() }
        val srcDir = outputDir.resolve("bin").apply { mkdir() }
        val accessors = sequenceOf(Accessor.ForConfiguration("api"))

        // when:
        val classNames = f(accessors, srcDir, binDir)

        // then:
        // verify classes
        classLoaderFor(binDir).useToRun {
            classNames.forEach {
                assertThat(
                    loadClass(it).kotlin.qualifiedName,
                    equalTo(it)
                )
            }
        }

        val configuration = mock<NamedDomainObjectProvider<Configuration>>()
        val configurations = mock<ConfigurationContainer> {
            on { named(any<String>()) } doReturn configuration
        }
        eval(
            script = "val api = configurations.api",
            target = projectMockWith(configurations),
            scriptCompilationClassPath = testCompilationClassPath + classPathOf(binDir),
            baseCacheDir = newFolder("cache")
        )

        verify(configurations, only()).named("api")
    }

    @Test
    fun `extract module metadata`() {

        val outputDir = newFolder("main")
        require(compileToDirectory(
            outputDir,
            listOf(
                file("ConfigurationAccessors.kt").apply {
                    writeText("""
                        package org.gradle.kotlin.dsl

                        import org.gradle.api.artifacts.*

                        val ConfigurationContainer.api: Configuration
                            inline get() = TODO()
                    """)
                }
            ),
            loggerFor<AccessorBytecodeEmitterSpike>(),
            testCompilationClassPath.asFiles
        ))

        val bytes = outputDir.resolve("META-INF/main.kotlin_module").readBytes()
        val metadata = KotlinModuleMetadata.read(bytes)!!
        metadata.accept(KotlinMetadataPrintingVisitor.ForModule)
    }

    @Test
    fun `extract file metadata`() {

        val fileFacadeHeader = javaClass.classLoader
            .loadClass(javaClass.name + "Kt")
            .readKotlinClassHeader()

        val metadata = KotlinClassMetadata.read(fileFacadeHeader) as KotlinClassMetadata.FileFacade
        metadata.accept(KotlinMetadataPrintingVisitor.ForPackage)
    }

    private
    fun Class<*>.readKotlinClassHeader(): KotlinClassHeader =
        getAnnotation(Metadata::class.java).run {
            KotlinClassHeader(
                kind,
                metadataVersion,
                bytecodeVersion,
                data1,
                data2,
                extraString,
                packageName,
                extraInt
            )
        }

    private
    fun projectMockWith(configurations: ConfigurationContainer): Project = mock {
        on { getConfigurations() } doReturn configurations
    }
}
