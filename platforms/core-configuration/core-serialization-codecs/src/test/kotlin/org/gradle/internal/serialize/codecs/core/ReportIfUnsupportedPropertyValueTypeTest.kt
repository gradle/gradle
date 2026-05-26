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

package org.gradle.internal.serialize.codecs.core

import org.gradle.api.problems.internal.ProblemLocator
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.problems.failure.Failure
import org.gradle.internal.problems.failure.FailureFactory
import org.gradle.internal.reflect.UnsupportedTypeException
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.IsolateOwner
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.WriteIsolate
import org.gradle.internal.serialize.graph.codecs.WideningCodec
import org.gradle.internal.serialize.graph.reportIfUnsupportedPropertyValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine


/**
 * Direct tests for [reportIfUnsupportedPropertyValueType]. Verifies that the helper
 * dispatches on its `valueType` argument — distinct argument types produce distinct
 * attached [UnsupportedTypeException] messages.
 *
 * This guards the specific regression that the integration tests in
 * `ConfigurationCacheGradlePropertiesIntegrationTest` cannot directly catch: the CC
 * problem aggregator dedupes by message + trace before any assertion surface, so two
 * `onProblem` calls that share a trace collapse to one user-visible problem. Asserting
 * at the helper level — *before* aggregation — directly checks that each call produces
 * a distinct exception keyed on `valueType`.
 */
class ReportIfUnsupportedPropertyValueTypeTest {
    private class Decoded
    private class SubjectA
    private class SubjectB

    @Test
    fun `returns false and does not report when no codec is registered for the value type`() {
        val capturedProblems = mutableListOf<PropertyProblem>()
        val context = fakeContext(codecs = emptyMap(), capturedProblems = capturedProblems)

        val dropped = runSuspending {
            context.reportIfUnsupportedPropertyValueType(ListProperty::class.java, SubjectA::class.java)
        }

        assertFalse(dropped)
        assertTrue("no problems should be reported", capturedProblems.isEmpty())
    }

    @Test
    fun `returns false and does not report when the codec is not a WideningCodec`() {
        val capturedProblems = mutableListOf<PropertyProblem>()
        val plainCodec = object : Codec<SubjectA> {
            override suspend fun WriteContext.encode(value: SubjectA) = Unit
            override suspend fun ReadContext.decode(): SubjectA? = null
        }
        val context = fakeContext(
            codecs = mapOf(SubjectA::class.java to plainCodec),
            capturedProblems = capturedProblems
        )

        val dropped = runSuspending {
            context.reportIfUnsupportedPropertyValueType(ListProperty::class.java, SubjectA::class.java)
        }

        assertFalse(dropped)
        assertTrue("no problems should be reported", capturedProblems.isEmpty())
    }

    @Test
    fun `returns true and reports when a WideningCodec produces an incompatible decode type`() {
        val capturedProblems = mutableListOf<PropertyProblem>()
        val context = fakeContext(
            codecs = mapOf(SubjectA::class.java to wideningCodec()),
            capturedProblems = capturedProblems
        )

        val dropped = runSuspending {
            context.reportIfUnsupportedPropertyValueType(ListProperty::class.java, SubjectA::class.java)
        }

        assertTrue(dropped)
        assertEquals(1, capturedProblems.size)
        val cause = capturedProblems.single().exception
        assertTrue("exception must be UnsupportedTypeException, was ${cause?.javaClass}", cause is UnsupportedTypeException)
        assertTrue(
            "exception message must name the value type, was: ${cause?.message}",
            cause!!.message!!.contains(SubjectA::class.java.simpleName)
        )
    }

    @Test
    fun `distinct value types produce distinct attached exception messages`() {
        // Core guard for MapPropertyCodec's two-call pattern: a regression that calls
        // the helper twice with the SAME valueType (instead of keyType then valueType)
        // would produce two identical exception messages — this assertion would fail.
        val capturedProblems = mutableListOf<PropertyProblem>()
        val context = fakeContext(
            codecs = mapOf(
                SubjectA::class.java to wideningCodec(),
                SubjectB::class.java to wideningCodec()
            ),
            capturedProblems = capturedProblems
        )

        runSuspending {
            context.reportIfUnsupportedPropertyValueType(MapProperty::class.java, SubjectA::class.java)
            context.reportIfUnsupportedPropertyValueType(MapProperty::class.java, SubjectB::class.java)
        }

        assertEquals(2, capturedProblems.size)
        val messageA = capturedProblems[0].exception!!.message!!
        val messageB = capturedProblems[1].exception!!.message!!
        assertNotEquals("messages must differ between distinct value types", messageA, messageB)
        assertTrue("first message names SubjectA, was: $messageA", messageA.contains("SubjectA"))
        assertTrue("second message names SubjectB, was: $messageB", messageB.contains("SubjectB"))
    }

