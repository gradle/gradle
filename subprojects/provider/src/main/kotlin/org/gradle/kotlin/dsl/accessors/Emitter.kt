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

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.artifacts.Configuration

import org.gradle.kotlin.dsl.concurrent.WriterThread
import org.gradle.kotlin.dsl.support.bytecode.InternalName
import org.gradle.kotlin.dsl.support.bytecode.beginFileFacadeClassHeader
import org.gradle.kotlin.dsl.support.bytecode.beginPublicClass
import org.gradle.kotlin.dsl.support.bytecode.closeHeader
import org.gradle.kotlin.dsl.support.bytecode.endKotlinClass
import org.gradle.kotlin.dsl.support.bytecode.moduleFileFor
import org.gradle.kotlin.dsl.support.bytecode.moduleMetadataBytesFor
import org.gradle.kotlin.dsl.support.useToRun

import java.io.File


internal
fun emitAccessorsFor(
    projectSchema: ProjectSchema<TypeAccessibility>,
    srcDir: File,
    binDir: File
): List<InternalName> = writerThreadFor(srcDir, binDir).useToRun {

    val emittedClassNames =
        accessorsFor(projectSchema).map { accessor ->
            emitClassFor(accessor, srcDir, binDir)
        }.toList()

    writeFile(
        moduleFileFor(binDir),
        moduleMetadataBytesFor(emittedClassNames)
    )

    emittedClassNames
}


private
fun WriterThread.emitClassFor(accessor: Accessor, srcDir: File, binDir: File): InternalName {

    val (className, fragments) = fragmentsFor(accessor)
    val sourceCode = mutableListOf<String>()
    val metadataWriter = beginFileFacadeClassHeader()
    val classWriter = beginPublicClass(className)

    for ((source, bytecode, metadata, signature) in fragments) {
        sourceCode.add(source)
        MetadataFragmentScope(signature, metadataWriter).run(metadata)
        BytecodeFragmentScope(signature, classWriter).run(bytecode)
    }

    val sourceFile = srcDir.resolve("${className.value.removeSuffix("Kt")}.kt")
    io { writeAccessorsTo(sourceFile, sourceCode.asSequence()) }

    val classHeader = metadataWriter.closeHeader()
    val classBytes = classWriter.endKotlinClass(classHeader)
    val classFile = binDir.resolve("$className.class")
    writeFile(classFile, classBytes)

    return className
}


internal
fun writerThreadFor(srcDir: File, binDir: File): WriterThread = WriterThread().apply {
    io { makeAccessorOutputDirs(srcDir, binDir) }
}


internal
fun makeAccessorOutputDirs(srcDir: File, binDir: File) {
    srcDir.resolve(packagePath).mkdirs()
    binDir.resolve(packagePath).mkdirs()
    binDir.resolve("META-INF").mkdir()
}


internal
sealed class Accessor {

    data class ForConfiguration(val name: AccessorNameSpec) : Accessor()

    data class ForExtension(val spec: TypedAccessorSpec) : Accessor()

    data class ForConvention(val spec: TypedAccessorSpec) : Accessor()

    data class ForContainerElement(val spec: TypedAccessorSpec) : Accessor()

    data class ForTask(val spec: TypedAccessorSpec) : Accessor()
}


internal
fun accessorsFor(schema: ProjectSchema<TypeAccessibility>): Sequence<Accessor> = sequence {
    schema.run {
        AccessorScope().run {
            yieldAll(uniqueAccessorsFor(extensions).map(Accessor::ForExtension))
            yieldAll(uniqueAccessorsFor(conventions).map(Accessor::ForConvention))
            yieldAll(uniqueAccessorsFor(tasks).map(Accessor::ForTask))
            yieldAll(uniqueAccessorsFor(containerElements).map(Accessor::ForContainerElement))

            val configurationNames = configurations.map(::AccessorNameSpec).asSequence()
            yieldAll(
                uniqueAccessorsFrom(
                    configurationNames.map(::configurationAccessorSpec)
                ).map(Accessor::ForContainerElement)
            )
            yieldAll(configurationNames.map(Accessor::ForConfiguration))
        }
    }
}


private
fun configurationAccessorSpec(nameSpec: AccessorNameSpec) =
    TypedAccessorSpec(
        accessibleType<NamedDomainObjectContainer<Configuration>>(),
        nameSpec,
        accessibleType<Configuration>()
    )


private
inline fun <reified T> accessibleType() =
    TypeAccessibility.Accessible(SchemaType.of<T>())
