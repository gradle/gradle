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

import org.gradle.api.Action
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.reflect.TypeOf

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.anyVararg
import com.nhaarman.mockito_kotlin.doNothing
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock

import org.junit.Test


class ExtensionContainerExtensionsTest {

    @Test
    fun add() {

        val extensions = mock<ExtensionContainer>()
        doNothing().`when`(extensions).add(any<TypeOf<SomeExtension<*>>>(), any(), any())

        val instance = SomeExtension("some")
        extensions.add<SomeExtension<String>>("name", instance)

        inOrder(extensions) {
            verify(extensions).add(typeOf<SomeExtension<String>>(), "name", instance)
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun create() {

        val extensions = mock<ExtensionContainer> {
            on { create(any(), any<Class<SomeExtension<Long>>>(), anyVararg()) } doReturn SomeExtension(23L)
        }

        extensions.create<SomeExtension<Long>>("name", 23L)

        inOrder(extensions) {
            verify(extensions).create(any(), any<Class<SomeExtension<Long>>>(), anyVararg())
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun getByType() {

        val extensions = mock<ExtensionContainer> {
            on { getByType(any<TypeOf<SomeInterface<Long>>>()) } doReturn mock<SomeInterface<Long>>()
        }

        extensions.getByType<SomeInterface<Long>>()

        inOrder(extensions) {
            verify(extensions).getByType(any<TypeOf<SomeInterface<Long>>>())
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun findByType() {

        val extensions = mock<ExtensionContainer> {
            on { findByType(any<TypeOf<SomeInterface<Long>>>()) } doReturn mock<SomeInterface<Long>>()
        }

        extensions.findByType<SomeInterface<Long>>()

        inOrder(extensions) {
            verify(extensions).findByType(any<TypeOf<SomeInterface<Long>>>())
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun configure() {

        val extensions = mock<ExtensionContainer>()
        doNothing().`when`(extensions).configure(any<TypeOf<SomeInterface<Long>>>(), any<Action<SomeInterface<Long>>>())

        extensions.configure<SomeInterface<Long>> { println(p) }

        inOrder(extensions) {
            verify(extensions).configure(any<TypeOf<SomeInterface<Long>>>(), any<Action<SomeInterface<Long>>>())
            verifyNoMoreInteractions()
        }
    }
}


interface SomeInterface<T> {
    val p: T
}


class SomeExtension<T>(override val p: T) : SomeInterface<T>
