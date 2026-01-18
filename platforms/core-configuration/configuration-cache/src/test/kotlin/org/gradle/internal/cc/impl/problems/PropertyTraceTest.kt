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

package org.gradle.internal.cc.impl.problems

import org.gradle.api.Task
import org.gradle.internal.configuration.problems.PropertyKind
import org.gradle.internal.configuration.problems.PropertyTrace

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals


class PropertyTraceTest {

    @Test
    fun `field of bean found in input property of task`() {

        val beanType = PropertyTraceTest::class.java
        val taskType = Task::class.java

        assertThat(
            PropertyTrace.Property(
                PropertyKind.Field,
                "f",
                PropertyTrace.Bean(
                    beanType,
                    PropertyTrace.Property(
                        PropertyKind.InputProperty,
                        "i",
                        PropertyTrace.Task(
                            taskType,
                            ":t"
                        )
                    )
                )
            ).toString(),
            equalTo(
                "field `f` of " +
                    "`${beanType.name}` bean found in " +
                    "input property `i` of " +
                    "task `:t` of type `${taskType.name}`"
            )
        )
    }

    @Test
    fun `toString representation takes the entire property trace chain account`() {
        assertEquals(PropertyTrace.Unknown.toString(), "unknown location")
        assertEquals(
            PropertyTrace.Property(PropertyKind.PropertyUsage, "prop1", PropertyTrace.Unknown).toString(),
            "property usage `prop1` of unknown location",
        )
        assertEquals(
            PropertyTrace.Property(PropertyKind.PropertyUsage, "prop1", PropertyTrace.Project(":proj1", PropertyTrace.Unknown)).toString(),
            "property usage `prop1` of project `:proj1` in unknown location",
        )
    }

    @Test
    fun `fullHash takes the entire property trace chain into account`() {
        assertEquals(PropertyTrace.Unknown.fullHash, PropertyTrace.Unknown.fullHash)
        assertNotEquals(PropertyTrace.Unknown.fullHash, PropertyTrace.Gradle.fullHash)
        assertEquals(
            PropertyTrace.Property(PropertyKind.PropertyUsage, "prop1", PropertyTrace.Unknown).fullHash,
            PropertyTrace.Property(PropertyKind.PropertyUsage, "prop1", PropertyTrace.Unknown).fullHash
        )
        assertNotEquals(
            PropertyTrace.Property(PropertyKind.PropertyUsage, "prop1", PropertyTrace.Unknown).fullHash,
            PropertyTrace.Property(PropertyKind.PropertyUsage, "prop1", PropertyTrace.Gradle).fullHash
        )
        assertNotEquals(
            PropertyTrace.Property(PropertyKind.PropertyUsage, "prop1", PropertyTrace.Unknown).fullHash,
            PropertyTrace.Property(PropertyKind.PropertyUsage, "prop2", PropertyTrace.Unknown)
        )
    }
}
