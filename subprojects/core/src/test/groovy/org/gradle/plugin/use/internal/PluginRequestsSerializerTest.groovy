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
import org.gradle.plugin.management.internal.DefaultPluginRequest
import org.gradle.plugin.management.internal.DefaultPluginRequests
import org.gradle.plugin.management.internal.PluginRequestsSerializer

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
            new DefaultPluginRequest("buildscript", 1, "java", null, null, true),
            new DefaultPluginRequest("buildscript", 2, "groovy", null, null, false),
            new DefaultPluginRequest("initscript", 3, "custom", "1.0", null, false),
            new DefaultPluginRequest("buildscript", 4, "other", null, "other.gradle", true)
        ]), serializer)

        then:
        serialized*.requestingScriptLineNumber == [1, 2, 3, 4]
        serialized*.requestingScriptDisplayName == ["buildscript", "buildscript", "initscript", "buildscript"]

        and:
        serialized*.id == ["java", "groovy", "custom", "other"].collect { DefaultPluginId.of(it) }
        serialized*.version == [null, null, "1.0", null]
        serialized*.script == [null, null, null, "other.gradle"]
        serialized*.apply == [true, false, false, true]
    }
}
