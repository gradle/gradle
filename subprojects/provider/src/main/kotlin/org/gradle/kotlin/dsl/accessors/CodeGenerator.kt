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

import org.gradle.api.internal.HasConvention
import org.gradle.api.plugins.ExtensionAware

import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens


internal
fun ProjectSchema<TypeAccessibility>.forEachAccessor(action: (String) -> Unit) {
    (extensionAccessors() + configurationAccessors()).forEach(action)
}


internal
fun ProjectSchema<TypeAccessibility>.extensionAccessors(): Sequence<String> = sequence {

    val seen = SeenAccessorSpecs()

    extensions.mapNotNull(::typedAccessorSpec).forEach { spec ->
        extensionAccessorFor(spec)?.let { extensionAccessor ->
            yield(extensionAccessor)
            seen.add(spec)
        }
    }

    conventions.mapNotNull(::typedAccessorSpec).filterNot(seen::hasConflict).forEach { spec ->
        conventionAccessorFor(spec)?.let {
            yield(it)
        }
    }
}


internal
fun <T> ProjectSchema<T>.configurationAccessors(): Sequence<String> = sequence {
    configurations.map(::accessorNameSpec).forEach { spec ->
        configurationAccessorFor(spec)?.let {
            yield(it)
        }
    }
}


private
data class SeenAccessorSpecs(
    private val targetTypesByName: HashMap<AccessorNameSpec, HashSet<TypeAccessibility.Accessible>> = hashMapOf()
) {

    fun add(accessorSpec: TypedAccessorSpec) =
        setFor(accessorSpec.name).add(accessorSpec.targetTypeAccess)

    fun hasConflict(accessorSpec: TypedAccessorSpec) =
        targetTypesByName[accessorSpec.name]?.let { targetTypes ->
            accessorSpec.targetTypeAccess in targetTypes
        } ?: false

    private
    fun setFor(accessorNameSpec: AccessorNameSpec): HashSet<TypeAccessibility.Accessible> =
        targetTypesByName.computeIfAbsent(accessorNameSpec) { hashSetOf() }
}


private
fun extensionAccessorFor(spec: TypedAccessorSpec): String? = spec.run {
    codeForAccessor(name) {
        when (typeAccess) {
            is TypeAccessibility.Accessible -> accessibleExtensionAccessorFor(targetTypeAccess.type, name, typeAccess.type)
            is TypeAccessibility.Inaccessible -> inaccessibleExtensionAccessorFor(targetTypeAccess.type, name, typeAccess)
        }
    }
}


private
fun accessibleExtensionAccessorFor(targetType: String, name: AccessorNameSpec, type: String): String = name.run {
    """
        /**
         * Retrieves the [$original][$type] extension.
         */
        val $targetType.`$kotlinIdentifier`: $type get() =
            $thisExtensions.getByName("$stringLiteral") as $type

        /**
         * Configures the [$original][$type] extension.
         */
        fun $targetType.`$kotlinIdentifier`(configure: $type.() -> Unit): Unit =
            $thisExtensions.configure("$stringLiteral", configure)

    """
}


private
fun inaccessibleExtensionAccessorFor(targetType: String, name: AccessorNameSpec, typeAccess: TypeAccessibility.Inaccessible): String = name.run {
    """
        /**
         * Retrieves the [$original][${typeAccess.type}] extension.
         *
         * ${documentInaccessibilityReasons(name, typeAccess)}
         */
        val $targetType.`$kotlinIdentifier`: Any get() =
            $thisExtensions.getByName("$stringLiteral")

        /**
         * Configures the [$original][${typeAccess.type}] extension.
         *
         * ${documentInaccessibilityReasons(name, typeAccess)}
         */
        fun $targetType.`$kotlinIdentifier`(configure: Any.() -> Unit): Unit =
            $thisExtensions.configure("$stringLiteral", configure)

    """
}


