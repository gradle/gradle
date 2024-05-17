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

package org.gradle.kotlin.dsl.normalization

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.support.KotlinCompilerOptions
import org.gradle.kotlin.dsl.support.compileToDirectory
import org.gradle.kotlin.dsl.support.loggerFor
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.objectweb.asm.ClassReader
import java.net.URLClassLoader


class KotlinApiClassExtractorTest : TestWithTempFiles() {

    @Test
    fun `API class is unaffected by changes to public method bodies`() {
        givenChangingClass(
            "Foo",
            """
                fun foo(): String {
                    return "foo"
                }
            """,
            { assertThat(it.instantiateAndCall("foo"), equalTo("foo")) },
            """
                fun foo(): String {
                    return "bar"
                }
            """,
            { assertThat(it.instantiateAndCall("foo"), equalTo("bar")) }
        ).assertSameApi()
    }

    // test throws until we can detect lambdas in inline functions and treat them as ABI
    @Test(expected = CompileAvoidanceException::class)
    fun `changes to inline method bodies change generated API class`() {
        givenChangingClass(
            "Foo",
            """
                inline fun foo(): String {
                    return "foo"
                }
            """,
            { assertThat(it.instantiateAndCall("foo"), equalTo("foo")) },
            """
                inline fun foo(): String {
                    return "bar"
                }
            """,
            { assertThat(it.instantiateAndCall("foo"), equalTo("bar")) }
        ).assertApiChanged()
    }

    // test throws until we can detect lambdas in inline functions and treat them as ABI
    @Test(expected = CompileAvoidanceException::class)
    fun `changes to standalone inline method bodies change generated API class`() {
        givenChangingScript(
            "Foo",
            """
                inline fun foo(): String {
                    return "foo"
                }
            """,
            { assertThat(it.callStatic("foo"), equalTo("foo")) },
            """
                inline fun foo(): String {
                    return "bar"
                }
            """,
            { assertThat(it.callStatic("foo"), equalTo("bar")) }
        ).assertApiChanged()
    }

    @Test
    fun `API class is unaffected by changes to standalone method bodies`() {
        givenChangingScript(
            "Foo",
            """
                fun foo(): String {
                    return "foo"
                }
            """,
            { assertThat(it.callStatic("foo"), equalTo("foo")) },
            """
                fun foo(): String {
                    return "bar"
                }
            """,
            { assertThat(it.callStatic("foo"), equalTo("bar")) }
        ).assertSameApi()
    }

    @Test
    fun `API class is unaffected by addition of private method`() {
        givenChangingClass(
            "Foo",
            """
                fun foo(): String {
                    return "foo"
                }
            """,
            { assertThat(it.instantiateAndCall("foo"), equalTo("foo")) },
            """
                fun foo(): String {
                    return bar()
                }
                private fun bar(): String {
                    return "bar"
                }
            """,
            { assertThat(it.instantiateAndCall("foo"), equalTo("bar")) }
        ).assertSameApi()
    }

    @Test
    fun `API class is unaffected by addition of internal method`() {
        givenChangingClass(
            "Foo",
            """
                fun foo(): String {
                    return "foo"
                }
            """,
            { assertThat(it.instantiateAndCall("foo"), equalTo("foo")) },
            """
                fun foo(): String {
                    return bar()
                }
                internal fun bar(): String {
                    return "bar"
                }
            """,
            { assertThat(it.instantiateAndCall("foo"), equalTo("bar")) }
        ).assertSameApi()
    }

    @Test
    fun `API class is unaffected by changes to internal inline method`() {
        givenChangingClass(
            "Foo",
            """
                internal inline fun foo(): String {
                    return "foo"
                }

                fun bar(): String = foo()
            """,
            { assertThat(it.instantiateAndCall("bar"), equalTo("foo")) },
            """
                internal inline fun foo(): String {
                    return "bar"
                }

                fun bar(): String = foo()
            """,
            { assertThat(it.instantiateAndCall("bar"), equalTo("bar")) }
        ).assertSameApi()
    }

    @Test
    fun `API class is unaffected by changes to private inline method`() {
        givenChangingClass(
            "Foo",
            """
                private inline fun foo(): String {
                    return "foo"
                }

                fun bar(): String = foo()
            """,
            { assertThat(it.instantiateAndCall("bar"), equalTo("foo")) },
            """
                private inline fun foo(): String {
                    return "bar"
                }

                fun bar(): String = foo()
            """,
            { assertThat(it.instantiateAndCall("bar"), equalTo("bar")) }
        ).assertSameApi()
    }

    @Test
    fun `changes to standalone const val field change generated API class`() {
        givenChangingScript(
            "Foo",
            """
                const val FOO = "foo"
                fun foo() = FOO
            """,
            { assertThat(it.callStatic("foo"), equalTo("foo")) },
            """
                const val FOO = "bar"
                fun foo() = FOO
            """,
            { assertThat(it.callStatic("foo"), equalTo("bar")) }
        ).assertApiChanged()
    }

