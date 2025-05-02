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

import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.initialization.Settings
import org.gradle.kotlin.dsl.concurrent.IO
import org.gradle.kotlin.dsl.concurrent.writeFile
import org.gradle.kotlin.dsl.support.bytecode.InternalName
import org.gradle.kotlin.dsl.support.bytecode.beginFileFacadeClassHeader
import org.gradle.kotlin.dsl.support.bytecode.beginPublicClass
import org.gradle.kotlin.dsl.support.bytecode.closeHeader
import org.gradle.kotlin.dsl.support.bytecode.endKotlinClass
import org.gradle.kotlin.dsl.support.bytecode.moduleFileFor
import org.gradle.kotlin.dsl.support.bytecode.moduleMetadataBytesFor
import java.io.File


internal
fun IO.emitAccessorsFor(
    projectSchema: ProjectSchema<TypeAccessibility>,
    srcDir: File,
    binDir: File?,
    outputPackage: OutputPackage,
    format: AccessorFormat
): List<InternalName> {

    makeAccessorOutputDirs(srcDir, binDir, outputPackage.path)

    // When building accessors for Settings, preserve semantics for existing custom accessors by
    // giving the generated accessors lower priority in Kotlin overload resolution.
    val useLowPriorityOverloadResolution = projectSchema.scriptTarget is Settings
    val moduleName = binDir?.name ?: "kotlin-dsl-accessors"
    val emittedClassNames =
        accessorsFor(projectSchema).map { accessor ->
            emitClassFor(
                accessor,
                srcDir,
                binDir,
                outputPackage,
                format,
                moduleName,
                useLowPriorityOverloadResolution
            )
        }.toList()

    if (binDir != null) {
        writeFile(
            moduleFileFor(binDir, moduleName),
            moduleMetadataBytesFor(emittedClassNames)
        )
    }

    return emittedClassNames
}


internal
fun IO.makeAccessorOutputDirs(srcDir: File, binDir: File?, packagePath: String) = io {
    srcDir.resolve(packagePath).mkdirs()
    binDir?.apply {
        resolve(packagePath).mkdirs()
        resolve("META-INF").mkdir()
    }
}


internal
data class OutputPackage(val name: String) {

    val path by lazy {
        name.replace('.', '/')
    }
}


private
fun IO.emitClassFor(
    accessor: Accessor,
    srcDir: File,
    binDir: File?,
    outputPackage: OutputPackage,
    format: AccessorFormat,
    moduleName: String,
    useLowPriorityOverloadResolution: Boolean
): InternalName {

    val (simpleClassName, fragments) = fragmentsFor(accessor)
    val className = InternalName("${outputPackage.path}/$simpleClassName")
    val sourceCode = mutableListOf<String>()

    fun collectSourceFragment(source: String) {
        sourceCode.add(format(source))
    }

    if (binDir != null) {
        writeAccessorsBytecodeTo(
            binDir,
            className,
            fragments,
            ::collectSourceFragment,
            moduleName,
            useLowPriorityOverloadResolution
        )
    } else {
        for ((source, _, _, _) in fragments) {
            collectSourceFragment(source)
        }
    }

    writeAccessorsTo(
        sourceFileFor(className, srcDir),
        sourceCode,
        importsRequiredBy(accessor),
        outputPackage.name
    )

    return className
}


private
fun sourceFileFor(className: InternalName, srcDir: File) =
    srcDir.resolve("${className.value.removeSuffix("Kt")}.kt")


private
fun IO.writeAccessorsBytecodeTo(
    binDir: File,
    className: InternalName,
    fragments: Sequence<AccessorFragment>,
    collectSourceFragment: (String) -> Unit,
    moduleName: String,
    useLowPriorityOverloadResolution: Boolean
) {

    val metadataWriter = beginFileFacadeClassHeader()
    val classWriter = beginPublicClass(className)

    for ((source, bytecode, metadata, signature) in fragments) {
        collectSourceFragment(source)
        MetadataFragmentScope(signature, metadataWriter, useLowPriorityOverloadResolution).run(metadata)
        BytecodeFragmentScope(signature, classWriter, useLowPriorityOverloadResolution).run(bytecode)
    }

    val metadata = metadataWriter.closeHeader(moduleName)
    val classBytes = classWriter.endKotlinClass(metadata)
    val classFile = binDir.resolve("$className.class")
    writeFile(classFile, classBytes)
}