private
fun conventionAccessorFor(spec: TypedAccessorSpec): String? = spec.run {
    codeForAccessor(name) {
        when (typeAccess) {
            is TypeAccessibility.Accessible -> accessibleConventionAccessorFor(targetTypeAccess.type, name, typeAccess.type)
            is TypeAccessibility.Inaccessible -> inaccessibleConventionAccessorFor(targetTypeAccess.type, name, typeAccess)
        }
    }
}


private
fun accessibleConventionAccessorFor(targetType: String, name: AccessorNameSpec, type: String): String = name.run {
    """
        /**
         * Retrieves the [$original][$type] convention.
         */
        val $targetType.`$kotlinIdentifier`: $type get() =
            $thisConvention.getPluginByName<$type>("$stringLiteral")

        /**
         * Configures the [$original][$type] convention.
         */
        fun $targetType.`$kotlinIdentifier`(configure: $type.() -> Unit): Unit =
            configure(`$stringLiteral`)

    """
}


private
fun inaccessibleConventionAccessorFor(targetType: String, name: AccessorNameSpec, typeAccess: TypeAccessibility.Inaccessible): String = name.run {
    """
        /**
         * Retrieves the [$original][${typeAccess.type}] convention.
         *
         * ${documentInaccessibilityReasons(name, typeAccess)}
         */
        val $targetType.`$kotlinIdentifier`: Any get() =
            $thisConvention.getPluginByName<Any>("$stringLiteral")

        /**
         * Configures the [$original][${typeAccess.type}] convention.
         *
         * ${documentInaccessibilityReasons(name, typeAccess)}
         */
        fun $targetType.`$kotlinIdentifier`(configure: Any.() -> Unit): Unit =
            configure(`$stringLiteral`)

    """
}


private
fun configurationAccessorFor(name: AccessorNameSpec): String? = name.run {
    codeForAccessor(name) {
        """
            /**
             * The '$original' configuration.
             */
            val ConfigurationContainer.`$kotlinIdentifier`: Configuration
                get() = getByName("$stringLiteral")

            /**
             * Adds a dependency to the '$original' configuration.
             *
             * @param dependencyNotation notation for the dependency to be added.
             * @return The dependency.
             *
             * @see [DependencyHandler.add]
             */
            fun DependencyHandler.`$kotlinIdentifier`(dependencyNotation: Any): Dependency? =
                add("$stringLiteral", dependencyNotation)

            /**
             * Adds a dependency to the '$original' configuration.
             *
             * @param dependencyNotation notation for the dependency to be added.
             * @param dependencyConfiguration expression to use to configure the dependency.
             * @return The dependency.
             *
             * @see [DependencyHandler.add]
             */
            inline fun DependencyHandler.`$kotlinIdentifier`(
                dependencyNotation: String,
                dependencyConfiguration: ExternalModuleDependency.() -> Unit
            ): ExternalModuleDependency = add("$stringLiteral", dependencyNotation, dependencyConfiguration)

            /**
             * Adds a dependency to the '$original' configuration.
             *
             * @param group the group of the module to be added as a dependency.
             * @param name the name of the module to be added as a dependency.
             * @param version the optional version of the module to be added as a dependency.
             * @param configuration the optional configuration of the module to be added as a dependency.
             * @param classifier the optional classifier of the module artifact to be added as a dependency.
             * @param ext the optional extension of the module artifact to be added as a dependency.
             * @return The dependency.
             *
             * @see [DependencyHandler.add]
             */
            fun DependencyHandler.`$kotlinIdentifier`(
                group: String,
                name: String,
                version: String? = null,
                configuration: String? = null,
                classifier: String? = null,
                ext: String? = null
            ): ExternalModuleDependency = create(group, name, version, configuration, classifier, ext).also {
                add("$stringLiteral", it)
            }

            /**
             * Adds a dependency to the '$original' configuration.
             *
             * @param group the group of the module to be added as a dependency.
             * @param name the name of the module to be added as a dependency.
             * @param version the optional version of the module to be added as a dependency.
             * @param configuration the optional configuration of the module to be added as a dependency.
             * @param classifier the optional classifier of the module artifact to be added as a dependency.
             * @param ext the optional extension of the module artifact to be added as a dependency.
             * @param dependencyConfiguration expression to use to configure the dependency.
             * @return The dependency.
             *
             * @see [DependencyHandler.create]
             * @see [DependencyHandler.add]
             */
            inline fun DependencyHandler.`$kotlinIdentifier`(
                group: String,
                name: String,
                version: String? = null,
                configuration: String? = null,
                classifier: String? = null,
                ext: String? = null,
                dependencyConfiguration: ExternalModuleDependency.() -> Unit
            ): ExternalModuleDependency = create(group, name, version, configuration, classifier, ext).also {
                add("$stringLiteral", it, dependencyConfiguration)
            }

            /**
             * Adds a dependency to the '$original' configuration.
             *
             * @param dependency dependency to be added.
             * @param dependencyConfiguration expression to use to configure the dependency.
             * @return The dependency.
             *
             * @see [DependencyHandler.add]
             */
            inline fun <T : ModuleDependency> DependencyHandler.`$kotlinIdentifier`(
                dependency: T,
                dependencyConfiguration: T.() -> Unit
            ): T = add("$stringLiteral", dependency, dependencyConfiguration)

            /**
             * Adds a dependency constraint to the '$original' configuration.
             *
             * @param constraintNotation the dependency constraint notation
             *
             * @return the added dependency constraint
             *
             * @see [DependencyConstraintHandler.add]
             */
            @Incubating
            fun DependencyConstraintHandler.`$kotlinIdentifier`(constraintNotation: Any): DependencyConstraint? =
                add("$stringLiteral", constraintNotation)

            /**
             * Adds a dependency constraint to the '$original' configuration.
             *
             * @param constraintNotation the dependency constraint notation
             * @param block the block to use to configure the dependency constraint
             *
             * @return the added dependency constraint
             *
             * @see [DependencyConstraintHandler.add]
             */
            @Incubating
            fun DependencyConstraintHandler.`$kotlinIdentifier`(constraintNotation: Any, block: DependencyConstraint.() -> Unit): DependencyConstraint? =
                add("$stringLiteral", constraintNotation, block)

        """
    }
}


