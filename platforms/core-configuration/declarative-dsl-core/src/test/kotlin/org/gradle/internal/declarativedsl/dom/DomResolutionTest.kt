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

package org.gradle.internal.declarativedsl.dom

import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.internal.declarativedsl.analysis.tracingCodeResolver
import org.gradle.internal.declarativedsl.dom.data.collectToMap
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.convertBlockToDocument
import org.gradle.internal.declarativedsl.dom.resolution.resolutionContainer
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil.parseAsTopLevelBlock
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.Test


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
            """.trimIndent()
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
            }
            """.trimIndent()
        )

        resolver.resolve(schema, emptyList(), topLevelBlock)

        val document = convertBlockToDocument(topLevelBlock)
        val resolved = resolutionContainer(schema, resolver.trace, document, strictReceiverChecks = true)
        val resolutions = resolved.collectToMap(document).values
        assertEquals(
            resolutions.map { resolutionPrettyString(it) }.joinToString("\n"),
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
            """.trimIndent()
        )
    }

    private
    val schema = schemaFromTypes(TestApi.TopLevelReceiver::class, TestApi::class.nestedClasses.toList())

    private
    fun resolutionPrettyString(resolution: DocumentResolution): String =
        resolution::class.simpleName + when (resolution) {
            is DocumentResolution.ElementResolution.SuccessfulElementResolution.ContainerElementResolved ->
                " -> element ${functionSignatureString(resolution.elementFactoryFunction)}"

            is DocumentResolution.ElementResolution.SuccessfulElementResolution.ConfiguringElementResolved ->
                " -> configure ${resolution.elementType}"

            is DocumentResolution.PropertyResolution.PropertyAssignmentResolved ->
                " -> ${resolution.receiverType}.${resolution.property.name}: ${typeString(resolution.property.valueType)}"

            is DocumentResolution.ValueNodeResolution.ValueFactoryResolution.ValueFactoryResolved ->
                " -> ${functionSignatureString(resolution.function)}"

            is DocumentResolution.ValueNodeResolution.LiteralValueResolved -> " -> ${resolution.value}"
            is DocumentResolution.UnsuccessfulResolution -> "(${resolution.reasons.joinToString()})"
        }

    private
    fun typeString(typeRef: DataTypeRef) = when (typeRef) {
        is DataTypeRef.Type -> typeRef.dataType.toString()
        is DataTypeRef.Name -> typeRef.fqName.simpleName
    }

    private
    fun functionSignatureString(function: SchemaFunction) =
        "${function.simpleName}(${function.parameters.joinToString { typeString(it.type) }}): ${typeString(function.returnValueType)}"
}
