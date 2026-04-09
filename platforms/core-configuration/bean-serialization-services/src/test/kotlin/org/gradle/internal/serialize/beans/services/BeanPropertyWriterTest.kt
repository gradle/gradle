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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


/**
 * Tests for the Kotlin delegate inspection methods on [BeanPropertyWriter].
 *
 * These are unit tests for the pure-logic helpers; end-to-end integration tests
 * for the full serialization path live in `ConfigurationCacheUnsupportedTypesIntegrationTest`.
 */
class BeanPropertyWriterTest {
    // region isKotlinPropertyTypeUnsupported
    @Test
    fun `returns true when getter returns Configuration`() {
        val field = BeanWithConfigurationDelegate::class.java.getDeclaredField("conf\$delegate")
        val writer = BeanPropertyWriter(BeanWithConfigurationDelegate::class.java)
        assertTrue(writer.isKotlinPropertyTypeUnsupported(field))
    }

    @Test
    fun `returns false when getter returns FileCollection`() {
        val field = BeanWithFileCollectionDelegate::class.java.getDeclaredField("files\$delegate")
        val writer = BeanPropertyWriter(BeanWithFileCollectionDelegate::class.java)
        assertFalse(writer.isKotlinPropertyTypeUnsupported(field))
    }

    @Test
    fun `returns false when getter returns String`() {
        val field = BeanWithStringDelegate::class.java.getDeclaredField("name\$delegate")
        val writer = BeanPropertyWriter(BeanWithStringDelegate::class.java)
        assertFalse(writer.isKotlinPropertyTypeUnsupported(field))
    }

    @Test(expected = DelegateInspectionException::class)
    fun `throws when field does not follow delegate naming convention`() {
        val field = BeanWithPlainField::class.java.getDeclaredField("notADelegate")
        val writer = BeanPropertyWriter(BeanWithPlainField::class.java)
        writer.isKotlinPropertyTypeUnsupported(field)
    }

    @Test(expected = DelegateInspectionException::class)
    fun `throws when getter cannot be found for delegate field`() {
        // A private val produces a $delegate field but a private getter,
        // so Class.getMethod (public-only) cannot find it.
        val field = BeanWithPrivateDelegate::class.java.getDeclaredField("hidden\$delegate")
        val writer = BeanPropertyWriter(BeanWithPrivateDelegate::class.java)
        writer.isKotlinPropertyTypeUnsupported(field)
    }
    // endregion isKotlinPropertyTypeUnsupported

    // region test fixtures
    @Suppress("unused")
    private class BeanWithConfigurationDelegate {
        val conf by lazy<org.gradle.api.artifacts.Configuration> {
            throw UnsupportedOperationException("not called in test")
        }
    }

    @Suppress("unused")
    private class BeanWithFileCollectionDelegate {
        val files by lazy<org.gradle.api.file.FileCollection> {
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
