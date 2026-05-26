/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.serialize.graph.codecs

import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.findCodecThatWidensIncompatibly
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.lang.reflect.Proxy


class FindCodecThatWidensIncompatiblyTest {
    private interface Base
    private open class Mid : Base
    private class Leaf : Mid()
    private class Unrelated

    @Test
    fun `returns null when no codec is registered for the runtime type`() {
        val context = fakeContext(emptyMap())
        assertNull(context.findCodecThatWidensIncompatibly(Mid::class.java))
    }

    @Test
    fun `returns null when the registered codec is not a WideningCodec`() {
        val plainCodec = object : Codec<Mid> {
            override suspend fun WriteContext.encode(value: Mid) = Unit
            override suspend fun ReadContext.decode(): Mid? = null
        }
        val context = fakeContext(mapOf(Mid::class.java to plainCodec))
        assertNull(context.findCodecThatWidensIncompatibly(Mid::class.java))
    }

    @Test
    fun `returns null when the declared type can accept the codec's decoded type`() {
        // Codec decodes Mid; field declared Base — Base.isAssignableFrom(Mid) is true,
        // so the decoded value fits and no widening problem is reported.
        val widening = wideningCodec(decodedType = Mid::class.java)
        val context = fakeContext(mapOf(Leaf::class.java to widening))
        assertNull(context.findCodecThatWidensIncompatibly(declaredType = Base::class.java, runtimeType = Leaf::class.java))
    }

    @Test
    fun `returns the codec when its decoded type is unrelated to the declared type`() {
        // Codec decodes Mid; field declared Unrelated — no subtyping relation,
        // assignment is impossible, so the widening codec is returned for reporting.
        val widening = wideningCodec(decodedType = Mid::class.java)
        val context = fakeContext(mapOf(Mid::class.java to widening))
        assertSame(widening, context.findCodecThatWidensIncompatibly(declaredType = Unrelated::class.java, runtimeType = Mid::class.java))
    }

    @Test
    fun `returns the codec when the declared type is a strict subtype of the codec's decoded type`() {
        // Codec decodes Mid; field declared Leaf — Mid.isAssignableFrom(Leaf) is true
        // (carve-out for callers like JavaRecordCodec), BUT this helper only checks the
        // OPPOSITE direction (Leaf.isAssignableFrom(Mid)). The opposite is false, so the
        // helper still returns the codec — the caller is expected to apply its own
        // subtype carve-out if the site warrants it.
        val widening = wideningCodec(decodedType = Mid::class.java)
        val context = fakeContext(mapOf(Leaf::class.java to widening))
        assertSame(widening, context.findCodecThatWidensIncompatibly(declaredType = Leaf::class.java, runtimeType = Leaf::class.java))
    }

    @Test
    fun `defaults runtimeType to declaredType when omitted`() {
        // Property/lambda call sites pass only one type signal. Verify the default
        // overload still finds a registered widening codec keyed on that single type.
        val widening = wideningCodec(decodedType = Mid::class.java)
        val context = fakeContext(mapOf(Unrelated::class.java to widening))
        assertSame(widening, context.findCodecThatWidensIncompatibly(declaredType = Unrelated::class.java))
    }

    private fun <T : Any> wideningCodec(decodedType: Class<T>): WideningCodec<T> =
        object : WideningCodec<T> {
            override val decodedType: Class<T> = decodedType
            override val wideningFix: String = "Use a supported type instead."
            override suspend fun WriteContext.encode(value: T) = Unit
            override suspend fun ReadContext.decode(): T? = null
        }

    /**
     * Stubs the small `codecForRuntimeType` surface of [WriteContext]; every other
     * method throws via a JDK Proxy so an accidental call surfaces loudly instead
     * of silently returning a default. The Proxy avoids hand-stubbing the ~50
     * methods on the interface.
     */
    private fun fakeContext(codecs: Map<Class<*>, Any?>): WriteContext {
        val throwingDelegate = Proxy.newProxyInstance(
            WriteContext::class.java.classLoader,
            arrayOf(WriteContext::class.java)
        ) { _, method, _ -> error("FakeWriteContext: unexpected call to ${method.name}") } as WriteContext
        return object : WriteContext by throwingDelegate {
            override fun codecForRuntimeType(type: Class<*>): Any? = codecs[type]
        }
    }
}
