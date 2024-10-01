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

import org.gradle.internal.declarativedsl.analysis.tracingCodeResolver
import org.gradle.internal.declarativedsl.dom.TestApi
import org.gradle.internal.declarativedsl.dom.data.collectToMap
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.convertBlockToDocument
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil.parseAsTopLevelBlock
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals


class DomResolutionTest {

    @Test
    fun `resolves declarative document`() {
        val resolver = tracingCodeResolver()

        val topLevelBlock = parseAsTopLevelBlock(
            """
            addAndConfigure("test") {
                number = 123
            }
            justAdd("test2")
            complexValueOne = one(two("three"))
            complexValueOneFromUtils = utils.oneUtil()
            complexValueTwo = two("three")
            nested {
                number = 456
                add()
                enum = A
            }
            """.trimIndent()
        )

        resolver.resolve(schema, emptyList(), topLevelBlock)

        val document = convertBlockToDocument(topLevelBlock)
        val resolved = resolutionContainer(schema, resolver.trace, document)
        val resolutions = resolved.collectToMap(document).values
        assertEquals(
            """
            ContainerElementResolved -> element addAndConfigure(String): TopLevelElement
            LiteralValueResolved -> test
            PropertyAssignmentResolved -> TopLevelElement.number: Int
            LiteralValueResolved -> 123
            ContainerElementResolved -> element justAdd(String): TopLevelElement
            LiteralValueResolved -> test2
            PropertyAssignmentResolved -> TopLevelReceiver.complexValueOne: ComplexValueOne
            ValueFactoryResolved -> one(ComplexValueTwo): ComplexValueOne
            ValueFactoryResolved -> two(String): ComplexValueTwo
            LiteralValueResolved -> three
            PropertyAssignmentResolved -> TopLevelReceiver.complexValueOneFromUtils: ComplexValueOne
            ValueFactoryResolved -> oneUtil(): ComplexValueOne
            PropertyAssignmentResolved -> TopLevelReceiver.complexValueTwo: ComplexValueTwo
            ValueFactoryResolved -> two(String): ComplexValueTwo
            LiteralValueResolved -> three
            ConfiguringElementResolved -> configure NestedReceiver
            PropertyAssignmentResolved -> NestedReceiver.number: Int
            LiteralValueResolved -> 456
            ContainerElementResolved -> element add(): MyNestedElement
            PropertyAssignmentResolved -> NestedReceiver.enum: Enum
            NamedReferenceResolved -> A
            """.trimIndent(),
            resolutions.map { resolutionPrettyString(it) }.joinToString("\n")
        )
    }

    @Test
    fun `maps resolution errors to document errors`() {
        val resolver = tracingCodeResolver()

        val topLevelBlock = parseAsTopLevelBlock(
            """
            addAndConfigure("correct") { }
            addAndConfigure("lambda missing")
            addAndConfigure("incorrect signature", 1) {
                number = 123
                number = f(illegalPropertyUsage) // for now, it is reported as a single error; do we want it to be an assignment of an erroneous value?
            }
            unknown("test2")
            complexValueOne = "type mismatch"
            noSuchFunction(two("three"))
            nested {
                addAndConfigure("cross-scope") { }
                add("incorrect signature")
                enum = X
            }
            """.trimIndent()
        )

        resolver.resolve(schema, emptyList(), topLevelBlock)

        val document = convertBlockToDocument(topLevelBlock)
        val resolved = resolutionContainer(schema, resolver.trace, document, strictReceiverChecks = true)
        val resolutions = resolved.collectToMap(document).values
        assertEquals(
            """
            ContainerElementResolved -> element addAndConfigure(String): TopLevelElement
            LiteralValueResolved -> correct
            ElementNotResolved(UnresolvedSignature)
            LiteralValueResolved -> lambda missing
            ElementNotResolved(UnresolvedSignature)
            LiteralValueResolved -> incorrect signature
            LiteralValueResolved -> 1
            PropertyNotAssigned(UnresolvedBase)
            LiteralValueResolved -> 123
            ErrorResolution(IsError)
            ElementNotResolved(UnresolvedSignature)
            LiteralValueResolved -> test2
            PropertyNotAssigned(ValueTypeMismatch)
            LiteralValueResolved -> type mismatch
            ElementNotResolved(UnresolvedSignature)
            ValueFactoryResolved -> two(String): ComplexValueTwo
            LiteralValueResolved -> three
            ConfiguringElementResolved -> configure NestedReceiver
            ElementNotResolved(CrossScopeAccess)
            LiteralValueResolved -> cross-scope
            ElementNotResolved(UnresolvedSignature)
            LiteralValueResolved -> incorrect signature
            PropertyNotAssigned(UnresolvedValueUsed)
            NamedReferenceNotResolved(UnresolvedName)
            """.trimIndent(),
            resolutions.joinToString("\n") { resolutionPrettyString(it) }
        )
    }

    private
    val schema = schemaFromTypes(TestApi.TopLevelReceiver::class, TestApi::class.nestedClasses.toList())

}
