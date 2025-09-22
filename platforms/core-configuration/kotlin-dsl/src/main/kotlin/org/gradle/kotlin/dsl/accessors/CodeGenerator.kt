/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.plugins.ExtensionAware

import org.gradle.kotlin.dsl.support.unsafeLazy
import org.gradle.util.internal.TextUtil

import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens


internal
data class AccessorScope(
    private val targetTypesByName: HashMap<AccessorNameSpec, HashSet<TypeAccessibility.Accessible>> = hashMapOf(),
    private val softwareFeatureEntriesByName: HashMap<AccessorNameSpec, HashSet<TypedSoftwareFeatureEntry>> = hashMapOf(),
    private val containerElementFactoriesByName: HashMap<AccessorNameSpec, HashSet<TypedContainerElementFactoryEntry>> = hashMapOf(),
) {
    fun uniqueAccessorsFor(entries: Iterable<ProjectSchemaEntry<TypeAccessibility>>): Sequence<TypedAccessorSpec> =
        uniqueAccessorsFrom(entries.asSequence().mapNotNull(::typedAccessorSpec))

    fun uniqueAccessorsFrom(accessorSpecs: Sequence<TypedAccessorSpec>): Sequence<TypedAccessorSpec> =
        accessorSpecs.filter(::add)

    fun uniqueSoftwareFeatureEntries(softwareFeatureEntries: Iterable<TypedSoftwareFeatureEntry>): Sequence<TypedSoftwareFeatureEntry> =
        softwareFeatureEntries.asSequence().filter(::add)

    fun uniqueContainerElementFactories(elementFactoryEntries: Iterable<TypedContainerElementFactoryEntry>): Sequence<TypedContainerElementFactoryEntry> =
        elementFactoryEntries.asSequence().filter(::add)

    private fun add(softwareFeatureEntry: TypedSoftwareFeatureEntry): Boolean =
        softwareFeatureEntriesByName.getOrPut(softwareFeatureEntry.softwareFeatureName) { hashSetOf() }.add(softwareFeatureEntry)

    private fun add(containerElementFactory: TypedContainerElementFactoryEntry): Boolean =
        containerElementFactoriesByName.getOrPut(containerElementFactory.name) { hashSetOf() }.add(containerElementFactory)

    private
    fun add(accessorSpec: TypedAccessorSpec) =
        targetTypesOf(accessorSpec.name).add(accessorSpec.receiver)

    private
    fun targetTypesOf(accessorNameSpec: AccessorNameSpec): HashSet<TypeAccessibility.Accessible> =
        targetTypesByName.computeIfAbsent(accessorNameSpec) { hashSetOf() }
}


internal
fun extensionAccessor(spec: TypedAccessorSpec): String = spec.run {
    when (type) {
        is TypeAccessibility.Accessible -> accessibleExtensionAccessorFor(
            receiver.type.kotlinString,
            name,
            type.type.kotlinString,
            type.deprecation(),
            uniqueOptInAnnotations(receiver, type)
        )
        is TypeAccessibility.Inaccessible -> inaccessibleExtensionAccessorFor(receiver.type.kotlinString, name, type)
    }
}

internal fun maybeDeprecationAnnotations(deprecation: Deprecated?): String {
    fun deprecatedAnnotation(deprecation: Deprecated) =
        "@Deprecated(\"${TextUtil.escapeString(deprecation.message)}\", level = DeprecationLevel.${deprecation.level.name})"

    return when (deprecation?.level) {
        null -> ""

        DeprecationLevel.WARNING -> """
        |@Suppress("deprecation")
        |        ${deprecatedAnnotation(deprecation)}
        """.trimMargin() + "\n        "

        DeprecationLevel.ERROR -> """
        |@Suppress("DEPRECATION_ERROR")
        |        ${deprecatedAnnotation(deprecation)}
        """.trimIndent() + "\n        "

        DeprecationLevel.HIDDEN -> ""
    }
}

internal fun maybeOptInAnnotationSource(vararg types: TypeAccessibility) =
    maybeOptInAnnotationSource(uniqueOptInAnnotations(*types))

private fun uniqueOptInAnnotations(vararg types: TypeAccessibility): List<AnnotationRepresentation> =
    types.filterIsInstance<TypeAccessibility.Accessible>().flatMap { it.optInRequirements }.distinctBy { it.type.kotlinString }

