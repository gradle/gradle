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

@file:Suppress("DEPRECATION", "TYPEALIAS_EXPANSION_DEPRECATION") // todo: should not be necessary in the end

package org.gradle.kotlin.dsl.support.bytecode

import kotlinx.metadata.Flag
import kotlinx.metadata.Flags
import kotlinx.metadata.KmClassifier
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmPackage
import kotlinx.metadata.KmProperty
import kotlinx.metadata.KmType
import kotlinx.metadata.KmTypeParameter
import kotlinx.metadata.KmTypeProjection
import kotlinx.metadata.KmTypeVisitor
import kotlinx.metadata.KmValueParameter
import kotlinx.metadata.KmVariance
import kotlinx.metadata.flagsOf
import kotlinx.metadata.jvm.JvmMethodSignature
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.jvm.KotlinModuleMetadata
import kotlinx.metadata.jvm.getterSignature
import kotlinx.metadata.jvm.moduleName
import kotlinx.metadata.jvm.signature
import kotlinx.metadata.jvm.syntheticMethodForAnnotations
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import java.io.File


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
    return KotlinClassMetadata.writeFileFacade(this).annotationData
}


internal
fun moduleMetadataBytesFor(fileFacades: List<InternalName>): ByteArray =
    KotlinModuleMetadata.Writer().run {
        visitPackageParts("org.gradle.kotlin.dsl", fileFacades.map { it.value }, emptyMap())
        visitEnd()
        write().bytes
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
    publicStaticMethod(name, desc, signature, exceptions, deprecated, annotations, methodBody)
}


internal
fun ClassVisitor.publicStaticSyntheticMethod(
    signature: JvmMethodSignature,
    methodBody: MethodVisitor.() -> Unit
) = method(
    Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_SYNTHETIC,
    signature.name,
    signature.desc,
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
    flags: Flags = 0
): KmValueParameter {
    val kmValueParameter = KmValueParameter(flags, name)
    kmValueParameter.type = type
    return kmValueParameter
}


internal
fun newOptionalValueParameterOf(
    name: String,
    type: KmType
): KmValueParameter =
    newValueParameterOf(name, nullable(type), flagsOf(Flag.ValueParameter.DECLARES_DEFAULT_VALUE))


internal
fun newTypeParameterOf(
    flags: Flags = 0,
    name: String,
    id: Int = 0,
    variance: KmVariance,
    upperBound: KmType,
): KmTypeParameter {
    val kmTypeParameter = KmTypeParameter(flags, name, id, variance)
    kmTypeParameter.upperBounds += upperBound
    return kmTypeParameter
}


internal
fun newTypeOf(builder: KmTypeBuilder) = KmType(0).with(builder)


internal
fun nullable(kmType: KmType): KmType = kmType.also { it.flags = flagsOf(Flag.Type.IS_NULLABLE) }


internal
fun newClassTypeOf(
    name: String,
    vararg arguments: KmTypeProjection
): KmType {
    val kmType = KmType(0)
    kmType.classifier = KmClassifier.Class(name)
    kmType.arguments.addAll(arguments)
    return kmType
}


internal
fun newTypeParameterTypeOf(id: Int): KmType {
    val kmType = KmType(0)
    kmType.classifier = KmClassifier.TypeParameter(id)
    return kmType
}


internal
fun newFunctionOf(
    flags: Flags = publicFunctionFlags,
    receiverType: KmType,
    returnType: KmType,
    name: String,
    valueParameters: Iterable<KmValueParameter> = listOf(),
    typeParameters: Iterable<KmTypeParameter> = listOf(),
    signature: JvmMethodSignature
): KmFunction {
    val kmFunction = KmFunction(flags, name)
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
    getterFlags: Flags = inlineGetterFlags,
    receiverType: KmType,
    returnType: KmType,
    getterSignature: JvmMethodSignature
): KmProperty {
    val kmProperty = KmProperty(readOnlyPropertyFlags, name, getterFlags, 6)
    kmProperty.receiverParameterType = receiverType
    kmProperty.returnType = returnType
    kmProperty.getterSignature = getterSignature
    kmProperty.syntheticMethodForAnnotations = null
    return kmProperty
}


internal
inline fun <T : KmTypeVisitor?> T.with(builder: KmTypeBuilder): T {
    this!!.run {
        builder()
        visitEnd()
    }
    return this
}


internal
fun genericTypeOf(genericType: KmType, genericArgument: KmType): KmType {
    genericType.arguments += KmTypeProjection(KmVariance.INVARIANT, genericArgument)
    return genericType
}


internal
fun genericTypeOf(genericType: KmTypeBuilder, genericArguments: Iterable<KmTypeBuilder>): KmTypeBuilder = {
    genericType()
    genericArguments.forEach { argument ->
        visitArgument(0, KmVariance.INVARIANT)!!.run {
            argument()
            visitEnd()
        }
    }
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
typealias KmTypeBuilder = KmTypeVisitor.() -> Unit


internal
fun jvmGetterSignatureFor(propertyName: String, desc: String): JvmMethodSignature =
    // Accessors honor the kotlin property jvm interop convention.
    // The only difference with JavaBean 1.01 is to prefer `get` over `is` for boolean properties.
    // The following code also complies with Section 8.8 of the spec, "Capitalization of inferred names.".
    // Sun: "However to support the occasional use of all upper-case names,
    //       we check if the first two characters of the name are both upper case and if so leave it alone."
    JvmMethodSignature("get${propertyName.uppercaseFirstChar()}", desc)


private
val readOnlyPropertyFlags = flagsOf(
    Flag.IS_PUBLIC,
    Flag.Property.HAS_GETTER,
    Flag.Property.IS_DECLARATION
)


private
val inlineGetterFlags = flagsOf(
    Flag.IS_PUBLIC,
    Flag.PropertyAccessor.IS_NOT_DEFAULT,
    Flag.PropertyAccessor.IS_INLINE
)


internal
val publicFunctionFlags = flagsOf(Flag.IS_PUBLIC)


internal
val publicFunctionWithAnnotationsFlags = flagsOf(Flag.IS_PUBLIC, Flag.HAS_ANNOTATIONS)
