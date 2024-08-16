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

package org.gradle.internal.cc.base.services

import org.gradle.internal.code.DefaultUserCodeApplicationContext
import org.gradle.internal.configuration.problems.DefaultProblemFactory
import org.gradle.internal.problems.NoOpProblemDiagnosticsFactory
import org.gradle.util.SetSystemProperties
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test


class ConfigurationCacheEnvironmentChangeTrackerTest {
    @Rule
    @JvmField
    val systemProperties = SetSystemProperties()

    private
    val tracker = ConfigurationCacheEnvironmentChangeTracker(DefaultProblemFactory(DefaultUserCodeApplicationContext(), NoOpProblemDiagnosticsFactory()))

    @Test
    fun `property set in tracking scope is considered changed`() {
        tracker.withTrackingSystemPropertyChanges {
            System.setProperty("some.property", "some.value")
        }

        assertTrue(tracker.isSystemPropertyMutated("some.property"))
        assertThat(System.getProperty("some.property"), equalTo("some.value"))
    }

    @Test
    fun `property changed in tracking scope is considered changed`() {
        System.setProperty("some.property", "some.value")
        tracker.withTrackingSystemPropertyChanges {
            System.setProperty("some.property", "other.value")
        }

        assertTrue(tracker.isSystemPropertyMutated("some.property"))
        assertThat(System.getProperty("some.property"), equalTo("other.value"))
    }

    @Test
    fun `property removed in tracking scope is considered removed`() {
        System.setProperty("some.property", "some.value")
        tracker.withTrackingSystemPropertyChanges {
            System.getProperties().remove("some.property")
            Unit
        }

        assertTrue(isSystemPropertyRemoved("some.property"))
        assertThat(System.getProperty("some.property"), nullValue())
    }

    @Test
    fun `tracking scope returns the value`() {
        val result = tracker.withTrackingSystemPropertyChanges {
            1
        }

        assertThat(result, equalTo(1))
    }

    @Test
    fun `property changes are tracked if an exception is thrown in the scope`() {
        try {
            tracker.withTrackingSystemPropertyChanges<String> {
                System.setProperty("some.property", "some.value")
                throw RuntimeException("expected")
            }
        } catch (ignored: Exception) {

        }

        assertTrue(tracker.isSystemPropertyMutated("some.property"))
        assertThat(System.getProperty("some.property"), equalTo("some.value"))
    }

    @Test
    fun `can use tracking scope after restoring`() {
        tracker.loadFrom(emptyEnvironmentState())

        tracker.withTrackingSystemPropertyChanges {
            System.setProperty("some.property", "some.value")
        }

        assertThat(System.getProperty("some.property"), equalTo("some.value"))
    }

    @Test
    fun `can load state after capturing`() {
        tracker.systemPropertyRemoved("some.property")
        tracker.getCachedState()

        tracker.loadFrom(emptyEnvironmentState())
    }

    @Test(expected = IllegalStateException::class)
    fun `updating state after loading throws`() {
        tracker.loadFrom(emptyEnvironmentState())

        tracker.systemPropertyRemoved("some.property")
    }

    @Test(expected = IllegalStateException::class)
    fun `loading twice throws`() {
        tracker.loadFrom(emptyEnvironmentState())
        tracker.loadFrom(emptyEnvironmentState())
    }

    private
    fun emptyEnvironmentState() = ConfigurationCacheEnvironmentChangeTracker.CachedEnvironmentState(cleared = false, updates = listOf(), removals = listOf())

    private
    fun isSystemPropertyRemoved(key: String): Boolean {
        return tracker.getCachedState().removals.find { it.key == key } != null
    }
}
