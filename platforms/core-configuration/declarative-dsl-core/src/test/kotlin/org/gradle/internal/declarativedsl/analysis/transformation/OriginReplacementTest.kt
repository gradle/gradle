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

package org.gradle.internal.declarativedsl.analysis.transformation

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.analysis.getDataType
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import kotlin.test.Test
import kotlin.test.assertEquals


object OriginReplacementTest {
    @Test
    fun `replaces configured object access and function call receiver`() {
        with(resolution("configuring { property = value() }")) {
            val assignment = assignments.single()
            val lhs = replaceInnerReceiverWithTopLevel(assignment.lhs.receiverObject)
            val rhs = replaceInnerReceiverWithTopLevel(assignment.rhs)

            assertEquals(topLevelReceiver, (lhs as ObjectOrigin.ImplicitThisReceiver).resolvedTo)
            assertEquals(topLevelReceiver, ((rhs as ObjectOrigin.NewObjectFromMemberFunction).receiver as ObjectOrigin.ImplicitThisReceiver).resolvedTo)
        }
    }

    @Test
    fun `replaces added object receiver`() {
        with(resolution("adding { addingValue(value()) }")) {
            val result = replaceInnerReceiverWithTopLevel(additions[1].dataObject)
            assertEquals(topLevelReceiver, ((result as ObjectOrigin.NewObjectFromMemberFunction).receiver as ObjectOrigin.ImplicitThisReceiver).resolvedTo)
        }
    }

    @Test
    fun `replaces receivers in function call arguments`() {
        with(resolution("configuring { property = value(value()) }")) {
            val result = replaceInnerReceiverWithTopLevel(assignments.single().rhs)
            val argResult = (result as ObjectOrigin.NewObjectFromMemberFunction).parameterBindings.bindingMap.values.single()
            assertEquals(topLevelReceiver, ((argResult as ObjectOrigin.NewObjectFromMemberFunction).receiver as ObjectOrigin.ImplicitThisReceiver).resolvedTo)
        }
    }

    @Test
    fun `replaces receiver in property access`() {
        with(resolution("configuring { addingValue(utils.value()) }")) {
            val result = replaceInnerReceiverWithTopLevel(additions.single().dataObject)
            val singleArg = (result as ObjectOrigin.NewObjectFromMemberFunction).parameterBindings.bindingMap.values.single()
            val propertyReference = (singleArg as ObjectOrigin.NewObjectFromMemberFunction).receiver as ObjectOrigin.PropertyReference
            assertEquals(topLevelReceiver, (propertyReference.receiver as ObjectOrigin.ImplicitThisReceiver).resolvedTo)
        }
    }

    //region features outside DOM

    @Test
    fun `replaces receiver in val references`() {
        with(resolution("configuring { val x = value(); property = x }")) {
            val result = replaceInnerReceiverWithTopLevel(assignments.single().rhs)
            val valAssignedFunction = ((result as ObjectOrigin.FromLocalValue).assigned) as ObjectOrigin.NewObjectFromMemberFunction
            val functionReceiver = valAssignedFunction.receiver as ObjectOrigin.ImplicitThisReceiver
            assertEquals(topLevelReceiver, functionReceiver.resolvedTo)
        }
    }

    @Test
    fun `replaces receiver in chained calls`() {
        with(resolution("configuring { addingValue(value().anotherValue()) }")) {
            val result = replaceInnerReceiverWithTopLevel(additions.single().dataObject)
            val singleArg = (result as ObjectOrigin.NewObjectFromMemberFunction).parameterBindings.bindingMap.values.single()
            val propertyReference = (singleArg as ObjectOrigin.NewObjectFromMemberFunction).receiver as ObjectOrigin.NewObjectFromMemberFunction
            assertEquals(topLevelReceiver, (propertyReference.receiver as ObjectOrigin.ImplicitThisReceiver).resolvedTo)
        }
    }

    //endregion

    private
    fun resolution(code: String) = schema.resolve(code).also { assertEquals(emptyList(), it.errors) }

    private
    fun ResolutionResult.replaceInnerReceiverWithTopLevel(origin: ObjectOrigin) =
        OriginReplacement.replaceReceivers(
            origin,
            { (SchemaTypeRefContext(schema).getDataType(it) as? DataClass)?.name?.simpleName == "InnerReceiver" },
            topLevelReceiver
        )

    private
    val schema = schemaFromTypes(TopLevelReceiver::class, this::class.nestedClasses)

    interface TopLevelReceiver {
        @Configuring
        fun configuring(fn: InnerReceiver.() -> Unit)

        @Adding
        fun adding(fn: InnerReceiver.() -> Unit): InnerReceiver
    }

    interface InnerReceiver {
        @Restricted
        fun value(): Value

        @Restricted
        fun value(arg: Value): Value

        @Adding
        fun addingValue(value: Value): Value

        @get:Restricted
        val utils: Utils

        @get:Restricted
        var property: Value
    }

    interface Utils {
        @Restricted
        fun value(): Value
    }

    interface Value {
        @Restricted
        fun anotherValue(): Value
    }
}
