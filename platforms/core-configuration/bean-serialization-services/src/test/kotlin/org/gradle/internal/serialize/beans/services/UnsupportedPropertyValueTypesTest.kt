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

import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [UnsupportedPropertyValueTypes] — verifies that the unsupported type list
 * is correctly checked, including subtype detection, property-kind-specific resolutions,
 * and the [UnsupportedPropertyValueTypes.CheckResult] sealed interface.
 */
class UnsupportedPropertyValueTypesTest {
    @Test
    fun `check returns Supported for String`() {
        assertTrue(
            UnsupportedPropertyValueTypes.check(String::class.java, Property::class.java)
                is UnsupportedPropertyValueTypes.CheckResult.Supported
        )
    }

    @Test
    fun `check returns Unsupported for Configuration`() {
        val result = UnsupportedPropertyValueTypes.check(Configuration::class.java, Property::class.java)
        assertTrue(result is UnsupportedPropertyValueTypes.CheckResult.Unsupported)
        assertEquals(
            "Use a @InputFiles ConfigurableFileCollection instead.",
            (result as UnsupportedPropertyValueTypes.CheckResult.Unsupported).resolution
        )
    }

    @Test
    fun `check returns Unsupported for SourceDirectorySet`() {
        val result = UnsupportedPropertyValueTypes.check(SourceDirectorySet::class.java, Property::class.java)
        assertTrue(result is UnsupportedPropertyValueTypes.CheckResult.Unsupported)
        assertEquals(
            "Use a @InputFiles ConfigurableFileCollection instead.",
            (result as UnsupportedPropertyValueTypes.CheckResult.Unsupported).resolution
        )
    }

    @Test
    fun `subtypes of unsupported types are also unsupported`() {
        val result = UnsupportedPropertyValueTypes.check(TestConfigurationSubtype::class.java, Property::class.java)
        assertTrue(result is UnsupportedPropertyValueTypes.CheckResult.Unsupported)
    }

    @Test
    fun `MapProperty kind gets restructuring advice`() {
        val result = UnsupportedPropertyValueTypes.check(Configuration::class.java, MapProperty::class.java)
        assertTrue(result is UnsupportedPropertyValueTypes.CheckResult.Unsupported)
        assertEquals(
            "Avoid using Configuration as a MapProperty key or value.",
            (result as UnsupportedPropertyValueTypes.CheckResult.Unsupported).resolution
        )
    }

    @Test
    fun `ListProperty kind gets standard advice`() {
        val result = UnsupportedPropertyValueTypes.check(Configuration::class.java, ListProperty::class.java)
        assertTrue(result is UnsupportedPropertyValueTypes.CheckResult.Unsupported)
        assertEquals(
            "Use a @InputFiles ConfigurableFileCollection instead.",
            (result as UnsupportedPropertyValueTypes.CheckResult.Unsupported).resolution
        )
    }

    @Test
    fun `SetProperty kind gets standard advice`() {
        val result = UnsupportedPropertyValueTypes.check(Configuration::class.java, SetProperty::class.java)
        assertTrue(result is UnsupportedPropertyValueTypes.CheckResult.Unsupported)
        assertEquals(
            "Use a @InputFiles ConfigurableFileCollection instead.",
            (result as UnsupportedPropertyValueTypes.CheckResult.Unsupported).resolution
        )
    }

    private interface TestConfigurationSubtype : Configuration
}
