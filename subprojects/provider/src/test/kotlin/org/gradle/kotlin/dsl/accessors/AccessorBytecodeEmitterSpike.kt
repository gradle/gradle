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

import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.jvm.KotlinModuleMetadata

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.publish.PublishingExtension

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.testCompilationClassPath
import org.gradle.kotlin.dsl.support.compileToDirectory
import org.gradle.kotlin.dsl.support.loggerFor

import org.junit.Test


val ConfigurationContainer.api: NamedDomainObjectProvider<Configuration>
    inline get() = named("api")


fun DependencyHandler.`testCompile`(dependencyNotation: Any): Dependency? =
    add("testCompile", dependencyNotation)


/**
 * Configures the [publishing][org.gradle.api.publish.PublishingExtension] extension.
 */
fun org.gradle.api.Project.`publishing`(configure: Action<PublishingExtension>) {
    (this as ExtensionAware).extensions.configure("publishing", configure)
}


class AccessorBytecodeEmitterSpike : TestWithTempFiles() {

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
}
