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

import com.nhaarman.mockito_kotlin.*

import kotlinx.metadata.*
import kotlinx.metadata.jvm.*

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer

import org.gradle.kotlin.dsl.accessors.AccessorBytecodeEmitter.emitExtensionsMultiThreaded
import org.gradle.kotlin.dsl.accessors.AccessorBytecodeEmitter.emitExtensionsSingleThreaded

import org.gradle.kotlin.dsl.execution.*

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

import java.util.concurrent.*

import kotlin.concurrent.thread


val ConfigurationContainer.api: NamedDomainObjectProvider<Configuration>
    inline get() = named("api")


class AccessorBytecodeEmitterSpike : TestWithTempFiles() {

    private
    val benchmarkConfig = BenchmarkConfig(0, 3)

    @Test
    fun `benchmark compiled accessors strategy`() {
        benchmarkWithConfigOnlySchema(::compiledAccessors)
    }

    @Test
    fun `benchmark bytecode accessors (single-threaded)`() {
        benchmarkWithConfigOnlySchema(::bytecodeAccessorsSingledThreaded)
    }

    @Test
    fun `benchmark bytecode accessors (multi-threaded)`() {
        benchmarkWithConfigOnlySchema(::bytecodeAccessorsMultiThreaded)
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
    fun bytecodeAccessorsMultiThreaded(projectSchema: ProjectSchema<String>, srcDir: File, binDir: File) {
        val sources = thread { emitSourcesFor(projectSchema, srcDir) }
        emitExtensionsMultiThreaded(accessorsForConfigurationsOf(projectSchema), binDir)
        sources.join()
    }

    private
    fun accessorsForConfigurationsOf(projectSchema: ProjectSchema<String>) =
        projectSchema.configurations.asSequence().map { Accessor.ForConfiguration(it) }

    private
    fun benchmarkWithConfigOnlySchema(f: (ProjectSchema<String>, File, File) -> Unit) {
        var run = 0
        val configOnlySchema = loadConfigurationSchema()
        val benchmarkResult = benchmark(benchmarkConfig) {
            configOnlySchema.forEach { (projectPath, projectSchema) ->
                val outputDir = newFolder("accessors", run.toString(), projectPath.replace(':', '_'))
                val srcDir = outputDir.resolve("src").apply { mkdir() }
                val binDir = outputDir.resolve("bin").apply { mkdir() }
                f(projectSchema, srcDir, binDir)
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
    fun loadConfigurationSchema(): Map<String, ProjectSchema<String>> {
        val helloAndroidProjectSchema = loadMultiProjectSchemaFromResource(
            "/gradle-project-schema.json"
//            "/hello-android-project-schema.json"
        )
        return configOnlySchemaFrom(helloAndroidProjectSchema)
    }

    private
    fun prettyPrint(currentResult: BenchmarkResult) {
        println("${currentResult.median.ms}ms (${currentResult.points.map { it.ms }})")
    }

    private
    fun configOnlySchemaFrom(schema: Map<String, ProjectSchema<String>>): Map<String, ProjectSchema<String>> = schema.mapValues { (_, v) ->
        ProjectSchema<String>(
            configurations = v.configurations,
            extensions = emptyList(),
            conventions = emptyList(),
            tasks = emptyList(),
            containerElements = emptyList()
        )
    }

    private
    fun loadMultiProjectSchemaFromResource(resourceName: String): Map<String, ProjectSchema<String>> =
        loadMultiProjectSchemaFrom(File(javaClass.getResource(resourceName).toURI()))

    @Test
    fun `spike inlined Configuration accessors`() {

        // given:
        val accessorsBinDir = newFolder("accessors")

        // when:
        emitExtensionsSingleThreaded(sequenceOf(Accessor.ForConfiguration("api")),
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

    fun emitExtensionsSingleThreaded(accessors: Sequence<Accessor>, outputDir: File) {

        val accessorGetterSignaturePairs = accessors.filterIsInstance<Accessor.ForConfiguration>().map { accessor ->
            accessor to jvmMethodSignatureFor(accessor)
        }.toList()

        val metadataWriter = KotlinClassMetadata.FileFacade.Writer()
        for ((accessor, getterSignature) in accessorGetterSignaturePairs) {
            metadataWriter.writeMetadataFor(accessor, getterSignature)
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
                for ((accessor, getterSignature) in accessorGetterSignaturePairs) {
                    method(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, getterSignature.name, getterSignature.desc) {
                        ALOAD(0)
                        LDC(accessor.configurationName)
                        INVOKEINTERFACE(configurationContainerInternalName, "named", namedMethodDescriptor)
                        ARETURN()
                    }
                }
            }

        outputDir.resolve("$className.class").run {
            parentFile.mkdirs()
            writeBytes(classBytes)
        }

        writeModuleMetadataFor(className, outputDir)
    }

    fun emitExtensionsMultiThreaded(accessors: Sequence<Accessor>, outputDir: File) {

        val executor = Executors.newFixedThreadPool(2)

        val headerQ = ArrayBlockingQueue<Request>(32)
        val header = executor.submit(Callable {
            emitKotlinClassHeader(headerQ)
        })

        val moduleQ = ArrayBlockingQueue<Request>(32)
        executor.submit {
            emitKotlinModule(moduleQ, header, outputDir)
        }

        accessors.filterIsInstance<Accessor.ForConfiguration>().forEach { accessor ->
            val r = Request.AccessorOf(accessor, jvmMethodSignatureFor(accessor))
            headerQ.put(r)
            moduleQ.put(r)
        }

        headerQ.put(Request.Done)
        moduleQ.put(Request.Done)

        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.DAYS)
    }

    sealed class Request {

        data class AccessorOf(val accessor: Accessor.ForConfiguration, val signature: JvmMethodSignature) : Request()

        object Done : Request()
    }

    private
    fun emitKotlinModule(moduleQ: ArrayBlockingQueue<Request>, header: Future<KotlinClassHeader>, outputDir: File) {

        val className = "org/gradle/kotlin/dsl/ConfigurationAccessorsKt"

        // Write the module metadata
        writeModuleMetadataFor(className, outputDir)

        val classBytes =
            publicClass(className) {
                moduleQ.forEachRequest {
                    method(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, signature.name, signature.desc) {
                        ALOAD(0)
                        LDC(accessor.configurationName)
                        INVOKEINTERFACE(configurationContainerInternalName, "named", namedMethodDescriptor)
                        ARETURN()
                    }
                }
                visitKotlinMetadataAnnotation(header.get())
            }

        outputDir.resolve("$className.class").run {
            parentFile.mkdirs()
            writeBytes(classBytes)
        }
    }

    private
    fun writeModuleMetadataFor(className: String, outputDir: File) {
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
    fun emitKotlinClassHeader(requests: ArrayBlockingQueue<Request>): KotlinClassHeader {

        val metadataWriter = KotlinClassMetadata.FileFacade.Writer()
        requests.forEachRequest {
            metadataWriter.writeMetadataFor(accessor, signature)
        }
        return metadataWriter.run {
            (visitExtensions(JvmPackageExtensionVisitor.TYPE) as KmPackageExtensionVisitor).run {
                visitEnd()
            }
            visitEnd()
            write().header
        }
    }

    private
    fun KotlinClassMetadata.FileFacade.Writer.writeMetadataFor(accessor: Accessor.ForConfiguration, getterSignature: JvmMethodSignature) {
        visitProperty(readOnlyPropertyFlags, accessor.configurationName, getterFlags, 6)!!.run {
            visitReceiverParameterType(0)!!.run {
                visitClass(configurationContainerInternalName)
                visitEnd()
            }
            visitReturnType(0)!!.run {
                visitClass(namedDomainObjectProviderInternalName)
                visitArgument(0, KmVariance.INVARIANT)!!.run {
                    visitClass(configurationInternalName)
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

    private
    fun jvmMethodSignatureFor(accessor: Accessor.ForConfiguration): JvmMethodSignature =
        JvmMethodSignature(
            "get${accessor.configurationName.capitalize()}",
            configurationAccessorMethodSignature
        )

    private
    val configurationContainerInternalName = ConfigurationContainer::class.internalName

    private
    val configurationInternalName = Configuration::class.internalName

    private
    val namedDomainObjectProviderInternalName = NamedDomainObjectProvider::class.internalName

    private
    val namedMethodDescriptor = "(Ljava/lang/String;)L$namedDomainObjectProviderInternalName;"

    private
    val configurationAccessorMethodSignature = "(L$configurationContainerInternalName;)L$namedDomainObjectProviderInternalName;"

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

    private
    fun <E> ArrayBlockingQueue<E>.forEachRequest(f: Request.AccessorOf.() -> Unit) {
        loop@ while (true) {
            when (val request = take()) {
                is Request.AccessorOf -> f(request)
                else -> break@loop
            }
        }
    }
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
