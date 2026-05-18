/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.cc.impl.serialization.codecs

import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.codecs.Bindings
import org.gradle.internal.serialize.graph.decodePreservingIdentity
import org.gradle.internal.serialize.graph.encodePreservingIdentityOf
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.Assert.fail
import org.junit.Test


class UserTypesCodecTest : AbstractUserTypeCodecTest() {

    @Test
    fun `can handle deeply nested graphs`() {

        val deepGraph = Peano.fromInt(1024)

        val read = configurationCacheRoundtripOf(deepGraph)

        assertThat(
            read.toInt(),
            equalTo(deepGraph.toInt())
        )
    }

    class Bar(val barString: String) {
        var foo: Foo? = null
    }

    class Foo(val fooString: String) {
        var bar: Bar? = null
    }

    @Test
    fun `can handle circular bean references`() {
        val foo = Foo("fooString")
        val bar = Bar("barString")
        foo.bar = bar
        bar.foo = foo

        val readFoo = configurationCacheRoundtripOf(foo)

        assertThat(readFoo.fooString, equalTo("fooString"))
        assertThat(readFoo.bar!!.barString, equalTo("barString"))
        assertThat(readFoo.bar!!.foo, sameInstance(readFoo))
    }

    class SelfRef {
        var inner: SelfRef? = null
    }

    @Test
    fun `integrity check detects circular reference from a buggy codec`() {
        // Codec that intentionally reads its inner state before calling putInstance,
        // simulating the kind of bug the integrity check is meant to catch.
        val brokenCodec: Codec<SelfRef> = object : Codec<SelfRef> {
            override suspend fun WriteContext.encode(value: SelfRef) {
                encodePreservingIdentityOf(value) {
                    write(value.inner)
                }
            }

            override suspend fun ReadContext.decode(): SelfRef =
                decodePreservingIdentity { id ->
                    val inner = read() as SelfRef?
                    val ref = SelfRef()
                    ref.inner = inner
                    isolate.identities.putInstance(id, ref)
                    ref
                }
        }
        val codec = Bindings.of { bind(brokenCodec) }.build()

        val self = SelfRef().also { it.inner = it }

        try {
            configurationCacheRoundtripOf(self, codec, integrityCheck = true)
            fail("Expected exception to be thrown")
        } catch (e: IllegalStateException) {
            assertThat(e.message, containsString("Unresolvable circular reference detected when decoding id="))
        }
    }

    class Item(val payload: String)
    class TwoItems(val a: Item, val b: Item)

    @Test
    fun `integrity check detects unexpected reuse of an id from a buggy codec`() {
        // Codec that during decode registers itself correctly but also pollutes the NEXT id slot.
        // The next decode call will see that slot already occupied and the integrity check should fire.
        val pollutingItemCodec: Codec<Item> = object : Codec<Item> {
            override suspend fun WriteContext.encode(value: Item) {
                encodePreservingIdentityOf(value) {
                    writeString(value.payload)
                }
            }

            override suspend fun ReadContext.decode(): Item =
                decodePreservingIdentity { id ->
                    val item = Item(readString())
                    isolate.identities.putInstance(id, item)
                    // Buggy: pollute the next id slot. A correct codec only writes its own id.
                    isolate.identities.putInstance(id + 1, "stale")
                    item
                }
        }

        // Plain wrapper codec that just forwards to its children without consuming an identity slot,
        // so the two children get assigned consecutive ids in the isolate's identities map.
        val twoItemsCodec: Codec<TwoItems> = object : Codec<TwoItems> {
            override suspend fun WriteContext.encode(value: TwoItems) {
                write(value.a)
                write(value.b)
            }

            override suspend fun ReadContext.decode(): TwoItems =
                TwoItems(read() as Item, read() as Item)
        }

        val codec = Bindings.of {
            bind(twoItemsCodec)
            bind(pollutingItemCodec)
        }.build()

        try {
            configurationCacheRoundtripOf(TwoItems(Item("a"), Item("b")), codec, integrityCheck = true)
            fail("Expected exception to be thrown")
        } catch (e: IllegalStateException) {
            assertThat(e.message, containsString("Unexpected reuse of id="))
        }
    }

    @Test
    fun `internal types codec leaves not implemented trace for unsupported types`() {

        val unsupportedBean = 42 to "42"
        val problems = serializationProblemsOf(unsupportedBean, codecs().internalTypesCodec())
        val problem = problems.single()
        assertInstanceOf<PropertyTrace.Gradle>(
            problem.trace
        )
        assertThat(
            problem.message.toString(),
            equalTo("objects of type 'kotlin.Pair' are not yet supported with the configuration cache.")
        )
    }

    @Test
    fun `can handle anonymous enum subtypes`() {
        EnumSuperType.entries.forEach {
            assertThat(
                configurationCacheRoundtripOf(it),
                sameInstance(it)
            )
        }
    }

    enum class EnumSuperType {

        SubType1 {
            override fun displayName() = "one"
        },
        SubType2 {
            override fun displayName() = "two"
        };

        abstract fun displayName(): String
    }

    @Test
    fun `preserves identity of java util logging Level`() {
        configurationCacheRoundtripOf(java.util.logging.Level.INFO to java.util.logging.Level.WARNING).run {
            assertThat(
                first,
                sameInstance(java.util.logging.Level.INFO)
            )
            assertThat(
                second,
                sameInstance(java.util.logging.Level.WARNING)
            )
        }
    }

    @Test
    fun `Peano sanity check`() {

        assertThat(
            Peano.fromInt(0),
            equalTo(Peano.Z)
        )

        assertThat(
            Peano.fromInt(1024).toInt(),
            equalTo(1024)
        )
    }

    sealed class Peano {

        companion object {

            fun fromInt(n: Int): Peano = (0 until n).fold(Z as Peano) { acc, _ -> S(acc) }
        }

        fun toInt(): Int = sequence().count() - 1

        object Z : Peano() {
            override fun toString() = "Z"
        }

        data class S(val n: Peano) : Peano() {
            override fun toString() = "S($n)"
        }

        private
        fun sequence() = generateSequence(this) { previous ->
            when (previous) {
                is Z -> null
                is S -> previous.n
            }
        }
    }
}
