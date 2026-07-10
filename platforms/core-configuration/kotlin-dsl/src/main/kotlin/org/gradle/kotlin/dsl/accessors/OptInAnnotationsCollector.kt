/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.reflect.TypeOf
import org.gradle.kotlin.dsl.internal.sharedruntime.support.ClassBytesRepository
import org.jetbrains.kotlin.ir.types.IdSignatureValues.result
import org.jetbrains.org.objectweb.asm.AnnotationVisitor
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

sealed interface OptInRequirements {
    object None : OptInRequirements
    data class Annotations(val annotations: List<AnnotationRepresentation>) : OptInRequirements
    data class Unsatisfiable(val becauseOfTypes: Set<String>) : OptInRequirements
}

internal class OptInAnnotationsCollector(
    classBytesRepository: ClassBytesRepository,
    private val inaccessibilityReasonsProvider: (SchemaType) -> List<InaccessibilityReason>,
    private val optInRequirementsCache: (String, () -> OptInRequirements) -> OptInRequirements,
) {
    private val isOptInRequirementChecker = IsOptInRequirementChecker(classBytesRepository)

    fun collectOptInRequirementAnnotationsForType(type: SchemaType): OptInRequirements {
        val context = Context()

        val optInRequirements = generateSequence(type.value.concreteClass) { it.enclosingClass }.toList().flatMap { it.annotations.asList() }.filter {
            it.annotationClass.qualifiedName?.let(isOptInRequirementChecker::isOptInRequirement) ?: false
        }

        optInRequirements.forEach { annotation ->
            with(context) {
                addOptInAnnotation(annotationRepresentationFrom(annotation))
            }
        }

        return when {
            context.unsatisfiableOptIns?.isNotEmpty() == true -> OptInRequirements.Unsatisfiable(context.unsatisfiableOptIns!!)
            context.annotations?.isNotEmpty() == true -> OptInRequirements.Annotations(
                context.annotations!!.distinctBy { it.type.kotlinString } // avoid duplicates, as most annotations are non-repeatable
            )

            else -> OptInRequirements.None
        }
    }

    private inner class Context {
        var unsatisfiableOptIns: MutableSet<String>? = null

        var annotations: MutableList<AnnotationRepresentation>? = null

        fun addUnsatisfiableOptIns(names: Set<String>) {
            if (unsatisfiableOptIns == null)
                unsatisfiableOptIns = mutableSetOf()
            unsatisfiableOptIns!!.addAll(names)
        }

        fun addOptInAnnotation(annotation: AnnotationRepresentation) {
            if (annotations == null)
                annotations = mutableListOf()
            annotations!!.add(annotation)
        }

        fun Context.annotationRepresentationFrom(annotation: Annotation): AnnotationRepresentation {
            val type = useType(annotation.annotationClass.java)
            val values = annotation.annotationClass.java.declaredMethods
                .filter { it.parameterCount == 0 && it.defaultValue != it.invoke(annotation) }
                .sortedBy { it.name }
                .associateTo(mutableMapOf()) { method -> method.name to annotationValueRepresentationFrom(method.invoke(annotation)) }
            return AnnotationRepresentation(type, values)
        }

        fun Context.annotationValueRepresentationFrom(value: Any?): AnnotationValueRepresentation = when (value) {
            is Class<*> -> AnnotationValueRepresentation.ClassValue(useType(value))
            is Array<*> -> AnnotationValueRepresentation.ValueArray(value.map { annotationValueRepresentationFrom(it) })
            is Annotation -> AnnotationValueRepresentation.AnnotationValue(annotationRepresentationFrom(value))
            is Enum<*> -> AnnotationValueRepresentation.EnumValue(useType(value.javaClass), value.name)
            else -> AnnotationValueRepresentation.PrimitiveValue(value)
        }

        private fun Context.useType(javaClass: Class<*>): SchemaType =
            SchemaType(TypeOf.typeOf(javaClass)).also {
                val inaccessibilityReasons = inaccessibilityReasonsProvider(it)
                if (inaccessibilityReasons.isNotEmpty()) {
                    addUnsatisfiableOptIns(setOf(it.kotlinString))
                }

                when (val optInRequirements = optInRequirementsCache(it.kotlinString) { collectOptInRequirementAnnotationsForType(it) }) {
                    is OptInRequirements.Annotations -> optInRequirements.annotations.forEach(::addOptInAnnotation)
                    is OptInRequirements.Unsatisfiable -> addUnsatisfiableOptIns(optInRequirements.becauseOfTypes)
                    OptInRequirements.None -> Unit
                }
            }
    }
}

private class IsOptInRequirementChecker(val classBytesRepository: ClassBytesRepository) {
    private val isOptInRequirementClass = hashSetOf<String>()
    private val isNotOptInRequirementClass = hashSetOf<String>()

    fun isOptInRequirement(className: String): Boolean = when (className) {
        in isOptInRequirementClass -> true
        in isNotOptInRequirementClass -> false
        else -> {
            val result = classBytesRepository.classBytesFor(className)?.let(::isAnnotatedWithRequiresOptIn) ?: false
            (if (result) isOptInRequirementClass else isNotOptInRequirementClass)
                .add(className)
            result
        }
    }

    private fun isAnnotatedWithRequiresOptIn(classBytes: ByteArray): Boolean {
        val targetDescriptor = "Lkotlin/RequiresOptIn;"

        var isAnnotated = false
        val reader = ClassReader(classBytes)
        reader.accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
                if (descriptor == targetDescriptor) {
                    isAnnotated = true
                }
                return super.visitAnnotation(descriptor, visible)
            }
        }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)

        return isAnnotated
    }
}