    @Test
    fun `custom resolution lambda overrides the default wideningFix`() {
        // The resolution-builder lambda lets callers (notably MapPropertyCodec) supply
        // a context-specific fix line instead of the codec's bean-field-oriented default.
        // Verifies the lambda is invoked with both the matched WideningCodec and the
        // original valueType, and its return value is attached to the exception.
        val capturedProblems = mutableListOf<PropertyProblem>()
        val context = fakeContext(
            codecs = mapOf(SubjectA::class.java to wideningCodec()),
            capturedProblems = capturedProblems
        )

        runSuspending {
            // Default — uses widening.wideningFix
            context.reportIfUnsupportedPropertyValueType(ListProperty::class.java, SubjectA::class.java)
            // Custom — MapProperty-style fix that names the offending valueType and the position
            context.reportIfUnsupportedPropertyValueType(MapProperty::class.java, SubjectA::class.java) { _, valueType ->
                "Avoid using ${valueType.simpleName} as a MapProperty key."
            }
        }

        assertEquals(2, capturedProblems.size)
        val defaultResolution = (capturedProblems[0].exception as UnsupportedTypeException).resolutions.single()
        val customResolution = (capturedProblems[1].exception as UnsupportedTypeException).resolutions.single()
        assertEquals("Use a supported type instead.", defaultResolution)
        assertEquals("Avoid using SubjectA as a MapProperty key.", customResolution)
        assertNotEquals("default and custom resolutions must differ", defaultResolution, customResolution)
    }

    private fun wideningCodec(): WideningCodec<Decoded> = object : WideningCodec<Decoded> {
        override val decodedType: Class<Decoded> = Decoded::class.java
        override val wideningFix: String = "Use a supported type instead."
        override suspend fun WriteContext.encode(value: Decoded) = Unit
        override suspend fun ReadContext.decode(): Decoded? = null
    }

    /**
     * Stubs the small WriteContext surface that [reportIfUnsupportedPropertyValueType]
     * touches (codecForRuntimeType, trace, isolate.owner.service, onProblem). Every
     * other method throws via a JDK Proxy so an accidental call is loud, not silent.
     */
    private fun fakeContext(
        codecs: Map<Class<*>, Any?>,
        capturedProblems: MutableList<PropertyProblem>
    ): WriteContext {
        val throwingDelegate = throwingProxy(WriteContext::class.java)
        val isolateStub = fakeWriteIsolate()
        return object : WriteContext by throwingDelegate {
            override fun codecForRuntimeType(type: Class<*>): Any? = codecs[type]
            override var trace: PropertyTrace = PropertyTrace.Unknown
            override val isolate: WriteIsolate get() = isolateStub
            override fun onProblem(problem: PropertyProblem) {
                capturedProblems += problem
            }
        }
    }

    private fun fakeWriteIsolate(): WriteIsolate {
        val failureFactory = object : FailureFactory {
            override fun create(failure: Throwable): Failure = throwingProxy(Failure::class.java)
            override fun create(failure: Throwable, problemLocator: ProblemLocator): Failure = create(failure)
        }
        val ownerStub = object : IsolateOwner {
            override val delegate: Any get() = error("not used")
            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> service(type: Class<T>): T = when (type) {
                FailureFactory::class.java -> failureFactory as T
                else -> error("FakeIsolateOwner: unexpected service request for $type")
            }
        }
        val throwingIsolate = throwingProxy(WriteIsolate::class.java)
        return object : WriteIsolate by throwingIsolate {
            override val owner: IsolateOwner get() = ownerStub
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> throwingProxy(iface: Class<T>): T =
        Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { _, method, _ ->
            error("Throwing proxy for ${iface.simpleName}: unexpected call to ${method.name}")
        } as T

    /**
     * Drives a suspend block to completion in a single thread. Works only when the
     * block doesn't actually suspend (returns synchronously) — true for every code
     * path under test here, since `reportIfUnsupportedPropertyValueType` and its
     * `reportSerializationProblem` callee both complete without awaiting anything.
     */
    private fun <T> runSuspending(block: suspend () -> T): T {
        var captured: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                captured = result
            }
        })
        return captured!!.getOrThrow()
    }
}
