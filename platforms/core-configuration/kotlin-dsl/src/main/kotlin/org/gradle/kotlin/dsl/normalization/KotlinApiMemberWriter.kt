/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.kotlin.dsl.normalization

import kotlinx.metadata.Flag
import kotlinx.metadata.KmDeclarationContainer
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.jvm.signature
import org.gradle.internal.tools.api.ApiClassExtractionException
import org.gradle.internal.tools.api.impl.AnnotationMember
import org.gradle.internal.tools.api.impl.ArrayAnnotationValue
import org.gradle.internal.tools.api.impl.ClassMember
import org.gradle.internal.tools.api.impl.FieldMember
import org.gradle.internal.tools.api.impl.InnerClassMember
import org.gradle.internal.tools.api.impl.JavaApiMemberWriter
import org.gradle.internal.tools.api.impl.MethodMember
import org.gradle.internal.tools.api.impl.SimpleAnnotationValue
import org.objectweb.asm.ClassVisitor


internal
class KotlinApiMemberWriter private constructor(apiMemberAdapter: ClassVisitor) : JavaApiMemberWriter(apiMemberAdapter) {

    private
    val kotlinMetadataAnnotationSignature = "Lkotlin/Metadata;"

    private
    val inlineFunctions: MutableSet<String> = HashSet()

    private
    val internalFunctions: MutableSet<String> = HashSet()

    companion object {
        fun adapter(): (ClassVisitor) -> KotlinApiMemberWriter {
            return ::KotlinApiMemberWriter
        }
    }

    override fun writeClass(classMember: ClassMember, methods: Set<MethodMember>, fields: Set<FieldMember>, innerClasses: Set<InnerClassMember>) {
        classMember.annotations.firstOrNull {
            it.name == kotlinMetadataAnnotationSignature
        }?.let {
            when (val kotlinMetadata = KotlinClassMetadata.read(parseKotlinClassHeader(it))) {
                is KotlinClassMetadata.Class -> kotlinMetadata.toKmClass().extractFunctionMetadata()
                is KotlinClassMetadata.FileFacade -> kotlinMetadata.toKmPackage().extractFunctionMetadata()
                is KotlinClassMetadata.MultiFileClassPart -> kotlinMetadata.toKmPackage().extractFunctionMetadata()
                is KotlinClassMetadata.MultiFileClassFacade -> {
                    // This metadata appears on a generated Java class resulting from @file:JvmName("ClassName") + @file:JvmMultiFileClass annotations in Kotlin scripts.
                    // The resulting facade class contains references to classes generated from each script pointing to this class.
                    // Each of those classes is visited separately and have KotlinClassMetadata.MultiFileClassPart on them
                }
                is KotlinClassMetadata.SyntheticClass -> {
                }
                is KotlinClassMetadata.Unknown -> {
                    throw ApiClassExtractionException("Unknown Kotlin metadata with kind: ${kotlinMetadata.header.kind} on class ${classMember.name} - " +
                        "this can happen if this class is compiled with a later Kotlin version than the Kotlin compiler used by Gradle")
                }
                null -> Unit
            }
        }

        super.writeClass(classMember, methods, fields, innerClasses)
    }

    override fun writeClassAnnotations(annotationMembers: Set<AnnotationMember>) {
        super.writeClassAnnotations(annotationMembers.filter { it.name != kotlinMetadataAnnotationSignature }.toSet())
    }

    override fun writeMethod(declaringInnerClass: InnerClassMember?, method: MethodMember) {
        when {
            method.isInternal() -> return
            method.isInline() -> throw publicInlineFunction(method)
            else -> super.writeMethod(declaringInnerClass, method)
        }
    }

    private
    fun KmDeclarationContainer.extractFunctionMetadata() {
        this.extractInternalFunctions()
        this.extractInlineFunctions()
    }

    private
    fun KmDeclarationContainer.extractInlineFunctions() {
        inlineFunctions.addAll(
            this.functions.asSequence()
                .filter { Flag.Function.IS_INLINE(it.flags) }
                .mapNotNull { it.signature?.asString() }
        )
    }

    private
    fun KmDeclarationContainer.extractInternalFunctions() {
        internalFunctions.addAll(
            this.functions.asSequence()
                .filter { Flag.IS_INTERNAL(it.flags) }
                .mapNotNull { it.signature?.asString() }
        )
    }

    private
    fun parseKotlinClassHeader(kotlinMetadataAnnotation: AnnotationMember): KotlinClassHeader {
        var kind: Int? = null
        var metadataVersion: IntArray? = null
        var data1: Array<String>? = null
        var data2: Array<String>? = null
        var extraString: String? = null
        var packageName: String? = null
        var extraInt: Int? = null
        kotlinMetadataAnnotation.values.forEach {
            // see Metadata.kt
            when (it) {
                is SimpleAnnotationValue ->
                    when (it.name) {
                        "k" -> kind = it.value as Int
                        "mv" -> metadataVersion = it.value as IntArray
                        "xs" -> extraString = it.value as String
                        "pn" -> packageName = it.value as String
                        "xi" -> extraInt = it.value as Int
                    }
                is ArrayAnnotationValue ->
                    when (it.name) {
                        "d1" -> data1 = it.value.map { arrayItem -> arrayItem.value as String }.toTypedArray()
                        "d2" -> data2 = it.value.map { arrayItem -> arrayItem.value as String }.toTypedArray()
                    }
            }
        }
        return KotlinClassHeader(kind, metadataVersion, data1, data2, extraString, packageName, extraInt)
    }

    private
    fun MethodMember.binarySignature() = this.name + this.typeDesc

    private
    fun MethodMember.isInternal() = internalFunctions.contains(this.binarySignature())

    private
    fun MethodMember.isInline() = inlineFunctions.contains(this.binarySignature())

    private
    fun publicInlineFunction(inlineFunction: MethodMember) =
        ApiClassExtractionException("inline fun ${inlineFunction.name}(): compile avoidance is not supported with public inline functions")
}
