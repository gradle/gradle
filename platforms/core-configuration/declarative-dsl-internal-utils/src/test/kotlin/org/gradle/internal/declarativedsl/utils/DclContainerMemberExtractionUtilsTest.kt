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
        class Unrelated<T>

        assertNull(elementTypeFromNdocContainerType(typeOf<Unrelated<String>>()))
    }

    @Test
    fun `element type is extracted from an exact NDOC instantiation`() {
        assertEquals(typeOf<String>(), elementTypeFromNdocContainerType(typeOf<NamedDomainObjectContainer<String>>()))
    }

    @Test
    fun `element type is extracted from an NDOC subtype with concrete type`() {
        abstract class Subtype : NamedDomainObjectContainer<String>

        assertEquals(typeOf<String>(), elementTypeFromNdocContainerType(typeOf<Subtype>()))
    }

    @Test
    fun `element type is extracted from parameterized NDOC subtype instantiation`() {
        abstract class Subtype<S> : NamedDomainObjectContainer<S>

        assertEquals(typeOf<String>(), elementTypeFromNdocContainerType(typeOf<Subtype<String>>()))
    }

    @Test
    fun `element type is extracted from deeply parameterized NDOC subtype instantiation`() {
        abstract class Subtype1<S1> : NamedDomainObjectContainer<S1>
        abstract class Subtype2<S2> : Subtype1<S2>()

        assertEquals(typeOf<String>(), elementTypeFromNdocContainerType(typeOf<Subtype2<String>>()))
    }

    @Test
    fun `no element type is extracted from NDOC parameterized with a star-projected type`() {
        abstract class Parameterized<P> : NamedDomainObjectContainer<P> {
            abstract val t: Parameterized<P>
        }

        assertNull(elementTypeFromNdocContainerType(Parameterized<*>::t.returnType))
    }

    @Test
    fun `no element type is extracted from projected types`() {
        abstract class Subtype<S> : NamedDomainObjectContainer<S>

        assertNull(elementTypeFromNdocContainerType(typeOf<NamedDomainObjectContainer<in String>>()))
        assertNull(elementTypeFromNdocContainerType(typeOf<NamedDomainObjectContainer<out String>>()))
        assertNull(elementTypeFromNdocContainerType(typeOf<Subtype<in String>>()))
        assertNull(elementTypeFromNdocContainerType(typeOf<Subtype<out String>>()))
    }
}
