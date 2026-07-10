/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl

import org.gradle.api.Named
import org.gradle.api.attributes.AttributeContainer
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock

class AttributeContainerExtensionsTest {

    @Test
    fun named() {
        val attributes = mock<AttributeContainer> {
            on { named(any<Class<Named>>(), eq("foo")) } doReturn SomeNamed("foo")
        }

        val named = attributes.named<SomeNamed>("foo")
        assertThat(named.name, equalTo("foo"))

        inOrder(attributes) {
            verify(attributes).named(SomeNamed::class.java, "foo")
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun namedIsNotShadowedByJavaMember() {
        val attributes = mock<AttributeContainer> {
            on { named(any<Class<Named>>(), eq("foo")) } doReturn SomeNamed("foo")
        }

        val named: SomeNamed = with(attributes) { named("foo") }
        assertThat(named.name, equalTo("foo"))
    }
}
