/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.plugin.use.internal

import org.gradle.internal.serialize.SerializerSpec
import org.gradle.plugin.internal.PluginId

class PluginRequestsSerializerTest extends SerializerSpec {

    def serializer = new PluginRequestsSerializer()

    def "empty"() {
        when:
        def serialized = serialize(new DefaultPluginRequests([]), serializer)

        then:
        serialized.empty
    }

    def "non empty"() {
        when:
        def serialized = serialize(new DefaultPluginRequests([
                new DefaultPluginRequest("java", null, 1, "buildscript"),
                new DefaultPluginRequest("groovy", null, 2, "buildscript"),
                new DefaultPluginRequest("custom", "1.0", 3, "initscript")
        ]), serializer)

        then:
        serialized*.id == ["java", "groovy", "custom"].collect { PluginId.of(it) }
        serialized*.version == [null, null, "1.0"]
        serialized*.lineNumber == [1, 2, 3]
        serialized*.scriptDisplayName == ["buildscript", "buildscript", "initscript"]
    }
}
