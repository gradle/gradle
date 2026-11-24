package org.gradle.kotlin.dsl

import groovy.lang.Closure
import groovy.lang.GroovyObject
import groovy.lang.MetaBeanProperty
import groovy.lang.MetaClass
import org.gradle.kotlin.dsl.support.uncheckedCast
import org.gradle.util.internal.ConfigureUtil
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.MockMakers
import org.mockito.Mockito
import org.mockito.kotlin.KStubbing
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import java.util.Locale


class GroovyInteroperabilityTest {

    @Test
    fun `can use closure with single argument call`() {
        val list = arrayListOf<Int>()
        closureOf<MutableList<Int>> { add(42) }.call(list)
        assertEquals(42, list.first())
    }

    @Test
    fun `can use closure with single nullable argument call`() {
        var passedIntoClosure: Any? = "Something non null"
        closureOf<Any?> { passedIntoClosure = this }.call(null)
        assertNull(passedIntoClosure)
    }

    @Test
    fun `can use closure with delegate call`() {
        val list = arrayListOf<Int>()
        delegateClosureOf<MutableList<Int>> { add(42) }.apply {
            delegate = list
            call()
        }
        assertEquals(42, list.first())
    }

    @Test
    fun `can use closure with a null delegate call`() {
        var passedIntoClosure: Any? = "Something non null"
        delegateClosureOf<Any?> { passedIntoClosure = this }.apply {
            delegate = null
            call()
        }
        assertNull(passedIntoClosure)
    }

    @Test
    fun `can adapt parameterless function using KotlinClosure0`() {

        fun closure(function: () -> String) = KotlinClosure0(function)

        assertEquals(
            "GROOVY",
            closure { "GROOVY" }.call()
        )
    }

    @Test
    fun `can adapt parameterless null returning function using KotlinClosure0`() {
        fun closure(function: () -> String?) = KotlinClosure0(function)

        assertEquals(
            null,
            closure { null }.call()
        )
    }

    @Test
    fun `can adapt unary function using KotlinClosure1`() {

        fun closure(function: String.() -> String) = KotlinClosure1(function)

        assertEquals(
            "GROOVY",
            closure { uppercase(Locale.US) }.call("groovy")
        )
    }

    @Test
    fun `can adapt unary null receiving function using KotlinClosure1`() {
        fun closure(function: String?.() -> String?) = KotlinClosure1(function)

        assertEquals(
            null,
            closure { null }.call(null)
        )
    }

    @Test
    fun `can adapt binary function using KotlinClosure2`() {

        fun closure(function: (String, String) -> String) = KotlinClosure2(function)

        assertEquals(
            "foobar",
            closure { x, y -> x + y }.call("foo", "bar")
        )
    }

    @Test
    fun `can adapt binary null receiving function using KotlinClosure2`() {

        fun closure(function: (String?, String?) -> String?) = KotlinClosure2(function)

        assertEquals(
            null,
            closure { _, _ -> null }.call(null, null)
        )
    }

    @Test
    fun `can adapt ternary function using KotlinClosure3`() {

        fun closure(function: (String, String, String) -> String) = KotlinClosure3(function)

        assertEquals(
            "foobarbaz",
            closure { x, y, z -> x + y + z }.call("foo", "bar", "baz")
        )
    }

    @Test
    fun `can adapt ternary null receiving function using KotlinClosure3`() {

        fun closure(function: (String?, String?, String?) -> String?) = KotlinClosure3(function)

        assertEquals(
            null,
            closure { _, _, _ -> null }.call(null, null, null)
        )
    }

    @Test
    fun `can invoke Closure`() {

        val invocations = mutableListOf<String>()

        val c0 =
            object : Closure<Boolean>(null, null) {
                @Suppress("unused")
                fun doCall() = invocations.add("c0")
            }

        val c1 =
            object : Closure<Boolean>(null, null) {
                @Suppress("unused")
                fun doCall(x: Any) = invocations.add("c1($x)")
            }

        val c2 =
            object : Closure<Boolean>(null, null) {
                @Suppress("unused")
                fun doCall(x: Any, y: Any) = invocations.add("c2($x, $y)")
            }

        val c3 =
            object : Closure<Boolean>(null, null) {
                @Suppress("unused")
                fun doCall(x: Any, y: Any, z: Any) = invocations.add("c3($x, $y, $z)")
            }

        assert(c0())
        assert(c1(42))
        assert(c2(11, 33))
        assert(c3(23, 7, 12))

        assertThat(
            invocations,
            equalTo(listOf("c0", "c1(42)", "c2(11, 33)", "c3(23, 7, 12)"))
        )
    }