private
fun importsRequiredBy(accessor: Accessor): List<String> = accessor.run {
    when (this) {
        is Accessor.ForExtension -> importsRequiredBy(spec.receiver, spec.type)
        is Accessor.ForConvention -> importsRequiredBy(spec.receiver, spec.type)
        is Accessor.ForTask -> importsRequiredBy(spec.type)
        is Accessor.ForContainerElement -> importsRequiredBy(spec.receiver, spec.type)
        is Accessor.ForModelDefault -> importsRequiredBy(spec.receiver, spec.type)
        is Accessor.ForSoftwareType -> importsRequiredBy(spec.modelType) + listOf(Incubating::class.java.name, Project::class.java.name)
        is Accessor.ForContainerElementFactory -> importsRequiredBy(spec.receiverType, spec.elementType) + listOf(Incubating::class.java.name)
        else -> emptyList()
    }
}


private
fun importsRequiredBy(vararg candidateTypes: TypeAccessibility): List<String> =
    importsRequiredBy(candidateTypes.asList())


internal
sealed class Accessor {

    data class ForConfiguration(val config: ConfigurationEntry<AccessorNameSpec>) : Accessor()

    data class ForExtension(val spec: TypedAccessorSpec) : Accessor()

    data class ForConvention(val spec: TypedAccessorSpec) : Accessor()

    data class ForContainerElement(val spec: TypedAccessorSpec) : Accessor()

    data class ForTask(val spec: TypedAccessorSpec) : Accessor()

    data class ForModelDefault(val spec: TypedAccessorSpec) : Accessor()

    data class ForSoftwareType(val spec: TypedSoftwareTypeEntry) : Accessor()

    data class ForContainerElementFactory(val spec: TypedContainerElementFactoryEntry) : Accessor()
}


internal
fun accessorsFor(schema: ProjectSchema<TypeAccessibility>): Sequence<Accessor> = sequence {
    schema.run {
        AccessorScope().run {
            yieldAll(uniqueAccessorsFor(extensions).map(Accessor::ForExtension))
            yieldAll(uniqueAccessorsFor(conventions).map(Accessor::ForConvention))
            yieldAll(uniqueAccessorsFor(tasks).map(Accessor::ForTask))
            yieldAll(uniqueAccessorsFor(containerElements).map(Accessor::ForContainerElement))

            val configurationNames = configurations.asSequence().mapNotNull { entry ->
                AccessorNameSpec.createOrNull(entry.target)?.let { accessorNameSpec -> entry.map { accessorNameSpec } }
            }
            yieldAll(
                uniqueAccessorsFrom(
                    configurationNames.map { it.target }.map(::configurationAccessorSpec)
                ).map(Accessor::ForContainerElement)
            )
            yieldAll(configurationNames.map(Accessor::ForConfiguration))

            yieldAll(uniqueAccessorsFor(modelDefaults).map(Accessor::ForModelDefault))
            yieldAll(uniqueSoftwareTypeEntries(softwareTypeEntries.mapNotNull(::typedSoftwareType)).map(Accessor::ForSoftwareType))
            yieldAll(uniqueContainerElementFactories(containerElementFactories.mapNotNull(::typedContainerElementFactory)).map(Accessor::ForContainerElementFactory))
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

private fun typedSoftwareType(softwareTypeEntry: SoftwareTypeEntry<TypeAccessibility>) : TypedSoftwareTypeEntry? {
    val name = AccessorNameSpec.createOrNull(softwareTypeEntry.softwareTypeName)
    return name?.let {
        TypedSoftwareTypeEntry(name, softwareTypeEntry.modelType)
    }
}

private fun typedContainerElementFactory(containerElementFactoryEntry: ContainerElementFactoryEntry<TypeAccessibility>) : TypedContainerElementFactoryEntry? {
    val name = AccessorNameSpec.createOrNull(containerElementFactoryEntry.factoryName)
    return name?.let {
        TypedContainerElementFactoryEntry(name, containerElementFactoryEntry.containerReceiverType, containerElementFactoryEntry.publicType)
    }
}

private
inline fun <reified T> accessibleType() =
    TypeAccessibility.Accessible(SchemaType.of<T>())
