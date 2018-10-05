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

import kotlinx.metadata.ClassName
import kotlinx.metadata.Flag
import kotlinx.metadata.Flags
import kotlinx.metadata.KmAnnotation
import kotlinx.metadata.KmExtensionType
import kotlinx.metadata.KmPackageExtensionVisitor
import kotlinx.metadata.KmPackageVisitor
import kotlinx.metadata.KmPropertyExtensionVisitor
import kotlinx.metadata.KmPropertyVisitor
import kotlinx.metadata.KmTypeVisitor
import kotlinx.metadata.KmVariance
import kotlinx.metadata.KmVersionRequirementVisitor
import kotlinx.metadata.flagsOf
import kotlinx.metadata.jvm.JvmFieldSignature
import kotlinx.metadata.jvm.JvmMethodSignature
import kotlinx.metadata.jvm.JvmPackageExtensionVisitor
import kotlinx.metadata.jvm.JvmPropertyExtensionVisitor
import kotlinx.metadata.jvm.KmModuleVisitor
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.jvm.KotlinModuleMetadata

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer

import org.gradle.kotlin.dsl.execution.ALOAD
import org.gradle.kotlin.dsl.execution.ARETURN
import org.gradle.kotlin.dsl.execution.INVOKEINTERFACE
import org.gradle.kotlin.dsl.execution.LDC
import org.gradle.kotlin.dsl.execution.internalName
import org.gradle.kotlin.dsl.execution.method
import org.gradle.kotlin.dsl.execution.publicClass
import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.classLoaderFor
import org.gradle.kotlin.dsl.fixtures.eval
import org.gradle.kotlin.dsl.fixtures.testCompilationClassPath
import org.gradle.kotlin.dsl.support.compileToDirectory
import org.gradle.kotlin.dsl.support.loggerFor

import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Opcodes

import org.junit.Test

import java.io.File


val ConfigurationContainer.api: NamedDomainObjectProvider<Configuration>
    inline get() = named("api")


class AccessorBytecodeEmitterSpike : TestWithTempFiles() {

