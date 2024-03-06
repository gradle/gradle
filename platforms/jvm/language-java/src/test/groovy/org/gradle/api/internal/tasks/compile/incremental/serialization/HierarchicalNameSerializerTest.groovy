/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.serialization

import org.gradle.api.internal.cache.StringInterner
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import spock.lang.Specification

class HierarchicalNameSerializerTest extends Specification {
    HierarchicalNameSerializer serializer = new HierarchicalNameSerializer(new StringInterner())
    HierarchicalNameSerializer deserializer = new HierarchicalNameSerializer(new StringInterner())

    def "can deserialize serialized form"() {
        given:
        def out = new ByteArrayOutputStream()

        when:
        new KryoBackedEncoder(out).withCloseable { encoder ->
            names.each {
                serializer.write(encoder, it)
            }
        }
        def bytes = out.toByteArray()
        def decoder = new KryoBackedDecoder(new ByteArrayInputStream(bytes))
        def result = []
        names.each {
            result << deserializer.read(decoder)
        }

        then:
        result == names
        int originalSize = names.sum {it.length() } as int
        int newSize = bytes.length
        newSize < originalSize / 2

        where:
        names = [
            "com/foo/bar/FooBarTest.java",
            "com.foo.bar.FooBarTest",
            "com/foo/bar/FooBarBazTest.java",
            "com.foo.bar.FooBarBazTest",
            "com/foo/bar/FooBarBazTest\$SomeUtility.java",
            "com.foo.bar.FooBarBazTest\$SomeUtility",
            "com.fizz.buzz.TestUtil",
            "com.fizz.buzz.SomeUtil",
            "com.google.common.collect.ImmutableSet",
            "com.google.common.collect.ImmutableSet",
            "com.google.common.collect.ImmutableSet",
            "com.google.common.collect.ImmutableSet",
            "com.google.common.collect.ImmutableSet",
            "com.google.common.collect.ImmutableList",
            "com.google.common.collect.ImmutableList",
            "com.google.common.collect.ImmutableList",
            "com.google.common.collect.ImmutableCollection",

        ]
    }
}
