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

package org.gradle.kotlin.dsl.support.bytecode

import org.gradle.kotlin.dsl.accessors.ExtensionSpec
import org.gradle.kotlin.dsl.accessors.accessorDescriptorFor
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import java.io.File
import kotlin.metadata.KmClassifier
import kotlin.metadata.KmFunction
import kotlin.metadata.KmPackage
import kotlin.metadata.KmProperty
import kotlin.metadata.KmPropertyAccessorAttributes
import kotlin.metadata.KmType
import kotlin.metadata.KmTypeParameter
import kotlin.metadata.KmTypeProjection
import kotlin.metadata.KmValueParameter
import kotlin.metadata.KmVariance
import kotlin.metadata.MemberKind
import kotlin.metadata.Visibility
import kotlin.metadata.declaresDefaultValue
import kotlin.metadata.isInline
import kotlin.metadata.isNotDefault
import kotlin.metadata.isNullable
import kotlin.metadata.jvm.JvmMetadataVersion
import kotlin.metadata.jvm.JvmMethodSignature
import kotlin.metadata.jvm.KmModule
import kotlin.metadata.jvm.KmPackageParts
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.KotlinModuleMetadata
import kotlin.metadata.jvm.UnstableMetadataApi
import kotlin.metadata.jvm.getterSignature
import kotlin.metadata.jvm.hasAnnotationsInBytecode
import kotlin.metadata.jvm.moduleName
import kotlin.metadata.jvm.signature
import kotlin.metadata.jvm.syntheticMethodForAnnotations
import kotlin.metadata.kind
import kotlin.metadata.visibility


internal
fun publicKotlinClass(
    internalClassName: InternalName,
    metadata: Metadata,
    classBody: ClassWriter.() -> Unit
): ByteArray = publicClass(internalClassName) {
    visitKotlinMetadataAnnotation(metadata)
    classBody()
}


internal
fun writeFileFacadeClassHeader(
    moduleName: String,
    kmPackage: KmPackage.() -> Unit
) = beginFileFacadeClassHeader().run {
    kmPackage()
    closeHeader(moduleName)
}


internal
fun beginFileFacadeClassHeader() = KmPackage()


internal
fun KmPackage.closeHeader(moduleName: String): Metadata {
    this.moduleName = moduleName
    return KotlinClassMetadata.FileFacade(this, JvmMetadataVersion.LATEST_STABLE_SUPPORTED, 0).write()
}


@OptIn(UnstableMetadataApi::class)
internal
fun moduleMetadataBytesFor(fileFacades: List<InternalName>): ByteArray {
    val kmModule = KmModule()
    kmModule.packageParts["org.gradle.kotlin.dsl"] = KmPackageParts(
        fileFacades.map { it.value }.toMutableList(),
        emptyMap<String, String>().toMutableMap()
    )
    return KotlinModuleMetadata(kmModule, JvmMetadataVersion.LATEST_STABLE_SUPPORTED).write()
}


internal
fun moduleFileFor(baseDir: File, moduleName: String) =
    baseDir.resolve("META-INF").resolve("$moduleName.kotlin_module")


internal
fun ClassVisitor.publicStaticMethod(
    jvmMethodSignature: JvmMethodSignature,
    signature: String? = null,
    exceptions: Array<String>? = null,
    deprecated: Boolean = false,
    annotations: MethodVisitor.() -> Unit = {},
    methodBody: MethodVisitor.() -> Unit
) = jvmMethodSignature.run {
    publicStaticMethod(name, descriptor, signature, exceptions, deprecated, annotations, methodBody)
}


internal
fun ClassVisitor.publicStaticSyntheticMethod(
    signature: JvmMethodSignature,
    methodBody: MethodVisitor.() -> Unit
) = method(
    Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_SYNTHETIC,
    signature.name,
    signature.descriptor,
    methodBody = methodBody
)


internal
fun ClassWriter.endKotlinClass(metadata: Metadata): ByteArray {
    visitKotlinMetadataAnnotation(metadata)
    return endClass()
}


/**
 * Writes the given [metadata] to the class file as a [kotlin.Metadata] annotation.
 **/
private
fun ClassWriter.visitKotlinMetadataAnnotation(metadata: Metadata) {
    visitAnnotation("Lkotlin/Metadata;", true).run {
        visit("mv", metadata.metadataVersion)
        visit("k", metadata.kind)
        visitArray("d1").run {
            metadata.data1.forEach { visit(null, it) }
            visitEnd()
        }
        visitArray("d2").run {
            metadata.data2.forEach { visit(null, it) }
            visitEnd()
        }
        visitEnd()
    }
}


internal
fun newValueParameterOf(
    name: String,
    type: KmType,
    declaresDefaultValue: Boolean = false
): KmValueParameter {
    val kmValueParameter = KmValueParameter(name)
    kmValueParameter.declaresDefaultValue = declaresDefaultValue
    kmValueParameter.type = type
    return kmValueParameter
}


internal
fun newOptionalValueParameterOf(
    name: String,
    type: KmType
): KmValueParameter =
    newValueParameterOf(name, nullable(type), declaresDefaultValue = true)


