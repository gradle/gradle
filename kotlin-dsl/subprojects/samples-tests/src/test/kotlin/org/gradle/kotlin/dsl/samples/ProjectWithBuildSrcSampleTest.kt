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

package org.gradle.kotlin.dsl.samples

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class ProjectWithBuildSrcSampleTest : AbstractSampleTest("project-with-buildSrc") {

    @Test
    fun `test precompiled script plugin`() {
        assertThat(
            build("myReport").output,
            containsString("my.flag = true")
        )
    }

    @Test
    fun `test profile`() {
        val output = build("printProfile").output
        assertThat(
            output,
            containsString("The current profile is dev")
        )
        assertThat(
            output,
            containsString("Here's the profile: dev")
        )
    }

    @Test
    fun `test profile with property`() {
        val output = build("printProfile", "-Pprofile=prod").output
        assertThat(
            output,
            containsString("The current profile is prod")
        )
        assertThat(
            output,
            containsString("Here's the profile: prod")
        )
    }
}
