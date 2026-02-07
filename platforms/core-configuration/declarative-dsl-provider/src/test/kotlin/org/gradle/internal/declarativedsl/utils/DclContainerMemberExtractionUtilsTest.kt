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

package org.gradle.internal.declarativedsl.utils

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.declarative.dsl.model.annotations.ElementFactoryName
import org.gradle.internal.declarativedsl.ndoc.DclContainerMemberExtractionUtils
import org.gradle.internal.declarativedsl.ndoc.DclContainerMemberExtractionUtils.elementTypeFromNdocContainerType
import org.gradle.internal.declarativedsl.schemaBuilder.DefaultSchemaBuildingHost
import org.gradle.internal.declarativedsl.schemaBuilder.LossySchemaBuildingOperation
import org.gradle.internal.declarativedsl.schemaBuilder.asSupported
import org.gradle.internal.declarativedsl.schemaBuilder.orError
import org.gradle.internal.declarativedsl.schemaBuilder.toKType
import org.junit.Assert
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@OptIn(LossySchemaBuildingOperation::class)
class DclContainerMemberExtractionUtilsTest {

    private val host = DefaultSchemaBuildingHost(Unit::class)

    private fun elementFactoryFunctionNameFromElementType(type: KType): String =
        DclContainerMemberExtractionUtils.elementFactoryFunctionNameFromElementType(type.asSupported(host).orError())

    private fun elementTypeFromNdocContainerType(type: KType): KType? =
        elementTypeFromNdocContainerType(host, type.asSupported(host).orError())?.toKType()

    @Test
    fun `the default element factory name is the decapitalized type name`() {
        Assert.assertEquals("any", elementFactoryFunctionNameFromElementType(typeOf<Any>()))
    }

    @Test
    fun `can use an annotation to provide a custom element factory name`() {
        @ElementFactoryName("custom")
        class Annotated

        Assert.assertEquals("custom", elementFactoryFunctionNameFromElementType(typeOf<Annotated>()))
    }

    @Test
    fun `throws error if element factory name annotation is missing a value`() {
        @ElementFactoryName
        class Annotated

        val exception = assertThrows<IllegalStateException> {
            elementFactoryFunctionNameFromElementType(typeOf<Annotated>())
        }
        Assert.assertTrue(ElementFactoryName::class.simpleName!! in exception.message!!)
    }

    @Test
    fun `no element type is extracted from unrelated parameterized types`() {
        Assert.assertNull(elementTypeFromNdocContainerType(typeOf<Unrelated<String>>()))
    }

    @Test
    fun `element type is extracted from an exact NDOC instantiation`() {
        Assert.assertEquals(typeOf<String>(), elementTypeFromNdocContainerType(typeOf<NamedDomainObjectContainer<String>>()))
    }

    @Test
    fun `parameterized types get properly extracted as element types`() {
        Assert.assertEquals(typeOf<List<String>>(), elementTypeFromNdocContainerType(typeOf<NamedDomainObjectContainer<List<String>>>()))
    }

    @Test
    fun `supertype arguments get properly discovered in types with multiple type arguments`() {
        Assert.assertEquals(typeOf<String>(), elementTypeFromNdocContainerType(typeOf<MultiArgSubtype<Int, String>>()))
    }

    @Test
    fun `element type is extracted from an NDOC subtype with concrete type`() {
        Assert.assertEquals(typeOf<String>(), elementTypeFromNdocContainerType(typeOf<NdocStringSubtype>()))
    }

    @Test
    fun `element type is extracted from parameterized NDOC subtype instantiation`() {
        Assert.assertEquals(typeOf<String>(), elementTypeFromNdocContainerType(typeOf<Subtype1<String>>()))
    }

    @Test
    fun `element type is extracted from deeply parameterized NDOC subtype instantiation`() {
        Assert.assertEquals(typeOf<String>(), elementTypeFromNdocContainerType(typeOf<Subtype2<String>>()))
    }

    @Test
    fun `no element type is extracted from NDOC parameterized with a star-projected type`() {
        abstract class Parameterized<P : Any> : NamedDomainObjectContainer<P> {
            abstract val t: Parameterized<P>
        }

        Assert.assertNull(elementTypeFromNdocContainerType(Parameterized<*>::t.returnType))
    }

    @Test
    fun `no element type is extracted from in-projected types`() {
        abstract class Subtype<S : Any> : NamedDomainObjectContainer<S>

        Assert.assertNull(elementTypeFromNdocContainerType(typeOf<NamedDomainObjectContainer<in String>>()))
        Assert.assertNull(elementTypeFromNdocContainerType(typeOf<Subtype<in String>>()))
    }

    @Test
    fun `element type is extracted from out-projected types`() {
        abstract class Subtype<S : Any> : NamedDomainObjectContainer<S>

        Assert.assertEquals(typeOf<String>(), elementTypeFromNdocContainerType(typeOf<NamedDomainObjectContainer<out String>>()))
        Assert.assertEquals(typeOf<String>(), elementTypeFromNdocContainerType(typeOf<Subtype<out String>>()))
    }

    class Unrelated<@Suppress("unused") T>
    abstract class NdocStringSubtype : NamedDomainObjectContainer<String>

    abstract class Subtype1<S1 : Any> : NamedDomainObjectContainer<S1>
    abstract class Subtype2<S2 : Any> : Subtype1<S2>()
    abstract class MultiArgSubtype<A : Any, B : Any> : Subtype2<B>()

    abstract class Instantiation {
        abstract val subtype2OfString: Subtype2<String>
        abstract val multiArgSubtypeOfIntString: MultiArgSubtype<Int, String>
    }

    val parameterizedUnrelated: Unrelated<String> get() = TODO()
    val parameterizedSubtype: Subtype1<String> get() = TODO()
    val ndocOfString: NamedDomainObjectContainer<String> get() = TODO()
    val ndocOfListOfString: NamedDomainObjectContainer<List<String>> get() = TODO()
    val listOfString: List<String> get() = TODO()
    val inProjectedNdocOfString: NamedDomainObjectContainer<in String> get() = TODO()
    val outProjectedNdocOfString: NamedDomainObjectContainer<out String> get() = TODO()
    val inProjectedNdocSubtypeOfString: Subtype2<in String> get() = TODO()
    val outProjectedNdocSubtypeOfString: Subtype2<out String> get() = TODO()
}
