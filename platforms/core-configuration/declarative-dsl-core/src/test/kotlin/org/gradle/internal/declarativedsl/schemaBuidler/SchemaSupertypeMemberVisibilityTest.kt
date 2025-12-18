/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition
import org.gradle.declarative.dsl.model.annotations.VisibleInDefinition
import org.gradle.internal.declarativedsl.schemaBuilder.DeclarativeDslSchemaBuildingException
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.gradle.internal.declarativedsl.schemaUtils.typeFor
import org.junit.Assert
import org.junit.Test

class SchemaSupertypeMemberVisibilityTest {
    @Test
    fun `hidden supertypes do not contribute members`() {
        val schema = schemaFromTypes(Sub::class, listOf(Sub::class, HiddenSup::class))

        Assert.assertNotNull(schema.typeFor<Sub>().memberFunctions.find { it.simpleName == Sub::sub.name })
        Assert.assertTrue(schema.typeFor<Sub>().memberFunctions.none { it.simpleName == HiddenSup::hiddenSup.name })
        Assert.assertTrue(schema.typeFor<Sub>().memberFunctions.none { it.simpleName == SupSup::supSup.name })
        Assert.assertTrue(schema.typeFor<Sub>().memberFunctions.none { it.simpleName == HiddenSupSup::hiddenSupSup.name })
    }

    @Test
    fun `a supertype is visible as long as reachable via non-hidden supertypes`() {
        val schema = schemaFromTypes(OtherSub::class, listOf(OtherSub::class, HiddenSup::class))

        Assert.assertTrue(schema.typeFor<OtherSub>().memberFunctions.none { it.simpleName == HiddenSup::hiddenSup.name })
        Assert.assertTrue(schema.typeFor<OtherSub>().memberFunctions.any { it.simpleName == SupSup::supSup.name })
    }

    @Test
    fun `a supertype is visible if annotated, even if only reachable via hidden supertypes`() {
        val schema = schemaFromTypes(SubWithVisibleOtherSup::class, listOf(SubWithVisibleOtherSup::class))

        Assert.assertTrue(schema.typeFor<SubWithVisibleOtherSup>().memberFunctions.none { it.simpleName == HiddenSupWithVisibleOtherSup::hiddenSupWithVisibleOtherSup.name })
        Assert.assertTrue(schema.typeFor<SubWithVisibleOtherSup>().memberFunctions.any { it.simpleName == VisibleOtherSup::visibleOtherSup.name })
    }

    @Test
    fun `annotating a type as both visible and hidden leads to an error`() {
        val error = Assert.assertThrows(IllegalStateException::class.java) { schemaFromTypes(VisibleHidden::class, listOf(VisibleHidden::class)) }

        Assert.assertTrue(
            error.message!!.contains(VisibleInDefinition::class.simpleName!!)
                && error.message!!.contains(HiddenInDefinition::class.simpleName!!)
                && error.message!!.contains(VisibleHidden::class.qualifiedName!!)
        )
    }

    @Test
    fun `annotating a member as both visible and hidden leads to an error`() {
        val error = Assert.assertThrows(DeclarativeDslSchemaBuildingException::class.java) { schemaFromTypes(VisibleHiddenMember::class, listOf(VisibleHiddenMember::class)) }

        Assert.assertTrue(
            error.message!!.contains(VisibleInDefinition::class.simpleName!!)
                && error.message!!.contains(HiddenInDefinition::class.simpleName!!)
                && error.message!!.contains(VisibleHiddenMember::class.qualifiedName!!)
        )
    }

    @Test
    fun `a member from a hidden supertype can be exposed with @VisibleInDefinition`() {
        val schema = schemaFromTypes(Sub::class, listOf(Sub::class, HiddenSup::class))

        Assert.assertNotNull(schema.typeFor<Sub>().memberFunctions.find { it.simpleName == HiddenSup::visibleInHiddenSup.name })
    }

    @Test
    fun `a hidden member from a visible type is not included in the schema`() {
        val schema = schemaFromTypes(OtherSub::class, listOf(OtherSub::class))

        Assert.assertNotNull(schema.typeFor<OtherSub>().memberFunctions.find { it.simpleName == OtherSub::otherSub.name })
        Assert.assertNull(schema.typeFor<OtherSub>().memberFunctions.find { it.simpleName == OtherSub::hiddenInOtherSub.name })
    }
}


@HiddenInDefinition
interface HiddenSupSup {
    fun hiddenSupSup(): String = "".reversed()
}

interface SupSup {
    fun supSup(): String = "".reversed()
}

@HiddenInDefinition
open class HiddenSup {
    fun hiddenSup(): String = "".reversed()

    @VisibleInDefinition
    fun visibleInHiddenSup(): String = "".reversed()
}

class Sub : HiddenSup() {
    fun sub(): String = "".reversed()
}

class OtherSub : HiddenSup(), SupSup {
    fun otherSub(): String = "".reversed()

    @HiddenInDefinition
    fun hiddenInOtherSub(): String = "".reversed()
}

@VisibleInDefinition
interface VisibleOtherSup {
    fun visibleOtherSup(): String = "".reversed()
}

@HiddenInDefinition
interface HiddenSupWithVisibleOtherSup : VisibleOtherSup {
    fun hiddenSupWithVisibleOtherSup(): String = "".reversed()
}

class SubWithVisibleOtherSup : HiddenSupWithVisibleOtherSup

@HiddenInDefinition
@VisibleInDefinition
class VisibleHidden

class VisibleHiddenMember {
    @HiddenInDefinition
    @VisibleInDefinition
    fun visibleHiddenMember(): String = "".reversed()
}
