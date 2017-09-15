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

import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens


internal
fun ProjectSchema<TypeAccessibility>.forEachAccessor(action: (String) -> Unit) {
    extensions.map(::typedAccessorSpecFor).forEach { spec ->
        extensionAccessorFor(spec)?.let(action)
    }
    conventions.map(::typedAccessorSpecFor).forEach { spec ->
        if (spec.name.original !in extensions) {
            conventionAccessorFor(spec)?.let(action)
        }
    }
    configurations.map(::accessorNameSpecFor).forEach { spec ->
        configurationAccessorFor(spec)?.let(action)
    }
}

private
fun extensionAccessorFor(spec: TypedAccessorSpec): String? =
    codeForAccessor(spec.name) {
        when (spec.typeAccess) {
            is TypeAccessibility.Accessible -> accessibleExtensionAccessorFor(spec.name, spec.typeAccess.type)
            is TypeAccessibility.Inaccessible -> inaccessibleExtensionAccessorFor(spec.name, spec.typeAccess)
        }
    }


private
fun accessibleExtensionAccessorFor(name: AccessorNameSpec, type: String): String =
    """
        /**
         * Retrieves the [${name.original}][$type] project extension.
         */
        val Project.`${name.kotlinIdentifier}`: $type get() =
            extensions.getByName("${name.stringLiteral}") as $type

        /**
         * Configures the [${name.original}][$type] project extension.
         */
        fun Project.`${name.kotlinIdentifier}`(configure: $type.() -> Unit): Unit =
            extensions.configure("${name.stringLiteral}", configure)

    """


private
fun inaccessibleExtensionAccessorFor(name: AccessorNameSpec, typeAccess: TypeAccessibility.Inaccessible): String =
    """
        /**
         * Retrieves the [${name.original}][${typeAccess.type}] project extension.
         *
         * ${documentInaccessibilityReasons(name, typeAccess)}
         */
        val Project.`${name.kotlinIdentifier}`: Any get() =
            extensions.getByName("${name.stringLiteral}")

        /**
         * Configures the [${name.original}][${typeAccess.type}] project extension.
         *
         * ${documentInaccessibilityReasons(name, typeAccess)}
         */
        fun Project.`${name.kotlinIdentifier}`(configure: Any.() -> Unit): Unit =
            extensions.configure("${name.stringLiteral}", configure)

    """


private
fun conventionAccessorFor(spec: TypedAccessorSpec): String? =
    codeForAccessor(spec.name) {
        when (spec.typeAccess) {
            is TypeAccessibility.Accessible -> accessibleConventionAccessorFor(spec.name, spec.typeAccess.type)
            is TypeAccessibility.Inaccessible -> inaccessibleConventionAccessorFor(spec.name, spec.typeAccess)
        }
    }


private
fun accessibleConventionAccessorFor(name: AccessorNameSpec, type: String): String =
    """
        /**
         * Retrieves the [${name.original}][$type] project convention.
         */
        val Project.`${name.kotlinIdentifier}`: $type get() =
            convention.getPluginByName<$type>("${name.stringLiteral}")

        /**
         * Configures the [${name.original}][$type] project convention.
         */
        fun Project.`${name.kotlinIdentifier}`(configure: $type.() -> Unit): Unit =
            configure(`${name.stringLiteral}`)

    """


private
fun inaccessibleConventionAccessorFor(name: AccessorNameSpec, typeAccess: TypeAccessibility.Inaccessible): String =
    """
        /**
         * Retrieves the [${name.original}][${typeAccess.type}] project convention.
         *
         * ${documentInaccessibilityReasons(name, typeAccess)}
         */
        val Project.`${name.kotlinIdentifier}`: Any get() =
            convention.getPluginByName<Any>("${name.stringLiteral}")

        /**
         * Configures the [${name.original}][${typeAccess.type}] project convention.
         *
         * ${documentInaccessibilityReasons(name, typeAccess)}
         */
        fun Project.`${name.kotlinIdentifier}`(configure: Any.() -> Unit): Unit =
            configure(`${name.stringLiteral}`)

    """


