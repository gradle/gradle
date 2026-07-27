/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.gradle.internal.declarativedsl.schemaUtils.findTypeFor
import org.junit.Assert
import org.junit.Test

class TypeDiscoveryTest {
    @Test
    fun `basic type discovery discovers function parameter types`() {
        val schema = schemaFromTypes(TopLevel::class)
        Assert.assertTrue(schema.findTypeFor<ParameterType>() != null)
        Assert.assertTrue(schema.findTypeFor<OtherParameterType>() != null)
    }

    interface TopLevel {
        fun fn(p: ParameterType): ReturnType
    }

    interface ReturnType

    interface ParameterType {
        fun otherFn(p: OtherParameterType): String
    }

    interface OtherParameterType
}
