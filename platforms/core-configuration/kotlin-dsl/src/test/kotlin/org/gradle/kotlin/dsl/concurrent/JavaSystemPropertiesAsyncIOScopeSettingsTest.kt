/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.kotlin.dsl.concurrent

import org.gradle.kotlin.dsl.concurrent.JavaSystemPropertiesAsyncIOScopeSettings.Companion.DEFAULT_IO_ACTION_TIMEOUT
import org.gradle.kotlin.dsl.concurrent.JavaSystemPropertiesAsyncIOScopeSettings.Companion.IO_ACTION_TIMEOUT_SYSTEM_PROPERTY
import org.gradle.util.SetSystemProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test


class JavaSystemPropertiesAsyncIOScopeSettingsTest {

    @Rule
    @JvmField
    val setSystemProperties = SetSystemProperties()

    @Test
    fun `use default timeout when property is not set`() {
        val settings = JavaSystemPropertiesAsyncIOScopeSettings()
        assertEquals(DEFAULT_IO_ACTION_TIMEOUT, settings.ioActionTimeoutMs)
    }

    @Test
    fun `use default timeout when property is an empty string`() {
        System.setProperty(IO_ACTION_TIMEOUT_SYSTEM_PROPERTY, "")
        val settings = JavaSystemPropertiesAsyncIOScopeSettings()
        assertEquals(DEFAULT_IO_ACTION_TIMEOUT, settings.ioActionTimeoutMs)
    }

    @Test
    fun `use default timeout when property is a blank string`() {
        System.setProperty(IO_ACTION_TIMEOUT_SYSTEM_PROPERTY, " ")
        val settings = JavaSystemPropertiesAsyncIOScopeSettings()
        assertEquals(DEFAULT_IO_ACTION_TIMEOUT, settings.ioActionTimeoutMs)
    }

    @Test
    fun `use custom timeout when property is set`() {
        System.setProperty(IO_ACTION_TIMEOUT_SYSTEM_PROPERTY, "111")
        val settings = JavaSystemPropertiesAsyncIOScopeSettings()
        assertEquals(111L, settings.ioActionTimeoutMs)
    }

    @Test
    fun `throws when property is not a valid number`() {
        System.setProperty(IO_ACTION_TIMEOUT_SYSTEM_PROPERTY, "abc")
        val settings = JavaSystemPropertiesAsyncIOScopeSettings()
        assertThrowsFor("abc") {
            settings.ioActionTimeoutMs
        }
    }

    @Test
    fun `throws when property is zero`() {
        System.setProperty(IO_ACTION_TIMEOUT_SYSTEM_PROPERTY, "0")
        val settings = JavaSystemPropertiesAsyncIOScopeSettings()
        assertThrowsFor("0") {
            settings.ioActionTimeoutMs
        }
    }

    @Test
    fun `throws when property is a negative number`() {
        System.setProperty(IO_ACTION_TIMEOUT_SYSTEM_PROPERTY, "-1")
        val settings = JavaSystemPropertiesAsyncIOScopeSettings()
        assertThrowsFor("-1") {
            settings.ioActionTimeoutMs
        }
    }

    private
    fun assertThrowsFor(propertyValue: String, block: () -> Unit) {
        try {
            block()
            fail("Expected to fail but succeeded")
        } catch (ex: Exception) {
            assertEquals(
                "Invalid value for system property '$IO_ACTION_TIMEOUT_SYSTEM_PROPERTY': '$propertyValue'. It must be a positive number of milliseconds.",
                ex.message
            )
        }
    }
}
