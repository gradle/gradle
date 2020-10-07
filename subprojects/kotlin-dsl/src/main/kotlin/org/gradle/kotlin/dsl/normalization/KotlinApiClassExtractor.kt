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
import org.gradle.internal.normalization.java.ApiClassExtractor
import org.gradle.internal.normalization.java.impl.AnnotationMember
import org.gradle.internal.normalization.java.impl.ApiMemberWriter
import org.gradle.internal.normalization.java.impl.ArrayAnnotationValue
import org.gradle.internal.normalization.java.impl.ClassMember
import org.gradle.internal.normalization.java.impl.FieldMember
import org.gradle.internal.normalization.java.impl.InnerClassMember
import org.gradle.internal.normalization.java.impl.MethodMember
import org.gradle.internal.normalization.java.impl.MethodStubbingApiMemberAdapter
import org.gradle.internal.normalization.java.impl.SimpleAnnotationValue
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes


class KotlinApiClassExtractor : ApiClassExtractor(
    emptySet(),
    { classReader, classWriter ->
        KotlinApiMemberWriter(
            MethodStubbingApiMemberAdapter(classWriter),
            MethodCopyingApiMemberAdapter(classReader, classWriter)
        )
    }
)


private
class KotlinApiMemberWriter(apiMemberAdapter: ClassVisitor, val inlineMethodWriter: MethodCopyingApiMemberAdapter) : ApiMemberWriter(apiMemberAdapter) {

    val inlineFunctions: MutableSet<String> = HashSet()

    override fun writeClass(classMember: ClassMember, methods: Set<MethodMember>, fields: Set<FieldMember>, innerClasses: Set<InnerClassMember>) {
        classMember.annotations.firstOrNull {
            it.name == "Lkotlin/Metadata;"
        }?.let { annotationMember ->
            val kotlinHeader = parseKotlinClassHeader(annotationMember)
            when (val kotlinMetadata = KotlinClassMetadata.read(kotlinHeader)) {
                is KotlinClassMetadata.Class ->
                    inlineFunctions.addAll(extractInlineFunctions(kotlinMetadata.toKmClass()))
                is KotlinClassMetadata.FileFacade ->
                    inlineFunctions.addAll(extractInlineFunctions(kotlinMetadata.toKmPackage()))
                else -> {
                    // KotlinClassMetadata.SyntheticClass || KotlinClassMetadata.MultiFileClassFacade || KotlinClassMetadata.MultiFileClassPart || KotlinClassMetadata.Unknown
                }
            }
        }

        super.writeClass(classMember, methods, fields, innerClasses)
    }

    override fun writeMethod(method: MethodMember) {
        if (inlineFunctions.contains(method.name + method.typeDesc)) {
            inlineMethodWriter.writeMethod(method)
        } else {
            super.writeMethod(method)
        }
    }

    private
    fun extractInlineFunctions(container: KmDeclarationContainer) =
        container.functions
            .filter { Flag.Function.IS_INLINE(it.flags) }
            .mapNotNull { it.signature?.asString() }

    private
    fun parseKotlinClassHeader(kotlinMetadataAnnotation: AnnotationMember): KotlinClassHeader {
        var kind: Int? = null
        var metadataVersion: IntArray? = null
        var bytecodeVersion: IntArray? = null
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
                        "bv" -> bytecodeVersion = it.value as IntArray
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
        return KotlinClassHeader(kind, metadataVersion, bytecodeVersion, data1, data2, extraString, packageName, extraInt)
    }
}


private
class MethodCopyingApiMemberAdapter(val classReader: ClassReader, val classWriter: ClassWriter) {
    fun writeMethod(method: MethodMember) {
        classReader.accept(MethodCopyingVisitor(method, classWriter), ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    }
}


private
class MethodCopyingVisitor(val method: MethodMember, val classWriter: ClassWriter) : ClassVisitor(Opcodes.ASM7) {

    override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
        if (method.access == access && method.name == name && method.typeDesc == descriptor && method.signature == signature) {
            return classWriter.visitMethod(access, name, descriptor, signature, exceptions)
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions)
    }
}
