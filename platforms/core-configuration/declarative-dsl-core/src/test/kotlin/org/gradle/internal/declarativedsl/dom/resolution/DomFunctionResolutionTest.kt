/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.declarativedsl.dom.resolution

import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.internal.declarativedsl.analysis.tracingCodeResolver
import org.gradle.internal.declarativedsl.dom.data.collectToMap
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.convertBlockToDocument
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil.parseAsTopLevelBlock
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class DomFunctionResolutionTest {
    @Test
    fun `using an opaque value as identity key is reported in the DOM resolution results`() {
        val resolver = tracingCodeResolver()

        val topLevelBlock = parseAsTopLevelBlock(
            """
            itemNamed("ok") {
                x = 1
            }
            itemNamed(stringFactory()) {
                x = 2
            }
            """.trimIndent()
        )

        resolver.resolve(schema, emptyList(), topLevelBlock)

        val document = convertBlockToDocument(topLevelBlock)
        val resolved = resolutionContainer(schema, resolver.trace, document)
        val resolutions = resolved.collectToMap(document).values
        assertEquals(
            resolutions.map { resolutionPrettyString(it) }.joinToString("\n"),
            """
            ConfiguringElementResolved -> configure Item
            LiteralValueResolved -> ok
            PropertyAssignmentResolved -> Item.x: Int
            LiteralValueResolved -> 1
            ElementNotResolved(OpaqueValueInIdentityKey)
            ValueFactoryResolved -> stringFactory(): String
            PropertyNotAssigned(UnresolvedBase)
            LiteralValueResolved -> 2
            """.trimIndent()
        )
    }

    private
    val schema = schemaFromTypes(TopLevel::class, this::class.nestedClasses.toList())

    interface TopLevel {
        @Suppress("unused")
        @Configuring
        fun itemNamed(name: String, configure: Item.() -> Unit)

        @Suppress("unused")
        @Restricted
        fun stringFactory(): String
    }

    interface Item {
        @Suppress("unused")
        @get:Restricted
        var x: Int
    }
}
