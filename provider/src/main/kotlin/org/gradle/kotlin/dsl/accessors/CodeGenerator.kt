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
fun ProjectSchema<String>.forEachAccessor(action: (String) -> Unit) {
    extensions.forEach { (name, type) ->
        extensionAccessorFor(name, type)?.let(action)
    }
    conventions.forEach { (name, type) ->
        if (name !in extensions) {
            conventionAccessorFor(name, type)?.let(action)
        }
    }
}


private
fun extensionAccessorFor(name: String, type: String): String? =
    if (isLegalExtensionName(name))
        """
            /**
             * Retrieves the [$name][$type] project extension.
             */
            val Project.`$name`: $type get() =
                extensions.getByName("$name") as $type

            /**
             * Configures the [$name][$type] project extension.
             */
            fun Project.`$name`(configure: $type.() -> Unit): Unit =
                extensions.configure("$name", configure)

        """.replaceIndent()
    else null


private
fun conventionAccessorFor(name: String, type: String): String? =
    if (isLegalExtensionName(name))
        """
            /**
             * Retrieves the [$name][$type] project convention.
             */
            val Project.`$name`: $type get() =
                convention.getPluginByName<$type>("$name")

            /**
             * Configures the [$name][$type] project convention.
             */
            fun Project.`$name`(configure: $type.() -> Unit): Unit =
                configure(`$name`)

        """.replaceIndent()
    else null


internal
fun isLegalExtensionName(name: String): Boolean =
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