internal
fun newTypeParameterOf(
    name: String,
    id: Int = 0,
    variance: KmVariance,
    upperBound: KmType,
): KmTypeParameter {
    val kmTypeParameter = KmTypeParameter(name, id, variance)
    kmTypeParameter.upperBounds += upperBound
    return kmTypeParameter
}


internal
fun nullable(kmType: KmType): KmType = kmType.also { it.isNullable = true }


internal
fun newClassTypeOf(
    name: String,
    vararg arguments: KmTypeProjection
): KmType {
    val kmType = KmType()
    kmType.classifier = KmClassifier.Class(name)
    kmType.arguments.addAll(arguments)
    return kmType
}


internal
fun newTypeParameterTypeOf(id: Int): KmType {
    val kmType = KmType()
    kmType.classifier = KmClassifier.TypeParameter(id)
    return kmType
}


internal
fun KmPackage.addKmProperty(extensionSpec: ExtensionSpec, getterSignature: JvmMethodSignature) {
    properties += newPropertyOf(
        name = extensionSpec.name,
        getterAttributes = {
            visibility = Visibility.PUBLIC
            isNotDefault = true
        },
        receiverType = extensionSpec.receiverType.kmType,
        returnType = extensionSpec.returnType.kmType,
        getterSignature = getterSignature
    )
}


internal
fun newFunctionOf(
    functionAttributes: KmFunction.() -> Unit = publicFunctionAttributes,
    receiverType: KmType,
    returnType: KmType,
    name: String,
    valueParameters: Iterable<KmValueParameter> = listOf(),
    typeParameters: Iterable<KmTypeParameter> = listOf(),
    signature: JvmMethodSignature
): KmFunction {
    val kmFunction = KmFunction(name)
    functionAttributes(kmFunction)
    kmFunction.receiverParameterType = receiverType
    kmFunction.valueParameters.addAll(valueParameters)
    kmFunction.typeParameters.addAll(typeParameters)
    kmFunction.returnType = returnType
    kmFunction.signature = signature
    return kmFunction
}


internal
fun newPropertyOf(
    name: String,
    getterAttributes: KmPropertyAccessorAttributes.() -> Unit = inlineGetterAttributes,
    receiverType: KmType,
    returnType: KmType,
    getterSignature: JvmMethodSignature,
    propertyAttributes: KmProperty.() -> Unit = readOnlyPropertyAttributes,
): KmProperty {
    val kmProperty = KmProperty(name)
    // TODO setterFlags = 6 ... WTF
    propertyAttributes(kmProperty)
    getterAttributes(kmProperty.getter)
    kmProperty.receiverParameterType = receiverType
    kmProperty.returnType = returnType
    kmProperty.getterSignature = getterSignature
    kmProperty.syntheticMethodForAnnotations = getterSignature
    return kmProperty
}


internal
fun genericTypeOf(type: KmType, argument: KmType): KmType {
    type.arguments += KmTypeProjection(KmVariance.INVARIANT, argument)
    return type
}


internal
fun genericTypeOf(type: KmType, arguments: Iterable<KmTypeProjection>): KmType {
    type.arguments += arguments
    return type
}


internal
fun actionTypeOf(type: KmType): KmType =
    newClassTypeOf("org/gradle/api/Action", KmTypeProjection(KmVariance.INVARIANT, type))


internal
fun providerOfStar(): KmType =
    newClassTypeOf("org/gradle/api/provider/Provider", KmTypeProjection.STAR)


internal
fun providerConvertibleOfStar(): KmType =
    newClassTypeOf("org/gradle/api/provider/ProviderConvertible", KmTypeProjection.STAR)


internal
fun jvmGetterSignatureFor(pluginsExtension: ExtensionSpec): JvmMethodSignature = jvmGetterSignatureFor(
    pluginsExtension.name,
    accessorDescriptorFor(pluginsExtension.receiverType.internalName, pluginsExtension.returnType.internalName)
)


internal
fun jvmGetterSignatureFor(propertyName: String, desc: String): JvmMethodSignature =
// Accessors honor the kotlin property jvm interop convention.
// The only difference with JavaBean 1.01 is to prefer `get` over `is` for boolean properties.
// The following code also complies with Section 8.8 of the spec, "Capitalization of inferred names.".
// Sun: "However to support the occasional use of all upper-case names,
    //       we check if the first two characters of the name are both upper case and if so leave it alone."
    JvmMethodSignature("get${propertyName.uppercaseFirstChar()}", desc)


internal
val readOnlyPropertyAttributes: KmProperty.() -> Unit = {
    visibility = Visibility.PUBLIC
    kind = MemberKind.DECLARATION
}


internal
val inlineGetterAttributes: KmPropertyAccessorAttributes.() -> Unit = {
    visibility = Visibility.PUBLIC
    isNotDefault = true
    isInline = true
}


internal
val publicFunctionAttributes: KmFunction.() -> Unit = {
    visibility = Visibility.PUBLIC
}


internal
val publicFunctionWithAnnotationsAttributes: KmFunction.() -> Unit = {
    publicFunctionAttributes(this)
    hasAnnotationsInBytecode = true
}
