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
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class ObjectFactoryExtensionsTest {

    @Test
    fun named() {

        val objectFactory = mock<ObjectFactory> {
            on { named(any<Class<Named>>(), eq("foo")) } doReturn SomeNamed("foo")
        }

        val named = objectFactory.named<SomeNamed>("foo")
        assertThat(named.name, equalTo("foo"))

        inOrder(objectFactory) {
            verify(objectFactory).named(SomeNamed::class.java, "foo")
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun newInstance() {

        val objectFactory = mock<ObjectFactory> {
            on { newInstance(any<Class<*>>(), eq("foo")) } doReturn SomeNamed("foo")
        }

        val instance = objectFactory.newInstance<SomeNamed>("foo")
        assertThat(instance.name, equalTo("foo"))

        inOrder(objectFactory) {
            verify(objectFactory).newInstance(SomeNamed::class.java, "foo")
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun property() {

        val objectFactory = mock<ObjectFactory> {
            on { property(any<Class<*>>()) } doReturn mock<Property<String>>()
        }

        objectFactory.property<String>()

        inOrder(objectFactory) {
            verify(objectFactory).property(String::class.java)
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun setProperty() {

        val objectFactory = mock<ObjectFactory> {
            on { setProperty(any<Class<*>>()) } doReturn mock<SetProperty<String>>()
        }

        objectFactory.setProperty<String>()

        inOrder(objectFactory) {
            verify(objectFactory).setProperty(String::class.java)
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun listProperty() {

        val objectFactory = mock<ObjectFactory> {
            on { listProperty(any<Class<*>>()) } doReturn mock<ListProperty<String>>()
        }

        objectFactory.listProperty<String>()

        inOrder(objectFactory) {
            verify(objectFactory).listProperty(String::class.java)
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun mapProperty() {

        val objectFactory = mock<ObjectFactory> {
            on { mapProperty(any<Class<*>>(), any<Class<*>>()) } doReturn mock<MapProperty<String, Int>>()
        }

        objectFactory.mapProperty<String, Int>()

        inOrder(objectFactory) {
            verify(objectFactory).mapProperty(String::class.java, Int::class.javaObjectType)
            verifyNoMoreInteractions()
        }
    }
}


class SomeNamed(private val name: String) : Named {
    override fun getName(): String = name
}
