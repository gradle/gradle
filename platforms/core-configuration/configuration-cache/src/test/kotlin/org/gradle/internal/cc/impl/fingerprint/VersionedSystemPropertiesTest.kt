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

package org.gradle.internal.cc.impl.fingerprint

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.util.Properties


class VersionedSystemPropertiesTest {

    @Test
    fun `version 0 is the initial environment`() {
        val properties = versionedProperties("existing" to "value")

        assertThat(properties.valueAt(0, "existing"), equalTo<Any?>("value"))
        assertThat(properties.valueAt(0, "absent"), nullValue())
    }

    @Test
    fun `a change is only visible from the version it produced`() {
        val properties = versionedProperties("prop" to "original")

        properties.setProperty(1, "prop", "changed")

        assertThat(properties.valueAt(0, "prop"), equalTo<Any?>("original"))
        assertThat(properties.valueAt(1, "prop"), equalTo<Any?>("changed"))
    }

    @Test
    fun `a version without a change of its own sees the closest preceding one`() {
        val properties = versionedProperties()

        properties.setProperty(1, "prop", "one")
        properties.setProperty(2, "other", "value")
        properties.setProperty(3, "prop", "three")

        assertThat(properties.valueAt(2, "prop"), equalTo<Any?>("one"))
        assertThat(properties.valueAt(4, "prop"), equalTo<Any?>("three"))
    }

    @Test
    fun `removing a property hides it from that version on`() {
        val properties = versionedProperties("prop" to "original")

        properties.removeProperty(1, "prop")

        assertThat(properties.valueAt(0, "prop"), equalTo<Any?>("original"))
        assertThat(properties.valueAt(1, "prop"), nullValue())
    }

    @Test
    fun `setting a property to null removes it, as the properties table cannot hold nulls`() {
        val properties = versionedProperties("prop" to "original")

        properties.setProperty(1, "prop", null)

        assertThat(properties.valueAt(1, "prop"), nullValue())
    }

    @Test
    fun `clearing hides every property, including ones set earlier`() {
        val properties = versionedProperties("fromEnvironment" to "value")

        properties.setProperty(1, "fromBuildLogic", "value")
        properties.clearProperties(2)

        assertThat(properties.valueAt(1, "fromEnvironment"), equalTo<Any?>("value"))
        assertThat(properties.valueAt(1, "fromBuildLogic"), equalTo<Any?>("value"))

        assertThat(properties.valueAt(2, "fromEnvironment"), nullValue())
        assertThat(properties.valueAt(2, "fromBuildLogic"), nullValue())
    }

    @Test
    fun `a property installed after clearing is visible again`() {
        val properties = versionedProperties()

        properties.clearProperties(1)
        properties.installProperties(2, mapOf("prop" to "installed"))

        assertThat(properties.valueAt(1, "prop"), nullValue())
        assertThat(properties.valueAt(2, "prop"), equalTo<Any?>("installed"))
    }

    @Test
    fun `the most recent change wins, whether it was made by build logic or by loading Gradle properties`() {
        val properties = versionedProperties()

        // Build logic changes a property that loading Gradle properties later declares.
        properties.setProperty(1, "prop", "fromBuildLogic")
        properties.installProperties(2, mapOf("prop" to "fromGradleProperties"))

        assertThat(properties.valueAt(2, "prop"), equalTo<Any?>("fromGradleProperties"))

        // And the other way around.
        properties.setProperty(3, "prop", "fromBuildLogicAgain")

        assertThat(properties.valueAt(3, "prop"), equalTo<Any?>("fromBuildLogicAgain"))
    }

    // Loading Gradle properties adds to the properties as of the previous version rather than replacing
    // them with whatever the process holds. That distinction only shows up for keys the load says nothing
    // about: checking an entry never applies the recorded changes to the process, so the properties of the
    // process are missing every one of them.

    @Test
    fun `loading Gradle properties keeps a property changed before it`() {
        val properties = versionedProperties("prop" to "fromEnvironment")

        properties.setProperty(1, "prop", "fromBuildLogic")
        properties.installProperties(2, mapOf("unrelated" to "value"))

        assertThat(properties.valueAt(2, "prop"), equalTo<Any?>("fromBuildLogic"))
        assertThat(properties.valueAt(2, "unrelated"), equalTo<Any?>("value"))
    }

    @Test
    fun `loading Gradle properties keeps a property removed before it removed`() {
        val properties = versionedProperties("prop" to "fromEnvironment")

        properties.removeProperty(1, "prop")
        properties.installProperties(2, mapOf("unrelated" to "value"))

        assertThat(properties.valueAt(2, "prop"), nullValue())
    }

    @Test
    fun `a change is not applied until every change before it is known`() {
        val properties = versionedProperties("prop" to "original")

        // The change producing version 2 is read before the one producing version 1, which can happen when
        // projects are configured in parallel.
        properties.setProperty(2, "prop", "two")

        // Version 2 cannot be answered yet: version 1 might change the same property.
        assertThat(properties.isReadyFor(2), equalTo(false))
        assertThat(properties.isReadyFor(0), equalTo(true))

        properties.setProperty(1, "other", "one")

        assertThat(properties.isReadyFor(2), equalTo(true))
        assertThat(properties.valueAt(2, "prop"), equalTo<Any?>("two"))
        assertThat(properties.valueAt(2, "other"), equalTo<Any?>("one"))
    }

    @Test
    fun `changes left pending are settled in version order once ingestion is over`() {
        val properties = versionedProperties()

        // Version 1 never arrives, which only happens for a fingerprint that violates the invariant.
        properties.setProperty(3, "prop", "three")
        properties.setProperty(2, "prop", "two")
        properties.ingestionFinished()

        // The changes are still applied in order, so the latest one wins.
        assertThat(properties.valueAt(3, "prop"), equalTo<Any?>("three"))
    }

    @Test
    fun `the final snapshot holds every change`() {
        val properties = versionedProperties("fromEnvironment" to "value")

        properties.setProperty(1, "changed", "value")
        properties.removeProperty(2, "fromEnvironment")

        assertThat(properties.finalSnapshot().get("changed"), equalTo<Any?>("value"))
        assertThat(properties.finalSnapshot().get("fromEnvironment"), nullValue())
    }

    @Test
    fun `a snapshot can be turned into a properties table`() {
        val properties = versionedProperties("fromEnvironment" to "value")

        properties.setProperty(1, "changed", "value")

        assertThat(
            properties.finalSnapshot().toProperties(),
            equalTo(propertiesOf("fromEnvironment" to "value", "changed" to "value"))
        )
    }

    private
    fun versionedProperties(vararg initial: Pair<String, String>) =
        VersionedSystemProperties(propertiesOf(*initial))

    private
    fun propertiesOf(vararg values: Pair<String, String>) = Properties().apply {
        values.forEach { (key, value) -> put(key, value) }
    }

    private
    fun VersionedSystemProperties.valueAt(version: Long, key: String): Any? = snapshotAt(version).get(key)
}
