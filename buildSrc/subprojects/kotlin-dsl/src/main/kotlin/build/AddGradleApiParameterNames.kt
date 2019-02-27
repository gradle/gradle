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

package build

import accessors.sourceSets
import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.gradlebuild.PublicApi
import org.gradle.kotlin.dsl.*

import org.gradle.kotlin.dsl.codegen.ParameterNamesSupplier
import org.gradle.kotlin.dsl.codegen.parameterNamesFor

import org.gradle.kotlin.dsl.support.GradleApiMetadata

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.ASM6
import org.objectweb.asm.Type
import java.io.File
import java.io.InputStream
import java.util.Properties
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.zip.ZipOutputStream


// TODO:kotlin-dsl dedupe, see MinifyPlugin
private
val minified = Attribute.of("minified", Boolean::class.javaObjectType)


fun Project.withCompileOnlyGradleApiModulesWithParameterNames(vararg gradleModuleNames: String) {

    val artifactType = Attribute.of("artifactType", String::class.java)
    val jarWithGradleApiParameterNames = "jar-with-gradle-api-parameter-names"

    val gradleApiWithParameterNames by configurations.registering {
        attributes {
            attribute(artifactType, jarWithGradleApiParameterNames)
        }
    }

    dependencies {
        registerTransform(AddGradleApiParameterNames::class) {
            from.attribute(artifactType, "jar").attribute(minified, true)
            to.attribute(artifactType, jarWithGradleApiParameterNames)
            parameters {
                publicApiIncludes = PublicApi.includes
                publicApiExcludes = PublicApi.excludes
            }
        }

        for (gradleModuleName in gradleModuleNames) {
            gradleApiWithParameterNames.name(project(gradleModuleName))
        }
    }

    sourceSets {
        "main" {
            compileClasspath += gradleApiWithParameterNames.get()
        }
    }
}


internal
abstract class AddGradleApiParameterNames : TransformAction<AddGradleApiParameterNames.Parameters> {

    interface Parameters : TransformParameters {
        @get:Input
        var publicApiIncludes: List<String>
        @get:Input
        var publicApiExcludes: List<String>
    }

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val input: File

    override fun transform(outputs: TransformOutputs) {
        if (input.name.startsWith("gradle-")) {
            transformGradleApiJar(input, outputs.file(outputFileNameFor(input)))
        } else {
            outputs.file(input)
        }
    }

    private
    fun outputFileNameFor(input: File) =
        "${input.nameWithoutExtension}-with-parameter-names.jar"

    private
    fun transformGradleApiJar(inputFile: File, outputFile: File) {
        JarFile(inputFile).use { inputJar ->
            val gradleApiMetadata = gradleApiMetadataFor(inputFile.gradleModuleName, inputJar)
            writingJar(outputFile) { zipOutputStream ->
                transformGradleApiJarEntries(gradleApiMetadata, inputJar, zipOutputStream)
            }
        }
    }

    private
    fun writingJar(outputJarFile: File, action: (ZipOutputStream) -> Unit) {
        outputJarFile.outputStream().buffered().use { fileStream ->
            ZipOutputStream(fileStream).use(action)
        }
    }

    private
    fun transformGradleApiJarEntries(gradleApiMetadata: GradleApiMetadata, inputJar: JarFile, zipOutputStream: ZipOutputStream) {
        inputJar.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
            if (gradleApiMetadata.isGradleApi(entry)) inputJar.transformJarEntry(gradleApiMetadata, entry, zipOutputStream)
            else inputJar.copyJarEntry(entry, zipOutputStream)
        }
    }

    private
    fun gradleApiMetadataFor(moduleName: String, inputJar: JarFile): GradleApiMetadata {
        val parameterNamesIndex = Properties().apply {
            inputJar.getJarEntry("$moduleName-parameter-names.properties")?.let { entry ->
                inputJar.getInputStream(entry).buffered().use { input -> load(input) }
            }
        }
        return GradleApiMetadata(
            parameters.publicApiIncludes,
            parameters.publicApiExcludes,
            { key: String -> parameterNamesIndex.getProperty(key, null)?.split(",") }
        )
    }

    private
    fun GradleApiMetadata.isGradleApi(entry: JarEntry) =
        entry.name.endsWith(".class")
            && !entry.name.endsWith("package-info.class")
            && spec.isSatisfiedBy(RelativePath.parse(true, entry.name))

    private
    fun JarFile.transformJarEntry(gradleApiMetadata: GradleApiMetadata, entry: JarEntry, zipOutputStream: ZipOutputStream) {
        getInputStream(entry).buffered().use { input ->
            zipOutputStream.run {
                putNextEntry(JarEntry(entry.name))
                write(transformClass(gradleApiMetadata, input))
                closeEntry()
            }
        }
    }

    private
    fun transformClass(gradleApiMetadata: GradleApiMetadata, input: InputStream): ByteArray {
        val writer = ClassWriter(0)
        val transformer = ParameterNamesClassVisitor(writer, gradleApiMetadata.parameterNamesSupplier)
        ClassReader(input).accept(transformer, 0)
        return writer.toByteArray()
    }

    private
    fun JarFile.copyJarEntry(entry: JarEntry, zipOutputStream: ZipOutputStream) {
        getInputStream(entry).buffered().use { input ->
            zipOutputStream.putNextEntry(JarEntry(entry.name))
            input.copyTo(zipOutputStream)
            zipOutputStream.closeEntry()
        }
    }
}


private
val gradleModuleNameRegex = Regex("-\\d.*")


private
val File.gradleModuleName: String
    get() = nameWithoutExtension.replace(gradleModuleNameRegex, "")


private
class ParameterNamesClassVisitor(
    delegate: ClassVisitor,
    private val parameterNamesSupplier: ParameterNamesSupplier
) : ClassVisitor(ASM6, delegate) {

    private
    lateinit var typeName: String

    override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
        typeName = name
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitMethod(access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?): MethodVisitor {
        return super.visitMethod(access, name, desc, signature, exceptions).apply {
            parameterNamesSupplier.parameterNamesForBinaryNames(typeName, name, desc)?.forEach { parameterName ->
                visitParameter(parameterName, 0)
            }
        }
    }

    private
    fun ParameterNamesSupplier.parameterNamesForBinaryNames(typeName: String, methodName: String, methodDescriptor: String) =
        parameterNamesFor(
            typeBinaryNameFor(typeName),
            methodName,
            parameterTypesBinaryNamesFor(methodDescriptor)
        )

    private
    fun typeBinaryNameFor(internalName: String): String =
        Type.getObjectType(internalName).className

    private
    fun parameterTypesBinaryNamesFor(methodDescriptor: String) =
        Type.getArgumentTypes(methodDescriptor).map { it.className }
}