private
val thisExtensions =
    "(this as ${ExtensionAware::class.java.name}).extensions"


private
val thisConvention =
    "((this as? Project)?.convention ?: (this as ${HasConvention::class.java.name}).convention)"


internal
data class AccessorNameSpec(val original: String) {

    val kotlinIdentifier
        get() = original

    val stringLiteral by lazy { stringLiteralFor(original) }
}


private
data class TypedAccessorSpec(
    val targetTypeAccess: TypeAccessibility.Accessible,
    val name: AccessorNameSpec,
    val typeAccess: TypeAccessibility
)


private
fun stringLiteralFor(original: String) =
    escapeStringTemplateDollarSign(original)


private
fun escapeStringTemplateDollarSign(string: String) =
    string.replace("${'$'}", "${'$'}{'${'$'}'}")


private
fun accessorNameSpec(originalName: String) =
    AccessorNameSpec(originalName)


private
fun typedAccessorSpec(schemaEntry: ProjectSchemaEntry<TypeAccessibility>) =
    schemaEntry.target.run {
        when (this) {
            is TypeAccessibility.Accessible ->
                TypedAccessorSpec(this, accessorNameSpec(schemaEntry.name), schemaEntry.type)
            is TypeAccessibility.Inaccessible ->
                null
        }
    }


private
fun documentInaccessibilityReasons(name: AccessorNameSpec, typeAccess: TypeAccessibility.Inaccessible): String =
    "`${name.kotlinIdentifier}` is not accessible in a type safe way because:\n${typeAccess.reasons.joinToString("\n") { reason ->
        "         * - ${reason.explanation}"
    }}"


private
inline fun codeForAccessor(name: AccessorNameSpec, code: () -> String): String? =
    if (isLegalAccessorName(name.kotlinIdentifier)) code().replaceIndent()
    else null


internal
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