    @Test
    fun `#withGroovyBuilder can dispatch keyword arguments against GroovyObject`() {

        val expectedInvokeResult = Any()
        val delegate = groovyObjectMock {
            on { invokeMethod(any(), any()) } doReturn expectedInvokeResult
        }

        val expectedBuilderResult = Any()
        val builderResult = delegate.withGroovyBuilder {
            val invokeResult = "withKeywordArguments"("string" to "42", "int" to 42)
            assertThat(invokeResult, sameInstance(expectedInvokeResult))
            assertThat(this.delegate, sameInstance(delegate))
            expectedBuilderResult
        }
        assertThat(builderResult, sameInstance(expectedBuilderResult))

        val expectedKeywordArguments = mapOf("string" to "42", "int" to 42)
        verify(delegate).invokeMethod("withKeywordArguments", arrayOf(expectedKeywordArguments))
    }

    @Test
    fun `#withGroovyBuilder allow nested invocations against GroovyObject`() {

        val expectedNestedInvokeResult = Any()
        val nestedDelegate = groovyObjectMock {
            on { invokeMethod(any(), any()) } doReturn expectedNestedInvokeResult
        }

        val expectedInvokeResult = Any()
        val delegate = groovyObjectMock {
            on { invokeMethod(eq("nest"), any()) }.thenAnswer {
                val varargs = uncheckedCast<Array<Any?>>(it.getArgument(1))
                val closure = uncheckedCast<Closure<Any>?>(varargs[0])
                ConfigureUtil
                    .configureUsing<Any>(closure)
                    .execute(nestedDelegate)
                expectedInvokeResult
            }
        }

        val expectedBuilderResult = Any()
        val builderResult = delegate.withGroovyBuilder {
            val invokeResult = "nest" {
                assertThat(this.delegate, sameInstance(nestedDelegate))
                val nestedInvokeResult = "nestedInvocation"()
                assertThat(nestedInvokeResult, sameInstance(expectedNestedInvokeResult))
            }
            assertThat(invokeResult, sameInstance(expectedInvokeResult))
            assertThat(this.delegate, sameInstance(delegate))
            expectedBuilderResult
        }
        assertThat(builderResult, sameInstance(expectedBuilderResult))

        verify(delegate).invokeMethod(eq("nest"), any())
        verify(nestedDelegate).invokeMethod("nestedInvocation", emptyArray<Any>())
    }

    interface NonGroovyObject {
        @Suppress("unused")
        val existingProperty: String
        fun withKeywordArguments(args: Map<String, Any?>): Any?
    }

    @Test
    fun `#withGroovyBuilder can dispatch keyword arguments against non GroovyObject`() {

        val expectedInvokeResult = Any()
        val delegate = mock<NonGroovyObject> {
            on { withKeywordArguments(any()) } doReturn expectedInvokeResult
        }

        val expectedBuilderResult = Any()
        val builderResult = delegate.withGroovyBuilder {
            val invokeResult = "withKeywordArguments"("string" to "42", "int" to 42)
            assertThat(invokeResult, sameInstance(expectedInvokeResult))
            assertThat(this.delegate, sameInstance(delegate))
            expectedBuilderResult
        }
        assertThat(builderResult, sameInstance(expectedBuilderResult))

        val expectedKeywordArguments = mapOf("string" to "42", "int" to 42)
        verify(delegate).withKeywordArguments(expectedKeywordArguments)
    }

    @Test
    fun `#withGroovyBuilder can query property existence against GroovyObject`() {

        val existingPropertyName = "existingProperty"
        val absentPropertyName = "absentProperty"

        val metaClass = mock<MetaClass> {
            on { hasProperty(any(), any()) } doAnswer {
                if (it.arguments[1] == existingPropertyName) mock<MetaBeanProperty>()
                else null
            }
        }
        val delegate = mock<GroovyObject> {
            on { getMetaClass() } doReturn metaClass
        }

        assertTrue(
            delegate.withGroovyBuilder { hasProperty(existingPropertyName) }
        )

        assertFalse(
            delegate.withGroovyBuilder { hasProperty(absentPropertyName) }
        )

        inOrder(delegate, metaClass) {
            verify(delegate).metaClass
            verify(metaClass).hasProperty(delegate, existingPropertyName)
            verify(delegate).metaClass
            verify(metaClass).hasProperty(delegate, absentPropertyName)
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `#withGroovyBuilder can query property existence against non GroovyObject`() {

        val delegate = mock<NonGroovyObject>()

        assertTrue(
            delegate.withGroovyBuilder { hasProperty("existingProperty") }
        )

        assertFalse(
            delegate.withGroovyBuilder { hasProperty("absentProperty") }
        )
    }

    /**
     * Sets up a [GroovyObject] mock that can intercept and verify calls to [GroovyObject.invokeMethod]
     * by using a [proxy mock][MockMakers.PROXY].
     *
     * Regular mocks (i.e. those produced by `mock<GroovyObject>()`) don't intercept calls to
     * default interface methods.
     */
    private
    fun groovyObjectMock(stubbing: KStubbing<GroovyObject>.(GroovyObject) -> Unit): GroovyObject =
        Mockito.mock<GroovyObject>(
            // Must use MockMackers.PROXY to properly intercept default interface methods in GroovyObject
            Mockito.withSettings().mockMaker(MockMakers.PROXY)
        ).stub(stubbing)
}