    @Test
    fun `changes to const val field in object change generated API class`() {
        givenChangingObject(
            "Foo",
            """
                const val FOO = "foo"
                fun foo() = FOO
            """,
            { assertThat(it.callOnObjectIntance("foo"), equalTo("foo")) },
            """
                const val FOO = "bar"
                fun foo() = FOO
            """,
            { assertThat(it.callOnObjectIntance("foo"), equalTo("bar")) }
        ).assertApiChanged()
    }

    private
    fun givenChangingClass(
        className: String,
        initialBody: String,
        initialAssertion: (ClassFixture) -> Unit,
        changedBody: String,
        changedAssertion: (ClassFixture) -> Unit
    ): ClassChangeFixture {
        val initialClass = compileClass(className, initialBody)
        initialAssertion(initialClass)
        val changedClass = compileClass(className, changedBody)
        changedAssertion(changedClass)
        return ClassChangeFixture(initialClass, changedClass)
    }

    private
    fun givenChangingObject(
        className: String,
        initialBody: String,
        initialAssertion: (ClassFixture) -> Unit,
        changedBody: String,
        changedAssertion: (ClassFixture) -> Unit
    ): ClassChangeFixture {
        val initialClass = compileObject(className, initialBody)
        initialAssertion(initialClass)
        val changedClass = compileObject(className, changedBody)
        changedAssertion(changedClass)
        return ClassChangeFixture(initialClass, changedClass)
    }

    private
    fun givenChangingScript(
        scriptName: String,
        initialBody: String,
        initialAssertion: (ClassFixture) -> Unit,
        changedBody: String,
        changedAssertion: (ClassFixture) -> Unit
    ): ClassChangeFixture {
        val scriptClass = "${scriptName}Kt.class"
        val initialClass = compileScript(scriptName, initialBody, scriptClass)
        initialAssertion(initialClass)
        val changedClass = compileScript(scriptName, changedBody, scriptClass)
        changedAssertion(changedClass)
        return ClassChangeFixture(initialClass, changedClass)
    }

    private
    fun compileClass(className: String, classBody: String): ClassFixture {
        return compileScript(
            className,
            """
            class $className {
            $classBody
            }
            """,
            "$className.class"
        )
    }

    private
    fun compileObject(objectName: String, classBody: String): ClassFixture {
        return compileScript(
            objectName,
            """
            object $objectName {
            $classBody
            }
            """,
            "$objectName.class"
        )
    }

    private
    fun compileScript(scriptName: String, scriptBody: String, scriptClass: String): ClassFixture {
        val sourceFile = newFile("$scriptName.kt", scriptBody)

        val binDir = newFolder("bin")
        compileToDirectory(
            binDir,
            KotlinCompilerOptions(),
            "test",
            listOf(sourceFile),
            loggerFor<KotlinApiClassExtractorTest>(),
            emptyList()
        )

        return ClassFixture(scriptBody, binDir.toPath().resolve(scriptClass).toFile().readBytes())
    }
}


private
class ClassChangeFixture(val initialClass: ClassFixture, val changedClass: ClassFixture) {
    private
    val apiClassExtractor = KotlinApiClassExtractor()

    val initialApiClassBytes = extractApiBytes(initialClass.bytes)
    val changedApiClassBytes = extractApiBytes(changedClass.bytes)

    fun assertInitialClass(assertion: (ClassFixture) -> Unit) {
        assertion(initialClass)
    }

    fun assertChangedClass(assertion: (ClassFixture) -> Unit) {
        assertion(changedClass)
    }

    fun assertSameApi() {
        assertThat(
            "expected:\n${initialClass.sourceContent}\n to the same API as:\n${changedClass.sourceContent}",
            initialApiClassBytes,
            equalTo(changedApiClassBytes)
        )
    }

    fun assertApiChanged() {
        assertThat(
            "expected\n${initialClass.sourceContent}\nto have different API from:\n${changedClass.sourceContent}",
            initialApiClassBytes,
            not(equalTo(changedApiClassBytes))
        )
    }

    private
    fun extractApiBytes(classBytes: ByteArray) =
        apiClassExtractor.extractApiClassFrom(ClassReader(classBytes)).get()
}


private
class ClassFixture(val sourceContent: String, val bytes: ByteArray) {
    fun loadClass() =
        BytesClassLoader().loadClassFromBytes(bytes)

    fun instantiateAndCall(methodName: String): Any {
        val loadedClass = loadClass()
        val method = loadedClass.getMethod(methodName)
        val instance = loadedClass.getDeclaredConstructor().newInstance()
        return method.invoke(instance)
    }

    fun callStatic(methodName: String): Any {
        val loadedClass = loadClass()
        val method = loadedClass.getMethod(methodName)
        return method.invoke(loadedClass)
    }

    fun callOnObjectIntance(methodName: String): Any {
        val loadedClass = loadClass()
        val method = loadedClass.getMethod(methodName)
        val instance = loadedClass.getField("INSTANCE")
        return method.invoke(instance.get(loadedClass))
    }
}


private
class BytesClassLoader : URLClassLoader(arrayOf(), getSystemClassLoader()) {
    fun loadClassFromBytes(bytes: ByteArray): Class<*> =
        defineClass(null, bytes, 0, bytes.size)
}
