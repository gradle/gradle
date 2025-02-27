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

package org.gradle.kotlin.dsl

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.junit.Test
import kotlin.test.assertSame

class CollectionPropertyExtensionsTest {

    @Test
    fun `adding value to list invokes add(T)`() {
        val list = mock<ListProperty<String>>()

        list += "foo"

        verify(list).add("foo")
    }

    @Test
    fun `adding collection to list invokes add(Collection of T)`() {
        val list = mock<ListProperty<String>>()
        val rawList = mock<List<String>>()

        list += rawList

        verify(list).addAll(rawList)
    }

    @Test
    fun `adding provider of collection invokes addAll(Provider of list of T)`() {
        val list = mock<ListProperty<String>>()
        val provider = mock<Provider<List<String>>>()

        list += provider

        verify(list).addAll(provider)
    }

    @Test
    fun `map indexed assignment using provider invokes put`() {
        val map = mock<MapProperty<String, Int>>()
        val valueProvider = mock<Provider<Int>>()

        map["a"] = valueProvider

        verify(map).put("a", valueProvider)
    }

    @Test
    fun `map indexed assignment using provider of subtype`() {
        val map = mock<MapProperty<String, Number>>()
        val valueProvider = mock<Provider<Int>>()

        map["a"] = valueProvider

        verify(map).put("a", valueProvider)
    }

    @Test
    fun `map indexed assignment invokes put`() {
        val map = mock<MapProperty<String, Int>>()

        map["a"] = 10

        verify(map).put("a", 10)
    }

    @Test
    fun `map indexed access invokes getting`() {
        val map = mock<MapProperty<String, Int>>()
        val providerOfInt = mock<Provider<Int>>()

        whenever(map.getting("a")).thenReturn(providerOfInt)

        val result = map["a"]

        verify(map).getting("a")
        assertSame(providerOfInt, result)
    }


}
