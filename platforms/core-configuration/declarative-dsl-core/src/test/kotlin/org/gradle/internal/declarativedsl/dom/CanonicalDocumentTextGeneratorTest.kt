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

import org.gradle.internal.declarativedsl.dom.fromLanguageTree.convertBlockToDocument
import org.gradle.internal.declarativedsl.dom.mutation.common.NewDocumentNodes
import org.gradle.internal.declarativedsl.dom.mutation.common.NodeRepresentationFlagsContainer
import org.gradle.internal.declarativedsl.dom.mutation.elementNamed
import org.gradle.internal.declarativedsl.dom.writing.CanonicalCodeGenerator
import org.gradle.internal.declarativedsl.dom.writing.CanonicalDocumentTextGenerator
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil.parseAsTopLevelBlock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.Test


class CanonicalDocumentTextGeneratorTest {
    private
    val canonicalCode = """
        myFun {
            a = 1
            b = f("x", z.f("y"))
            c = true
            nested {
                x = "y"
            }
            factory(1)
            otherFactory()
            otherFactoryWithArgs(1, 2)
        }

        myOtherFun {
            x = "y"
        }

        x = 1
        y = 2

        block {
            test()
        }
    """.trimIndent()

    @Test
    fun `canonical writer produces the canonical code from a dom`() {
        val tree = parseAsTopLevelBlock(canonicalCode)
        val dom = convertBlockToDocument(tree)
        assertEquals(canonicalCode, CanonicalDocumentTextGenerator().generateText(dom))
    }

    @Test
    fun `canonical writer can insert an empty element with forced empty block`() {
        val tree = parseAsTopLevelBlock(canonicalCode)
        val dom = convertBlockToDocument(tree)

        val forceEmptyBlock = NodeRepresentationFlagsContainer(
            setOf(
                dom.elementNamed("myFun").elementNamed("factory"),
                dom.elementNamed("block").elementNamed("test")
            )
        )

        assertEquals(
            canonicalCode
                .replace("factory(1)", "factory(1) { }") // with arguments
                .replace("test()", "test { }"), // with no arguments
            CanonicalCodeGenerator().generateCode(
                NewDocumentNodes(dom.content, forceEmptyBlock), "    "::repeat, true
            )
        )
    }
}
