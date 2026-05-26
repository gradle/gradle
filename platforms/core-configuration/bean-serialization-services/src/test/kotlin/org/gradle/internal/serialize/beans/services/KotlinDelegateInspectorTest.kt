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

package org.gradle.internal.serialize.beans.services

import kotlin.properties.Delegates
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinDelegateInspectorTest {
    // region extractValue
    @Test
    fun `extractValue extracts value from initialized lazy`() {
        val delegate = lazy { "computed" }
        delegate.value // force evaluation
        assertEquals("computed", KotlinDelegateInspector.extractValue(delegate))
    }

    @Test
    fun `extractValue returns null for uninitialized lazy`() {
        val delegate = lazy { "not yet" }
        assertNull(KotlinDelegateInspector.extractValue(delegate))
    }

    @Test
    fun `extractValue returns null for initialized lazy holding null`() {
        val delegate = lazy<String?> { null }
        delegate.value // force evaluation
        assertNull(KotlinDelegateInspector.extractValue(delegate))
    }

    @Test
    fun `extractValue extracts value from observable delegate`() {
        val delegate = Delegates.observable("initial") { _, _, _ -> }
        assertEquals("initial", KotlinDelegateInspector.extractValue(delegate))
    }

    @Test
    fun `extractValue extracts value from vetoable delegate`() {
        val delegate = Delegates.vetoable("guarded") { _, _, _ -> true }
        assertEquals("guarded", KotlinDelegateInspector.extractValue(delegate))
    }

    @Test
    fun `extractValue returns null from notNull delegate before assignment`() {
        val delegate = Delegates.notNull<String>()
        assertNull(KotlinDelegateInspector.extractValue(delegate))
    }

    @Test(expected = DelegateInspectionException::class)
    fun `extractValue throws for non-delegate object`() {
        KotlinDelegateInspector.extractValue("not a delegate")
    }

    @Test(expected = DelegateInspectionException::class)
    fun `extractValue throws for ReadOnlyProperty delegate without value field`() {
        val delegate = object : ReadOnlyProperty<Any?, String> {
            override fun getValue(thisRef: Any?, property: KProperty<*>) = "hello"
        }
        KotlinDelegateInspector.extractValue(delegate)
    }
    // endregion extractValue

    // region isKotlinDelegate
    @Test
    fun `isKotlinDelegate returns true for Lazy`() {
        assertTrue(KotlinDelegateInspector.isKotlinDelegate(lazy { }))
    }

    @Test
    fun `isKotlinDelegate returns true for observable`() {
        assertTrue(KotlinDelegateInspector.isKotlinDelegate(Delegates.observable("x") { _, _, _ -> }))
    }

    @Test
    fun `isKotlinDelegate returns true for vetoable`() {
        assertTrue(KotlinDelegateInspector.isKotlinDelegate(Delegates.vetoable("x") { _, _, _ -> true }))
    }

    @Test
    fun `isKotlinDelegate returns false for plain objects`() {
        assertFalse(KotlinDelegateInspector.isKotlinDelegate("not a delegate"))
    }

    @Test
    fun `isKotlinDelegate returns false for null`() {
        assertFalse(KotlinDelegateInspector.isKotlinDelegate(null))
    }

    // endregion isKotlinDelegate

    // region delegateKindName
    @Test
    fun `delegateKindName returns lazy for Lazy`() {
        assertEquals("lazy", KotlinDelegateInspector.delegateKindName(lazy { }))
    }

    @Test
    fun `delegateKindName returns observable-vetoable for observable`() {
        val delegate = Delegates.observable("x") { _, _, _ -> }
        assertEquals("observable/vetoable", KotlinDelegateInspector.delegateKindName(delegate))
    }

    @Test
    fun `delegateKindName returns observable-vetoable for vetoable`() {
        val delegate = Delegates.vetoable("x") { _, _, _ -> true }
        assertEquals("observable/vetoable", KotlinDelegateInspector.delegateKindName(delegate))
    }

    @Test(expected = DelegateInspectionException::class)
    fun `delegateKindName throws for unrecognized types`() {
        KotlinDelegateInspector.delegateKindName("not a delegate")
    }
    // endregion delegateKindName

    // region kotlinPropertyGetterReturnType
    @Test
    fun `kotlinPropertyGetterReturnType returns Configuration class for Configuration-typed property`() {
        val field = BeanWithConfigurationDelegate::class.java.getDeclaredField("conf\$delegate")
        assertEquals(Configuration::class.java, KotlinDelegateInspector.kotlinPropertyGetterReturnType(field))
    }

    @Test
    fun `kotlinPropertyGetterReturnType returns FileCollection class for FileCollection-typed property`() {
        val field = BeanWithFileCollectionDelegate::class.java.getDeclaredField("files\$delegate")
        assertEquals(FileCollection::class.java, KotlinDelegateInspector.kotlinPropertyGetterReturnType(field))
    }

    @Test
    fun `kotlinPropertyGetterReturnType returns String class for String-typed property`() {
        val field = BeanWithStringDelegate::class.java.getDeclaredField("name\$delegate")
        assertEquals(String::class.java, KotlinDelegateInspector.kotlinPropertyGetterReturnType(field))
    }

    @Test(expected = DelegateInspectionException::class)
    fun `kotlinPropertyGetterReturnType throws when field does not follow delegate naming convention`() {
        val field = BeanWithPlainField::class.java.getDeclaredField("notADelegate")
        KotlinDelegateInspector.kotlinPropertyGetterReturnType(field)
    }

    @Test(expected = DelegateInspectionException::class)
    fun `kotlinPropertyGetterReturnType throws when getter cannot be found for delegate field`() {
        // A private val produces a $delegate field but a private getter,
        // so Class.getMethod (public-only) cannot find it.
        val field = BeanWithPrivateDelegate::class.java.getDeclaredField("hidden\$delegate")
        KotlinDelegateInspector.kotlinPropertyGetterReturnType(field)
    }
    // endregion kotlinPropertyGetterReturnType

    // region test fixtures
    @Suppress("unused")
    private class BeanWithConfigurationDelegate {
        val conf by lazy<Configuration> {
            throw UnsupportedOperationException("not called in test")
        }
    }

    @Suppress("unused")
    private class BeanWithFileCollectionDelegate {
        val files by lazy<FileCollection> {
            throw UnsupportedOperationException("not called in test")
        }
    }

    @Suppress("unused")
    private class BeanWithStringDelegate {
        val name by lazy { "hello" }
    }

    @Suppress("unused")
    private class BeanWithPlainField {
        @JvmField
        val notADelegate: String = "plain"
    }

    @Suppress("unused")
    private class BeanWithPrivateDelegate {
        private val hidden by lazy { "invisible getter" }
    }
    // endregion test fixtures
}
