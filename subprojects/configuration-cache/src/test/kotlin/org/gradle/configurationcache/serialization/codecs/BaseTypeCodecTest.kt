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

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.util.Hashtable
import java.util.Properties


class BaseTypeCodecTest : AbstractUserTypeCodecTest() {

    @Test
    fun `can handle Properties`() {
        val properties = Properties()
        properties.setProperty("prop1", "value1")
        properties.setProperty("prop2", "value2")
        configurationCacheRoundtripOf(properties).run {
            assertThat(properties, equalTo(this))
        }
    }

    @Test
    fun `can handle Hashtable`() {
        val hashtable = Hashtable<String, Any>(100)
        hashtable.put("key1", "value1")
        hashtable.put("key2", true)
        hashtable.put("key3", 42)
        configurationCacheRoundtripOf(hashtable).run {
            assertThat(hashtable, equalTo(this))
        }
    }
}
