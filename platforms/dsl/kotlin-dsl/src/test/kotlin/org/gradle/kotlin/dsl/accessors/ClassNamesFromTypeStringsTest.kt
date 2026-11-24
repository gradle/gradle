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

package org.gradle.kotlin.dsl.accessors

import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassNamesFromTypeStringsTest {

    @Test
    fun `class names from type strings`() {

        val subject = ClassNamesFromTypeStrings()

        subject.classNamesFrom("String").apply {
            assertTrue(all.isEmpty())
            assertTrue(leaves.isEmpty())
        }

        subject.classNamesFrom("CustomTask").apply {
            assertThat(all, hasItems("CustomTask"))
            assertThat(leaves, hasItems("CustomTask"))
        }

        subject.classNamesFrom("java.util.List<String>").apply {
            assertThat(all, hasItems("java.util.List"))
            assertTrue(leaves.isEmpty())
        }

        subject.classNamesFrom("org.gradle.api.NamedDomainObjectContainer<Extension>").apply {
            assertThat(all, hasItems("org.gradle.api.NamedDomainObjectContainer", "Extension"))
            assertThat(leaves, hasItems("Extension"))
        }

        subject.classNamesFrom("java.lang.String").apply {
            assertThat(all, hasItems("java.lang.String"))
            assertThat(leaves, hasItems("java.lang.String"))
        }

        subject.classNamesFrom("java.util.Map<java.util.List, java.util.Set>").apply {
            assertThat(all, hasItems("java.util.Map", "java.util.List", "java.util.Set"))
            assertThat(leaves, hasItems("java.util.List", "java.util.Set"))
        }
    }
}
