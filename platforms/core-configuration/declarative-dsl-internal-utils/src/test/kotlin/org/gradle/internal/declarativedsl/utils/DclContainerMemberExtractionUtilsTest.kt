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

package org.gradle.internal.declarativedsl.utils

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.declarative.dsl.model.annotations.ElementFactoryName
import org.gradle.internal.declarativedsl.utils.DclContainerMemberExtractionUtils.elementFactoryFunctionNameFromElementType
import org.gradle.internal.declarativedsl.utils.DclContainerMemberExtractionUtils.elementTypeFromNdocContainerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.typeOf

class DclContainerMemberExtractionUtilsTest {
    @Test
    fun `the default element factory name is the decapitalized type name`() {
        assertEquals("any", elementFactoryFunctionNameFromElementType(typeOf<Any>()))
    }

    @Test
    fun `can use an annotation to provide a custom element factory name`() {
        @ElementFactoryName("custom")
        class Annotated

        assertEquals("custom", elementFactoryFunctionNameFromElementType(typeOf<Annotated>()))
    }

    @Test
    fun `throws error if element factory name annotation is missing a value`() {
        @ElementFactoryName
        class Annotated

        val exception = assertThrows<IllegalStateException> {
            elementFactoryFunctionNameFromElementType(typeOf<Annotated>())
        }
        assertTrue(ElementFactoryName::class.simpleName!! in exception.message!!)
    }

    @Test
    fun `no element type is extracted from unrelated parameterized types`() {
        assertNull(elementTypeFromNdocContainerType(typeOf<Unrelated<String>>()))
        assertNull(elementTypeFromNdocContainerType(::parameterizedUnrelated.javaGetter!!.genericReturnType))
    }

    @Test
    fun `element type is extracted from an exact NDOC instantiation`() {
        assertEquals(typeOf<String>(), elementTypeFromNdocContainerType(typeOf<NamedDomainObjectContainer<String>>()))
        assertEquals(String::class.java, elementTypeFromNdocContainerType(::ndocOfString.javaGetter!!.genericReturnType))
    }

    @Test
    fun `parameterized types get properly extracted as element types`() {
        assertEquals(typeOf<List<String>>(), elementTypeFromNdocContainerType(typeOf<NamedDomainObjectContainer<List<String>>>()))
        assertEquals(::listOfString.javaGetter!!.genericReturnType, elementTypeFromNdocContainerType(::ndocOfListOfString.javaGetter!!.genericReturnType))
    }

    @Test
    fun `supertype arguments get properly discovered in types with multiple type arguments`() {
        assertEquals(typeOf<String>(), elementTypeFromNdocContainerType(typeOf<MultiArgSubtype<Int, String>>()))
        assertEquals(String::class.java, elementTypeFromNdocContainerType(Instantiation::multiArgSubtypeOfIntString.javaGetter!!.genericReturnType))
    }

    @Test
    fun `element type is extracted from an NDOC subtype with concrete type`() {
        assertEquals(typeOf<String>(), elementTypeFromNdocContainerType(typeOf<NdocStringSubtype>()))
        assertEquals(String::class.java, elementTypeFromNdocContainerType(NdocStringSubtype::class.java))
    }

    @Test
    fun `element type is extracted from parameterized NDOC subtype instantiation`() {
        assertEquals(typeOf<String>(), elementTypeFromNdocContainerType(typeOf<Subtype1<String>>()))
        assertEquals(String::class.java, elementTypeFromNdocContainerType(::parameterizedSubtype.javaGetter!!.genericReturnType))
    }

    @Test
    fun `element type is extracted from deeply parameterized NDOC subtype instantiation`() {
        assertEquals(typeOf<String>(), elementTypeFromNdocContainerType(typeOf<Subtype2<String>>()))
        assertEquals(String::class.java, elementTypeFromNdocContainerType(Instantiation::subtype2OfString.javaGetter!!.genericReturnType))
    }

    @Test
    fun `no element type is extracted from NDOC parameterized with a star-projected type`() {
        abstract class Parameterized<P : Any> : NamedDomainObjectContainer<P> {
            abstract val t: Parameterized<P>
        }

        assertNull(elementTypeFromNdocContainerType(Parameterized<*>::t.returnType))
        assertNull(elementTypeFromNdocContainerType(Parameterized::class.java))
    }

    @Test
    fun `no element type is extracted from projected types`() {
        abstract class Subtype<S : Any> : NamedDomainObjectContainer<S>

        assertNull(elementTypeFromNdocContainerType(typeOf<NamedDomainObjectContainer<in String>>()))
        assertNull(elementTypeFromNdocContainerType(typeOf<NamedDomainObjectContainer<out String>>()))
        assertNull(elementTypeFromNdocContainerType(typeOf<Subtype<in String>>()))
        assertNull(elementTypeFromNdocContainerType(typeOf<Subtype<out String>>()))

        assertNull(elementTypeFromNdocContainerType(::inProjectedNdocOfString.javaGetter!!.genericReturnType))
        assertNull(elementTypeFromNdocContainerType(::outProjectedNdocOfString.javaGetter!!.genericReturnType))
        assertNull(elementTypeFromNdocContainerType(::inProjectedNdocSubtypeOfString.javaGetter!!.genericReturnType))
        assertNull(elementTypeFromNdocContainerType(::outProjectedNdocSubtypeOfString.javaGetter!!.genericReturnType))
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
