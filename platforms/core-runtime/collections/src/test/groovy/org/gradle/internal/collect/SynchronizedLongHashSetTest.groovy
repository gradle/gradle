/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.collect

import spock.lang.Specification

class SynchronizedLongHashSetTest extends Specification {

    def set = new SynchronizedLongHashSet()

    def "add and contains"() {
        when:
        set.add(1)
        set.add(2)
        set.add(3)

        then:
        set.contains(1)
        set.contains(2)
        set.contains(3)
        !set.contains(4)
        set.size() == 3
    }

    def "remove returns true for existing and false for missing"() {
        given:
        set.add(1)
        set.add(2)

        expect:
        set.remove(1)
        !set.remove(1)
        !set.remove(99)
        set.contains(2)
        !set.contains(1)
        set.size() == 1
    }

    def "handles add after remove of same slot (tombstone reuse)"() {
        given:
        set.add(1)
        set.remove(1)

        when:
        set.add(1)

        then:
        set.contains(1)
        set.size() == 1
    }

    def "grows table on high load"() {
        when:
        // Initial capacity is 16, load factor is 75% — should rehash after 12 entries
        (1L..20L).each { set.add(it) }

        then:
        set.size() == 20
        (1L..20L).every { set.contains(it) }
        !set.contains(21)
    }

    def "handles many add/remove cycles without degradation"() {
        when:
        // Simulate operation lifecycle: add then remove, many times
        (1L..1000L).each {
            set.add(it)
            assert set.contains(it)
            set.remove(it)
            assert !set.contains(it)
        }

        then:
        set.size() == 0
    }

    def "concurrent add and remove"() {
        given:
        def threads = 8
        def opsPerThread = 1000

        when:
        def threadList = (1..threads).collect { threadNum ->
            Thread.start {
                for (long i = 1; i <= opsPerThread; i++) {
                    long id = threadNum * 100000 + i
                    set.add(id)
                    assert set.contains(id)
                    set.remove(id)
                }
            }
        }
        threadList.each { it.join() }

        then:
        set.size() == 0
    }

    def "handles large IDs"() {
        when:
        set.add(Long.MAX_VALUE - 1)
        set.add(Integer.MAX_VALUE + 1L)
        set.add(999999999999L)

        then:
        set.contains(Long.MAX_VALUE - 1)
        set.contains(Integer.MAX_VALUE + 1L)
        set.contains(999999999999L)
        set.size() == 3
    }
}
