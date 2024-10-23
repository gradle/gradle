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

package org.gradle.internal.declarativedsl.schemaBuidler

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.ParameterSemantics
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import org.gradle.internal.declarativedsl.assertIs
import org.gradle.internal.declarativedsl.schemaUtils.singleFunctionNamed
import org.gradle.internal.declarativedsl.schemaUtils.typeFor


class FunctionExtractorTest {
    @Test
    fun `adding function may have a configuring lambda if it returns the added value`() {
        val schema = schemaFromTypes(ReceiverOne::class, listOf(ReceiverOne::class))
        val dataClass = schema.dataClassTypesByFqName.values.single() as DataClass
        val function = dataClass.memberFunctions.single()
        assertIs<FunctionSemantics.AddAndConfigure>(function.semantics)
    }

    @Test
    fun `adding function may not have a configuring lambda if it returns Unit`() {
        val exception = assertThrows<IllegalStateException> {
            schemaFromTypes(ReceiverTwo::class, listOf(ReceiverTwo::class))
        }
        assertTrue { exception.message!!.contains("@Adding") }
    }

    @Test
    fun `adding function with no lambda is accepted if it returns Unit`() {
        val schema = schemaFromTypes(ReceiverThree::class, listOf(ReceiverThree::class))
        val dataClass = schema.dataClassTypesByFqName.values.single() as DataClass
        val function = dataClass.memberFunctions.single()
        assertIs<FunctionSemantics.AddAndConfigure>(function.semantics)
    }

    @Test
    fun `configuring functions may accept parameters recognized as identity keys`() {
        with(schemaFromTypes(ReceiverFour::class, listOf(ReceiverFour::class))) {
            assertIs<ParameterSemantics.IdentityKey>(
                typeFor<ReceiverFour>().singleFunctionNamed("configuring").function.parameters.single().semantics
            )
        }
    }

    abstract class ReceiverOne {
        @Adding
        abstract fun adding(receiver: ReceiverOne.() -> Unit): ReceiverOne
    }

    abstract class ReceiverTwo {
        @Adding
        abstract fun adding(receiver: ReceiverTwo.() -> Unit)
    }

    abstract class ReceiverThree {
        @Adding
        abstract fun adding(three: Int)
    }

    abstract class ReceiverFour {
        @Configuring
        abstract fun configuring(item: Int, configure: ReceiverFour.() -> Unit)
    }
}