internal fun maybeOptInAnnotationSource(optInAnnotations: List<AnnotationRepresentation>): String {
    val annotationSources = object {
        private fun annotationValueSource(annotationValueRepresentation: AnnotationValueRepresentation): String = when (annotationValueRepresentation) {
            is AnnotationValueRepresentation.AnnotationValue -> annotationSource(annotationValueRepresentation.representation, asValue = true)
            is AnnotationValueRepresentation.ClassValue -> annotationValueRepresentation.type.kotlinString + "::class"
            is AnnotationValueRepresentation.EnumValue -> annotationValueRepresentation.type.kotlinString + "." + annotationValueRepresentation.entryName
            is AnnotationValueRepresentation.PrimitiveValue -> annotationValueRepresentation.value.let { if (it is String) "\"$it\"" else it.toString() }
            is AnnotationValueRepresentation.ValueArray -> annotationValueRepresentation.elements.joinToString(", ", "[", "]") { annotationValueSource(it) }
        }

        fun annotationSource(annotationRepresentation: AnnotationRepresentation, asValue: Boolean = false): String = buildString {
            if (!asValue) {
                append("@")
            }
            append(annotationRepresentation.type.kotlinString)
            if (annotationRepresentation.values.isNotEmpty() || asValue) {
                append("(")
                annotationRepresentation.values.entries.forEachIndexed { index, (name, value) ->
                    if (name != "value" || annotationRepresentation.values.size > 1) {
                        append(name)
                        append(" = ")
                    }
                    append(annotationValueSource(value))
                    if (index != annotationRepresentation.values.size - 1) append(", ")
                }
                append(")")
            }
        }
    }


    return optInAnnotations.map { annotationSources.annotationSource(it) }.let {
        if (it.isNotEmpty()) {
            buildString {
                append(it.first())
                if (it.size > 1) {
                    appendLine()
                    it.subList(1, it.size).forEach { appendLine("        $it") }
                    append("        ")
                } else {
                    append("\n        ")
                }
            }
        } else ""
    }
}


private
fun accessibleExtensionAccessorFor(
    targetType: String,
    name: AccessorNameSpec,
    type: String,
    deprecation: Deprecated?,
    optInAnnotations: List<AnnotationRepresentation>
): String = name.run {
    val annotations = "${maybeDeprecationAnnotations(deprecation)}${maybeOptInAnnotationSource(optInAnnotations)}"
    """
        /**
         * Retrieves the [$original][$type] extension.
         */
        ${annotations}val $targetType.`$kotlinIdentifier`: $type get() =
            $thisExtensions.getByName("$stringLiteral") as $type

        /**
         * Configures the [$original][$type] extension.
         */
        ${annotations}fun $targetType.`$kotlinIdentifier`(configure: Action<$type>): Unit =
            $thisExtensions.configure("$stringLiteral", configure)

    """.trimMargin()
}


private
fun inaccessibleExtensionAccessorFor(targetType: String, name: AccessorNameSpec, typeAccess: TypeAccessibility.Inaccessible): String = name.run {
    """
        /**
         * Retrieves the `$original` extension.
         *
         * ${documentInaccessibilityReasons(name, typeAccess)}
         */
        val $targetType.`$kotlinIdentifier`: Any get() =
            $thisExtensions.getByName("$stringLiteral")

        /**
         * Configures the `$original` extension.
         *
         * ${documentInaccessibilityReasons(name, typeAccess)}
         */
        fun $targetType.`$kotlinIdentifier`(configure: Action<Any>): Unit =
            $thisExtensions.configure("$stringLiteral", configure)

    """
}


internal
fun existingTaskAccessor(spec: TypedAccessorSpec): String = spec.run {
    when (type) {
        is TypeAccessibility.Accessible -> accessibleExistingTaskAccessorFor(name, type.type.kotlinString, spec.type.deprecation(), spec.type.requiredOptIns().orEmpty())
        is TypeAccessibility.Inaccessible -> inaccessibleExistingTaskAccessorFor(name, type)
    }
}


private
fun accessibleExistingTaskAccessorFor(name: AccessorNameSpec, type: String, deprecation: Deprecated?, requiredOptIns: List<AnnotationRepresentation>): String {
    val annotations = "${maybeDeprecationAnnotations(deprecation)}${maybeOptInAnnotationSource(requiredOptIns)}"
    return name.run {
        """
        /**
         * Provides the existing [$original][$type] task.
         */
        ${annotations}val TaskContainer.`$kotlinIdentifier`: TaskProvider<$type>
            get() = named<$type>("$stringLiteral")

    """
    }
}


private
fun inaccessibleExistingTaskAccessorFor(name: AccessorNameSpec, typeAccess: TypeAccessibility.Inaccessible): String = name.run {
    """
        /**
         * Provides the existing `$original` task.
         *
         * ${documentInaccessibilityReasons(name, typeAccess)}
         */
        val TaskContainer.`$kotlinIdentifier`: TaskProvider<Task>
            get() = named("$stringLiteral")

    """
}


internal
fun existingContainerElementAccessor(spec: TypedAccessorSpec): String = spec.run {
    when (type) {
        is TypeAccessibility.Accessible -> accessibleExistingContainerElementAccessorFor(receiver.type.kotlinString, name, type.type.kotlinString, type.deprecation(), uniqueOptInAnnotations(spec.type))
        is TypeAccessibility.Inaccessible -> inaccessibleExistingContainerElementAccessorFor(receiver.type.kotlinString, name, type)
    }
}


private
fun accessibleExistingContainerElementAccessorFor(targetType: String, name: AccessorNameSpec, type: String, deprecation: Deprecated?, optIns: List<AnnotationRepresentation>): String = name.run {
    val annotations = "${maybeDeprecationAnnotations(deprecation)}${maybeOptInAnnotationSource(optIns)}"

    """
        /**
         * Provides the existing [$original][$type] element.
         */
        ${annotations}val $targetType.`$kotlinIdentifier`: NamedDomainObjectProvider<$type>
            get() = named<$type>("$stringLiteral")

    """
}