    @Test
    fun `spike inlined Configuration accessors`() {

        // given:
        val accessorsBinDir = newFolder("accessors")

        // when:
        AccessorBytecodeEmitter.emitKotlinExtensionsFor(
            sequenceOf(Accessor.ForConfiguration("api")),
            outputDir = accessorsBinDir
        )

        // then:
        // verify class
        classLoaderFor(accessorsBinDir).use {
            it.loadClass("org.gradle.kotlin.dsl.ConfigurationAccessorsKt").kotlin
        }

        val configuration = mock<NamedDomainObjectProvider<Configuration>>()
        val configurations = mock<ConfigurationContainer> {
            on { named(any<String>()) } doReturn configuration
        }
        eval(
            script = "val api = configurations.api",
            target = projectMockWith(configurations),
            scriptCompilationClassPath = testCompilationClassPath + classPathOf(accessorsBinDir),
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
        metadata.accept(PrintingVisitor.ForModule)
    }

    @Test
    fun `extract file metadata`() {

        val fileFacadeHeader = javaClass.classLoader
            .loadClass(javaClass.name + "Kt")
            .readKotlinClassHeader()

        val metadata = KotlinClassMetadata.read(fileFacadeHeader) as KotlinClassMetadata.FileFacade
        metadata.accept(PrintingVisitor.ForPackage)
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


internal
object AccessorBytecodeEmitter {

    fun emitKotlinExtensionsFor(accessors: Sequence<Accessor>, outputDir: File) {

        val metadataWriter = KotlinClassMetadata.FileFacade.Writer()

        val accessor = accessors.single() as Accessor.ForConfiguration
        val getterSignature = JvmMethodSignature(
            "get${accessor.configurationName.capitalize()}",
            "(L${ConfigurationContainer::class.internalName};)L${NamedDomainObjectProvider::class.internalName};"
        )

        metadataWriter.run {
            visitProperty(readOnlyPropertyFlags, accessor.configurationName, getterFlags, 6)!!.run {
                visitReceiverParameterType(0)!!.run {
                    visitClass(ConfigurationContainer::class.internalName)
                    visitEnd()
                }
                visitReturnType(0)!!.run {
                    visitClass(NamedDomainObjectProvider::class.internalName)
                    visitArgument(0, KmVariance.INVARIANT)!!.run {
                        visitClass(Configuration::class.internalName)
                        visitEnd()
                    }
                    visitEnd()
                }
                (visitExtensions(JvmPropertyExtensionVisitor.TYPE) as JvmPropertyExtensionVisitor).run {
                    visit(null, getterSignature, null)
                    visitSyntheticMethodForAnnotations(null)
                    visitEnd()
                }
                visitEnd()
            }
        }

        val header = metadataWriter.run {
            (visitExtensions(JvmPackageExtensionVisitor.TYPE) as KmPackageExtensionVisitor).run {
                visitEnd()
            }
            visitEnd()
            write().header
        }

        val className = "org/gradle/kotlin/dsl/ConfigurationAccessorsKt"
        val classBytes =
            publicClass(className) {
                visitKotlinMetadataAnnotation(header)
                method(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, getterSignature.name, getterSignature.desc) {
                    ALOAD(0)
                    LDC(accessor.configurationName)
                    INVOKEINTERFACE(ConfigurationContainer::class.internalName, "named", "(Ljava/lang/String;)L${NamedDomainObjectProvider::class.internalName};")
                    ARETURN()
                }
            }

        outputDir.resolve("$className.class").run {
            parentFile.mkdirs()
            writeBytes(classBytes)
        }

        // Write the module metadata
        val moduleBytes = KotlinModuleMetadata.Writer().run {
            visitPackageParts("org.gradle.kotlin.dsl", listOf(className), emptyMap())
            visitEnd()
            write().bytes
        }
        outputDir
            .resolve("META-INF").apply { mkdir() }
            .resolve("${outputDir.name}.kotlin_module")
            .writeBytes(moduleBytes)
    }

    private
    val readOnlyPropertyFlags = flagsOf(
        Flag.IS_PUBLIC,
        Flag.Property.HAS_GETTER,
        Flag.Property.IS_DECLARATION
    )

    private
    val getterFlags = flagsOf(
        Flag.IS_PUBLIC,
        Flag.PropertyAccessor.IS_NOT_DEFAULT,
        Flag.PropertyAccessor.IS_INLINE
    )
}


internal
sealed class Accessor {

    data class ForConfiguration(val configurationName: String) : Accessor()
}


/**
 * Writes the given [header] to the class file as a [kotlin.Metadata] annotation.
 **/
private
fun ClassWriter.visitKotlinMetadataAnnotation(header: KotlinClassHeader) {
    visitAnnotation("Lkotlin/Metadata;", true).run {
        visit("mv", header.metadataVersion)
        visit("bv", header.bytecodeVersion)
        visit("k", header.kind)
        visitArray("d1").run {
            header.data1.forEach { visit(null, it) }
            visitEnd()
        }
        visitArray("d2").run {
            header.data2.forEach { visit(null, it) }
            visitEnd()
        }
        visitEnd()
    }
}


object PrintingVisitor {

    object ForPackage : KmPackageVisitor() {

        override fun visitExtensions(type: KmExtensionType): KmPackageExtensionVisitor? {
            println("visitExtensions($type)")
            return object : JvmPackageExtensionVisitor() {
                override fun visitLocalDelegatedProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? {
                    println("visitLocalDelegatedProperty($flags, $name, $getterFlags, $setterFlags)")
                    return super.visitLocalDelegatedProperty(flags, name, getterFlags, setterFlags)
                }

                override fun visitEnd() {
                    println("visitEnd()")
                    super.visitEnd()
                }
            }
        }

        override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? {
            println("visitProperty($flags, $name, $getterFlags, $setterFlags)")
            return ForProperty
        }

        override fun visitEnd() {
            println("visitEnd()")
            super.visitEnd()
        }
    }

    object ForProperty : KmPropertyVisitor() {

        override fun visitExtensions(type: KmExtensionType): KmPropertyExtensionVisitor? {
            println("visitExtensions($type")
            return object : JvmPropertyExtensionVisitor() {
                override fun visit(fieldDesc: JvmFieldSignature?, getterDesc: JvmMethodSignature?, setterDesc: JvmMethodSignature?) {
                    println("visit($fieldDesc, $getterDesc, $setterDesc)")
                    super.visit(fieldDesc, getterDesc, setterDesc)
                }

                override fun visitSyntheticMethodForAnnotations(desc: JvmMethodSignature?) {
                    println("visitSyntheticMethodForAnnotations($desc)")
                    super.visitSyntheticMethodForAnnotations(desc)
                }

                override fun visitEnd() {
                    println("visitEnd()")
                    super.visitEnd()
                }
            }
        }

        override fun visitReceiverParameterType(flags: Flags): KmTypeVisitor? {
            println("visitReceiverParameterType($flags)")
            return ForType
        }

        override fun visitReturnType(flags: Flags): KmTypeVisitor? {
            println("visitReturnType($flags)")
            return ForType
        }

        override fun visitVersionRequirement(): KmVersionRequirementVisitor? {
            println("visitVersionRequirement()")
            return super.visitVersionRequirement()
        }

        override fun visitEnd() {
            println("visitEnd()")
            super.visitEnd()
        }
    }

    object ForType : KmTypeVisitor() {

        override fun visitClass(name: ClassName) {
            println("visitClass($name)")
            super.visitClass(name)
        }

        override fun visitArgument(flags: Flags, variance: KmVariance): KmTypeVisitor? {
            println("visitArgument($flags, $variance)")
            return ForType
        }

        override fun visitEnd() {
            println("visitEnd()")
            super.visitEnd()
        }
    }

    object ForModule : KmModuleVisitor() {
        override fun visitAnnotation(annotation: KmAnnotation) {
            println("visitAnnotation($annotation)")
            super.visitAnnotation(annotation)
        }

        override fun visitPackageParts(fqName: String, fileFacades: List<String>, multiFileClassParts: Map<String, String>) {
            println("visitPackageParts($fqName, $fileFacades, $multiFileClassParts")
            super.visitPackageParts(fqName, fileFacades, multiFileClassParts)
        }

        override fun visitEnd() {
            println("visitEnd()")
            super.visitEnd()
        }
    }
}
