/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class AccessorsClassPathIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `warning is emitted if a gradle slash project dash schema dot json file is present`() {

        withDefaultSettings()
        withBuildScript("")

        withFile(PROJECT_SCHEMA_RESOURCE_PATH)

        assertThat(build("help").output, containsString(PROJECT_SCHEMA_RESOURCE_DISCONTINUED_WARNING))
    }
}