private
fun inaccessibleExistingContainerElementAccessorFor(containerType: String, name: AccessorNameSpec, elementType: TypeAccessibility.Inaccessible): String = name.run {
    """
        /**
         * Provides the existing `$original` element.
         *
         * ${documentInaccessibilityReasons(name, elementType)}
         */
        val $containerType.`$kotlinIdentifier`: NamedDomainObjectProvider<Any>
            get() = named("$stringLiteral")

    """
}


internal
fun modelDefaultAccessor(spec: TypedAccessorSpec): String = spec.run {
    when (type) {
        is TypeAccessibility.Accessible -> accessibleModelDefaultAccessorFor(name, type.type.kotlinString, type.deprecation(), type.optInRequirements)
        is TypeAccessibility.Inaccessible -> inaccessibleModelDefaultAccessorFor(name, type)
    }
}


private
fun accessibleModelDefaultAccessorFor(name: AccessorNameSpec, type: String, deprecation: Deprecated?, optIns: List<AnnotationRepresentation>): String = name.run {
    val annotations = """${maybeDeprecationAnnotations(deprecation)}${maybeOptInAnnotationSource(optIns)}"""
    """
    |        /**
    |         * Adds model defaults for the [$original][$name] software type.
    |         */
    |        ${annotations}fun SharedModelDefaults.`$kotlinIdentifier`(configure: Action<$type>): Unit =
    |            add("$stringLiteral", $type, configure)
    """.trimMargin()
}


private
fun inaccessibleModelDefaultAccessorFor(name: AccessorNameSpec, typeAccess: TypeAccessibility.Inaccessible): String = name.run {
    """
        /**
         * Adds model defaults for the `$original` software type.
         *
         * ${documentInaccessibilityReasons(name, typeAccess)}
         */
        fun SharedModelDefaults.`$kotlinIdentifier`(configure: Action<Any>): Unit =
            add("$stringLiteral", KotlinType.Any, configure)

    """
}


private
val thisExtensions =
    "(this as ${ExtensionAware::class.java.name}).extensions"


internal
class AccessorNameSpec private constructor(val original: String) {
    val kotlinIdentifier: String
        get() = original

    val stringLiteral by unsafeLazy {
        stringLiteralFor(original)
    }

    companion object {
        /**
         * Create a new [AccessorNameSpec], if [original] is valid.
         * Else, return `null`.
         */
        internal
        fun createOrNull(original: String): AccessorNameSpec? =
            if (isLegalAccessorName(original)) AccessorNameSpec(original)
            else null

        private
        fun isLegalAccessorName(name: String): Boolean =
            isKotlinIdentifier("`$name`")
                && name.indexOfAny(invalidNameChars) < 0

        private
        val invalidNameChars = charArrayOf('.', '/', '\\')

        private
        fun isKotlinIdentifier(candidate: String): Boolean =
            KotlinLexer().run {
                start(candidate)
                tokenStart == 0
                    && tokenEnd == candidate.length
                    && tokenType == KtTokens.IDENTIFIER
            }
    }

    override fun toString(): String = "AccessorNameSpec(original=$original)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AccessorNameSpec
        return original == other.original
    }

    override fun hashCode(): Int = original.hashCode()
}


internal
data class TypedAccessorSpec(
    val receiver: TypeAccessibility.Accessible,
    val name: AccessorNameSpec,
    val type: TypeAccessibility
)

internal
data class TypedSoftwareFeatureEntry(
    val softwareFeatureName: AccessorNameSpec,
    val modelType: TypeAccessibility,
    val targetType: TypeAccessibility
)

internal
data class TypedContainerElementFactoryEntry(
    val name: AccessorNameSpec,
    val receiverType: TypeAccessibility,
    val elementType: TypeAccessibility,
)


private
fun stringLiteralFor(original: String) =
    escapeStringTemplateDollarSign(original)


private
fun escapeStringTemplateDollarSign(string: String) =
    string.replace("${'$'}", "${'$'}{'${'$'}'}")


private
fun typedAccessorSpec(schemaEntry: ProjectSchemaEntry<TypeAccessibility>): TypedAccessorSpec? {
    val accessorName = AccessorNameSpec.createOrNull(schemaEntry.name) ?: return null
    return when (schemaEntry.target) {
        is TypeAccessibility.Accessible ->
            TypedAccessorSpec(schemaEntry.target, accessorName, schemaEntry.type)

        is TypeAccessibility.Inaccessible ->
            null
    }
}


private
fun documentInaccessibilityReasons(name: AccessorNameSpec, typeAccess: TypeAccessibility.Inaccessible): String =
    "`${name.kotlinIdentifier}` is not accessible in a type safe way because:\n${
        typeAccess.reasons.joinToString("\n") { reason ->
            "         * - ${reason.explanation}"
        }
    }"
