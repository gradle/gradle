/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache.serialization.codecs

import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import spock.lang.Issue


class UnitCodecTest : AbstractUserTypeCodecTest() {

    @Test
    @Issue("https://github.com/gradle/gradle/issues/25560")
    fun `same Unit instance is used upon restore`() {
        configurationCacheRoundtripOf(Unit).run {
            assertThat(this, sameInstance(Unit))
        }
    }
}
