/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.remote.internal.hub

import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.DefaultSerializerRegistry
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.SerializerSpec

class DefaultMethodArgsSerializerTest extends SerializerSpec {
    def registry1 = new DefaultSerializerRegistry()
    def registry2 = new DefaultSerializerRegistry()
    def defaultArgsBuilder = Mock(MethodArgsSerializer)
    def serializer = new DefaultMethodArgsSerializer([registry1, registry2], defaultArgsBuilder)

    def "serializes an empty args array"() {
        expect:
        def arraySerializer = serializer.forTypes([] as Class[])
        serialize([] as Object[], arraySerializer).length == 0
        toBytes([] as Object[], arraySerializer).length == 0
    }

    def "serializes multiple args using the serializer registry that can serialize all types"() {
        given:
        registry2.register(String, BaseSerializerFactory.STRING_SERIALIZER)
        registry2.register(Long, BaseSerializerFactory.LONG_SERIALIZER)

        expect:
        def arraySerializer = serializer.forTypes([String, Long, String] as Class[])
        serialize(["a", 12L, "b"] as Object[], arraySerializer) == ["a", 12L, "b"] as Object[]
    }

    def "falls back to default when no serializer registry knows about types"() {
        given:
        def serializer = Stub(Serializer)
        defaultArgsBuilder.forTypes(_) >> serializer

        expect:
        this.serializer.forTypes([String, Long, String] as Class[]) == serializer
    }
}
