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

package org.gradle.configurationcache.isolation

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.gradle.api.IsolatedAction
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.provider.CollectionPropertyInternal
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.configurationcache.serialization.IsolateOwners
import org.gradle.configurationcache.serialization.beans.DefaultBeanStateWriterLookup
import org.gradle.configurationcache.serialization.codecs.beanStateReaderLookupForTesting
import org.gradle.configurationcache.serialization.codecs.jos.JavaSerializationEncodingLookup
import org.gradle.configurationcache.services.IsolatedActionCodecsFactory
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.isolation.IsolatedActionsForTesting.isolatedActionLambdaWith
import org.gradle.util.TestUtil
import org.gradle.util.TestUtil.objectFactory
import org.gradle.util.TestUtil.propertyFactory
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import java.util.function.Consumer


typealias TestableIsolatedAction<T> = IsolatedAction<in Consumer<T>>


class IsolatedActionSerializerTest {

    @Test
    fun `can serialize Kotlin action`() {
        assertThat(
            valueCarriedBy(roundtripOf(isolatedActionCarrying(42))),
            equalTo(42)
        )
    }

    @Test
    fun `can serialize Java lambda`() {
        assertThat(
            valueCarriedBy(roundtripOf(isolatedActionLambdaWith(42))),
            equalTo(42)
        )
    }

    @Test
    fun `can serialize Kotlin action with Property`() {
        val loaded = valueCarriedBy(roundtripOf(isolatedActionCarrying(propertyOf("42"))))
        assertThat(
            loaded.get(),
            equalTo("42")
        )
    }

    @Test
    fun `can serialize Java lambda with Property`() {
        assertProviderRoundtrip(
            propertyOf("42"),
            "42"
        )
    }

    @Test
    fun `can serialize Java lambda with ListProperty`() {
        assertProviderRoundtrip(
            listPropertyOf(1, 2, 3),
            listOf(1, 2, 3)
        )
    }

    @Test
    fun `can serialize Java lambda with MapProperty`() {
        assertProviderRoundtrip(
            mapPropertyOf("foo" to 1, "bar" to 2),
            mapOf("foo" to 1, "bar" to 2)
        )
    }

    @Test
    fun `can serialize Java lambda with SetProperty`() {
        assertProviderRoundtrip(
            setPropertyOf(1, 2, 3),
            setOf(1, 2, 3)
        )
    }

    interface Dsl {
        val property: Property<String>
    }

    @Test
    fun `can serialize Java lambda with Gradle model`() {
        val stored = newInstance<Dsl>().apply {
            property.set("42")
        }
        val loaded = valueCarriedBy(roundtripOf(isolatedActionLambdaWith(stored)))
        assertThat(
            loaded.property.get(),
            equalTo("42")
        )
    }

    private
    fun <T : Any> assertProviderRoundtrip(property: Provider<out T>, expectedValue: T) {
        val loaded = valueCarriedBy(roundtripOf(isolatedActionLambdaWith(property)))
        assertThat(loaded.get(), equalTo(expectedValue))
    }

    private
    inline fun <reified K : Any> setPropertyOf(vararg values: K) =
        objectFactory().setProperty(K::class.java).apply {
            if (values.isNotEmpty()) {
                addAll(*values)
            }
        }

    private
    inline fun <reified K : Any, reified V : Any> mapPropertyOf(vararg values: Pair<K, V>) =
        objectFactory().mapProperty(K::class.java, V::class.java).apply {
            values.forEach { (k, v) ->
                put(k, v)
            }
        }

    private
    inline fun <reified T : Any> listPropertyOf(vararg values: T) =
        objectFactory().listProperty(T::class.java).apply {
            if (values.isNotEmpty()) {
                uncheckedNonnullCast<CollectionPropertyInternal<T, Collection<T>>>(this).appendAll(*values)
            }
        }

    private
    inline fun <reified T : Any> propertyOf(value: T) =
        objectFactory().property(T::class.java).apply {
            set(value)
        }

    private
    inline fun <reified T> newInstance() =
        objectFactory().newInstance(T::class.java)

    private
    fun <T : Any> valueCarriedBy(isolatedAction: TestableIsolatedAction<T>): T {
        lateinit var value: T
        isolatedAction.execute {
            value = it
        }
        return value
    }

    private
    fun <T> roundtripOf(action: TestableIsolatedAction<T>): TestableIsolatedAction<T> =
        deserialize(serialize(action))

    private
    fun <T> serialize(action: TestableIsolatedAction<T>) =
        IsolatedActionSerializer(ownerGradle(), DefaultBeanStateWriterLookup(), isolatedActionCodecsFactory())
            .serialize(action)

    private
    fun <T> deserialize(serialized: SerializedIsolatedActionGraph<TestableIsolatedAction<T>>) =
        IsolatedActionDeserializer(ownerGradle(), beanStateReaderLookupForTesting(), isolatedActionCodecsFactory())
            .deserialize(serialized)

    private
    fun ownerGradle() = IsolateOwners.OwnerGradle(
        mock<GradleInternal> {
            on { services } doReturn TestUtil.services()
        }
    )

    private
    fun <T> isolatedActionCarrying(value: T): TestableIsolatedAction<T> =
        TestableIsolatedAction { accept(value) }

    private
    fun isolatedActionCodecsFactory(): IsolatedActionCodecsFactory =
        IsolatedActionCodecsFactory(
            javaSerializationEncodingLookup = JavaSerializationEncodingLookup(),
            propertyFactory = propertyFactory(),
            fileFactory = TestFiles.fileFactory(),
            filePropertyFactory = TestFiles.filePropertyFactory()
        )
}
