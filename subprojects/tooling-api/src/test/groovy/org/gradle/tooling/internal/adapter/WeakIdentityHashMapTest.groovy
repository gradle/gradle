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

package org.gradle.tooling.internal.adapter

import spock.lang.Specification


class WeakIdentityHashMapTest extends Specification {

    def "can put a value to map"() {
        Thing thing = new Thing("thing")
        WeakIdentityHashMap<Thing, String> map = new WeakIdentityHashMap<>()
        map.put(thing, "Bar")

        expect:
        map.get(thing) == "Bar"
    }

    def "can compute a value in case of absence"() {
        Thing thing = new Thing("thing")
        WeakIdentityHashMap<Thing, String> map = new WeakIdentityHashMap<>()

        assert map.get(thing) == null

        String foo = map.computeIfAbsent(
            thing,
            new WeakIdentityHashMap.AbsentValueProvider<String>() {
                @Override
                String provide() {
                    return "Foo"
                }
            })

        expect:
        foo == "Foo"
        map.get(thing) == "Foo"
    }

    def "map preserves entries until keys in use"() {
        WeakIdentityHashMap<Thing, String> map = new WeakIdentityHashMap<>()

        Thing key1 = new Thing("Key1")
        Thing key2 = new Thing("Key2")


        when:
        map.put(key1, "Foo")
        map.put(key2, "Bar")

        then:
        map.keySet().every { it.get() != null }

        when:
        key1 = null
        key2 = null

        then:
        waitForConditionAfterGC { map.keySet().every { it.get() == null } }
    }

    def "weakKey is doesn't keep strong reference at referent"() {
        Thing referent = new Thing("thing")
        WeakIdentityHashMap.WeakKey weakKey = new WeakIdentityHashMap.WeakKey(referent)

        when:
        referent = null

        then:
        waitForConditionAfterGC { weakKey.get() == null }
    }

    def "weakKeys for equal objects are different"() {
        Thing thing1 = new Thing("thing")
        Thing thing2 = new Thing("thing")

        assert thing1 == thing2

        WeakIdentityHashMap.WeakKey<Thing> weakKey1 = new WeakIdentityHashMap.WeakKey<>(thing1)
        WeakIdentityHashMap.WeakKey<Thing> weakKey2 = new WeakIdentityHashMap.WeakKey<>(thing2)

        expect:
        weakKey1 != weakKey2
    }

    def "hashCodes of weakKeys for equal objects are different"() {
        Thing thing1 = new Thing("thing")
        Thing thing2 = new Thing("thing")

        assert thing1 == thing2

        WeakIdentityHashMap.WeakKey<Thing> weakKey1 = new WeakIdentityHashMap.WeakKey<>(thing1)
        WeakIdentityHashMap.WeakKey<Thing> weakKey2 = new WeakIdentityHashMap.WeakKey<>(thing2)

        expect:
        weakKey1.hashCode() != weakKey2.hashCode()
    }

    def "weakKeys for same object are equal"() {
        Thing thing1 = new Thing("thing")

        WeakIdentityHashMap.WeakKey<Thing> weakKey1 = new WeakIdentityHashMap.WeakKey<>(thing1)
        WeakIdentityHashMap.WeakKey<Thing> weakKey2 = new WeakIdentityHashMap.WeakKey<>(thing1)

        expect:
        weakKey1 == weakKey2
    }

    def "hashCode of weakKey is system identity hashCode"() {
        Thing thing = new Thing("thing")
        WeakIdentityHashMap.WeakKey<Thing> weakKey = new WeakIdentityHashMap.WeakKey<>(thing)

        expect:
        weakKey.hashCode() == System.identityHashCode(thing)
    }

    class Thing {
        String name

        Thing(String name) {
            this.name = name
        }

        @Override
        boolean equals(Object obj) {
            return obj.name == name
        }

        @Override
        int hashCode() {
            return name.hashCode()
        }
    }

    private boolean waitForConditionAfterGC(Closure<Boolean> condition) {
        System.gc()
        waitForCondition(condition)
    }

    private boolean waitForCondition(Closure<Boolean> condition) {
        def conditionMet = condition.call()
        def startTime = System.currentTimeMillis()

        while (!conditionMet && (System.currentTimeMillis() - startTime) < 2000) {
            Thread.sleep(100)
            conditionMet = condition.call()
        }

        conditionMet
    }
}
