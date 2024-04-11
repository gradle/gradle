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

import com.nhaarman.mockitokotlin2.mock
import org.gradle.api.IsolatedAction
import org.gradle.api.provider.Property
import org.gradle.configurationcache.extensions.uncheckedCast
import org.gradle.configurationcache.serialization.IsolateOwner
import org.gradle.configurationcache.serialization.beans.BeanStateWriterLookup
import org.gradle.configurationcache.serialization.codecs.beanStateReaderLookupForTesting
import org.gradle.configurationcache.serialization.codecs.jos.JavaSerializationEncodingLookup
import org.gradle.configurationcache.services.IsolatedActionCodecsFactory
import org.gradle.internal.isolation.IsolatedActionsForTesting.isolatedActionLambdaWith
import org.gradle.util.TestUtil.objectFactory
import org.gradle.util.TestUtil.propertyFactory
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Ignore
import org.junit.Test
import java.util.function.Consumer


typealias TestableIsolatedAction<T> = IsolatedAction<in Consumer<T>>


class IsolatedActionSerializerTest {

    @Test
    fun `can serialize Kotlin action`() {
        assertThat(
            replay(roundtripOf(isolatedActionCarrying(42))),
            equalTo(42)
        )
    }

    @Test
    fun `can serialize Java lambda`() {
        assertThat(
            replay(roundtripOf(isolatedActionLambdaWith(42))),
            equalTo(42)
        )
    }

    @Test
    fun `can serialize Kotlin action with Gradle property`() {
        val loaded = replay(roundtripOf(isolatedActionCarrying(propertyOf("42"))))
        assertThat(
            loaded.get(),
            equalTo("42")
        )
    }

    @Test
    fun `can serialize Java lambda with Gradle property`() {
        val loaded = replay(roundtripOf(isolatedActionLambdaWith(propertyOf("42"))))
        assertThat(
            loaded.get(),
            equalTo("42")
        )
    }

    interface Dsl {
        val property: Property<String>
    }

    @Ignore("wip")
    @Test
    fun `can serialize Java lambda with Gradle model`() {
        val stored = newInstance<Dsl>().apply {
            property.set("42")
        }
        val loaded = replay(roundtripOf(isolatedActionLambdaWith(stored)))
        assertThat(
            loaded.property.get(),
            equalTo("42")
        )
    }

    private
    inline fun <reified T> propertyOf(value: T) =
        objectFactory().property(T::class.java).apply {
            set(value)
        }

    private
    inline fun <reified T> newInstance() =
        objectFactory().newInstance(T::class.java)

    private
    fun <T> isolatedActionCarrying(value: T): TestableIsolatedAction<T> =
        TestableIsolatedAction { accept(value) }

    private
    fun <T : Any> replay(isolatedAction: TestableIsolatedAction<T>): T {
        lateinit var loaded: T
        isolatedAction.execute {
            loaded = it
        }
        return loaded
    }

    private
    fun <T> roundtripOf(action: TestableIsolatedAction<T>): TestableIsolatedAction<T> =
        deserialize(serialize(action))

    private
    fun <T> serialize(action: TestableIsolatedAction<T>): SerializedAction =
        IsolatedActionSerializer(IsolateOwner.OwnerGradle(mock()), BeanStateWriterLookup(), isolatedActionCodecsFactory())
            .serialize(action)

    private
    fun <T> deserialize(serialized: SerializedAction): TestableIsolatedAction<T> =
        IsolatedActionDeserializer(IsolateOwner.OwnerGradle(mock()), beanStateReaderLookupForTesting(), isolatedActionCodecsFactory())
            .deserialize(serialized)
            .uncheckedCast()

    private
    fun isolatedActionCodecsFactory(): IsolatedActionCodecsFactory =
        IsolatedActionCodecsFactory(
            javaSerializationEncodingLookup = JavaSerializationEncodingLookup(),
            propertyFactory = propertyFactory()
        )
}