private
fun configurationAccessorFor(name: AccessorNameSpec): String? =
    codeForAccessor(name) {
        """
            /**
             * The '${name.original}' configuration.
             */
            val ConfigurationContainer.`${name.kotlinIdentifier}`: Configuration
                get() = getByName("${name.stringLiteral}")

            /**
             * Adds a dependency to the '${name.original}' configuration.
             *
             * @param dependencyNotation notation for the dependency to be added.
             * @return The dependency.
             *
             * @see DependencyHandler.add
             */
            fun DependencyHandler.`${name.kotlinIdentifier}`(dependencyNotation: Any): Dependency =
                add("${name.stringLiteral}", dependencyNotation)

            /**
             * Adds a dependency to the '${name.original}' configuration.
             *
             * @param dependencyNotation notation for the dependency to be added.
             * @param dependencyConfiguration expression to use to configure the dependency.
             * @return The dependency.
             *
             * @see DependencyHandler.add
             */
            inline
            fun DependencyHandler.`${name.kotlinIdentifier}`(
                dependencyNotation: String,
                dependencyConfiguration: ExternalModuleDependency.() -> Unit): ExternalModuleDependency =
                add("${name.stringLiteral}", dependencyNotation, dependencyConfiguration)

            /**
             * Adds a dependency to the '${name.original}' configuration.
             *
             * @param group the group of the module to be added as a dependency.
             * @param name the name of the module to be added as a dependency.
             * @param version the optional version of the module to be added as a dependency.
             * @param configuration the optional configuration of the module to be added as a dependency.
             * @param classifier the optional classifier of the module artifact to be added as a dependency.
             * @param ext the optional extension of the module artifact to be added as a dependency.
             * @return The dependency.
             *
             * @see DependencyHandler.add
             */
            fun DependencyHandler.`${name.kotlinIdentifier}`(
                group: String,
                name: String,
                version: String? = null,
                configuration: String? = null,
                classifier: String? = null,
                ext: String? = null): ExternalModuleDependency =
                create(group, name, version, configuration, classifier, ext).apply { add("${name.stringLiteral}", this) }

            /**
             * Adds a dependency to the '${name.original}' configuration.
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
             * @see DependencyHandler.create
             * @see DependencyHandler.add
             */
            inline
            fun DependencyHandler.`${name.kotlinIdentifier}`(
                group: String,
                name: String,
                version: String? = null,
                configuration: String? = null,
                classifier: String? = null,
                ext: String? = null,
                dependencyConfiguration: ExternalModuleDependency.() -> Unit): ExternalModuleDependency =
                add("${name.stringLiteral}", create(group, name, version, configuration, classifier, ext), dependencyConfiguration)

            /**
             * Adds a dependency to the '${name.original}' configuration.
             *
             * @param dependency dependency to be added.
             * @param dependencyConfiguration expression to use to configure the dependency.
             * @return The dependency.
             *
             * @see DependencyHandler.add
             */
            inline
            fun <T : ModuleDependency> DependencyHandler.`${name.kotlinIdentifier}`(dependency: T, dependencyConfiguration: T.() -> Unit): T =
                add("${name.stringLiteral}", dependency, dependencyConfiguration)

        """
    }


private
data class AccessorNameSpec(val original: String,
                            val kotlinIdentifier: String = original,
                            val stringLiteral: String = stringLiteralFor(original))

private
data class TypedAccessorSpec(val name: AccessorNameSpec, val typeAccess: TypeAccessibility)


private
fun stringLiteralFor(original: String) =
    escapeStringTemplateDollarSign(original)


private
fun escapeStringTemplateDollarSign(string: String) =
    string.replace("${'$'}", "${'$'}{'${'$'}'}")


private
fun accessorNameSpecFor(originalName: String) =
    AccessorNameSpec(originalName)


private
fun typedAccessorSpecFor(nameAndType: Map.Entry<String, TypeAccessibility>) =
    TypedAccessorSpec(accessorNameSpecFor(nameAndType.key), nameAndType.value)


private
fun documentInaccessibilityReasons(name: AccessorNameSpec, typeAccess: TypeAccessibility.Inaccessible): String =
    "`${name.kotlinIdentifier}` is not accessible in a type safe way because:\n${typeAccess.reasons.joinToString("\n") { reason ->
        "         * - ${reason.explanation}"
    }}"


private inline
fun codeForAccessor(name: AccessorNameSpec, code: () -> String): String? =
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

