/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.process.internal


import org.gradle.api.internal.lambdas.SerializableLambdas.SerializableSupplier
import spock.lang.Specification

class DerivedEnvironmentVarsMapTest extends Specification {

    def "changes to base environment are visible after deserialization"() {
        def env = environment([FOO: "1"], [BAR: "2"])
        def map = new DerivedEnvironmentVarsMap(env)

        expect:
        with(deserialized(map)) {
            it["FOO"] == null
            it["BAR"] == "2"
        }
    }

    def "cleared with #method environment is cleared after deserialization"() {
        def env = environment([FOO: "1"], [BAR: "2"])
        def map = new DerivedEnvironmentVarsMap(env)

        when:
        cleanup(map)

        then:
        with(deserialized(map)) {
            it.isEmpty()
        }

        where:
        method               | cleanup
        "clear() "           | { it.clear() }
        "entrySet().clear()" | { it.entrySet().clear() }
        "keySet().clear()"   | { it.keySet().clear() }
        "values().clear()"   | { it.values().clear() }
    }

    def "put is replayed"() {
        def env = environment([FOO: "1"], [BAR: "2"])
        def map = new DerivedEnvironmentVarsMap(env)

        when:
        map.put("BAZ", "3")

        then:
        map["BAZ"] == "3"
        deserialized(map)["BAZ"] == "3"
    }

    def "putAll is replayed"() {
        def env = environment([FOO: "1"], [BAR: "2"])
        def map = new DerivedEnvironmentVarsMap(env)

        when:
        map.putAll(BAZ: "3", QUX: "4")

        then:
        map["BAZ"] == "3"
        map["QUX"] == "4"
        with(deserialized(map)) {
            it["BAZ"] == "3"
            it["QUX"] == "4"
        }
    }

    def "remove is replayed"() {
        def env = environment([FOO: "1"], [BAR: "2"])
        def map = new DerivedEnvironmentVarsMap(env)

        when:
        map.remove(key)

        then:
        !map.containsKey(key)
        !deserialized(map).containsKey(key)

        where:
        key << ["FOO", "BAR", "BAZ"]
    }

    def "remove through #iter iterator is replayed"() {
        def env = environment([FOO: "1"], [FOO: "2", BAR: "3"])
        def map = new DerivedEnvironmentVarsMap(env)

        when:
        def i = iterBuilder(map).iterator()
        i.next()
        i.remove()

        then:
        !map.containsKey("FOO")
        with(deserialized(map)) {
            !it.containsKey("FOO")
            it.containsKey("BAR")
        }

        where:
        iter       | iterBuilder
        "entrySet" | { it.entrySet() }
        "keySet"   | { it.keySet() }
        "values"   | { it.values() }
    }

    def "replacing with put is replayed"() {
        def env = environment([FOO: "1"], [BAR: "1", FOO: "2"])
        def map = new DerivedEnvironmentVarsMap(env)

        when:
        map.put("FOO", "3")

        then:
        map["FOO"] == "3"
        with(deserialized(map)) {
            it["BAR"] == "1"
            it["FOO"] == "3"
        }
    }

    def "replacing with putAll is replayed"() {
        def env = environment([FOO: "1"], [BAR: "1", FOO: "2"])
        def map = new DerivedEnvironmentVarsMap(env)

        when:
        map.putAll(FOO: "3", "BAR": "4")

        then:
        map["BAR"] == "4"
        map["FOO"] == "3"
        with(deserialized(map)) {
            it["BAR"] == "4"
            it["FOO"] == "3"
        }
    }

    def "replacing with entry is replayed"() {
        def env = environment([FOO: "1"], [FOO: "2"])
        def map = new DerivedEnvironmentVarsMap(env)

        when:
        map.entrySet().iterator().next().value = "3"

        then:
        map["FOO"] == "3"
        with(deserialized(map)) {
            it["FOO"] == "3"
        }
    }

    private DerivedEnvironmentVarsMap deserialized(DerivedEnvironmentVarsMap map) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        try (def out = new ObjectOutputStream(baos)) {
            out.writeObject(map)
        }
        try (def inp = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            return inp.readObject() as DerivedEnvironmentVarsMap
        }
    }

    private def environment(Map<String, String> before, Map<String, String> after) {
        return new ChangingSupplier(before, after)
    }

    private static class ChangingSupplier implements SerializableSupplier<Map<String, String>> {
        private final transient Map<String, String> before
        private final Map<String, String> after

        ChangingSupplier(Map<String, String> before, Map<String, String> after) {
            this.before = before
            this.after = after
        }

        @Override
        Map<String, String> get() {
            return before ?: after
        }
    }
}
