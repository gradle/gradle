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

class MutationNotifyingCollectionsTest extends Specification {

    def "MutationNotifyingList notifies '#expectedMessage'"() {
        given:
        List<String> mutations = []
        def list = new MutationNotifyingList<>(new ArrayList(["a", "b"]), { mutations << it })

        when:
        operation(list)

        then:
        mutations == [expectedMessage]

        where:
        expectedMessage              | operation
        "add(Object)"                | { l -> l.add("x") }
        "add(int, Object)"           | { l -> l.add(0, "x") }
        "addAll(Collection)"         | { l -> l.addAll(["x"]) }
        "addAll(int, Collection)"    | { l -> l.addAll(0, ["x"]) }
        "set(int, Object)"           | { l -> l.set(0, "x") }
        "remove(int)"                | { l -> l.remove(0) }
        "remove(Object)"             | { l -> l.remove("a") }
        "removeAll(Collection)"      | { l -> l.removeAll(["a"]) }
        "retainAll(Collection)"      | { l -> l.retainAll(["a"]) }
        "removeIf(Predicate)"        | { l -> l.removeIf { it == "nope" } }
        "replaceAll(UnaryOperator)"  | { l -> l.replaceAll { it.toUpperCase() } }
        "sort(Comparator)"           | { l -> l.sort({ x, y -> x <=> y } as Comparator) }
        "clear()"                    | { l -> l.clear() }
    }

    def "MutationNotifyingList does not notify on read operations"() {
        List<String> mutations = []
        def list = new MutationNotifyingList<>(["a", "b"], { mutations << it })

        when:
        list.size()
        list.isEmpty()
        list.contains("a")
        list.get(0)
        list.indexOf("a")
        list.toArray()
        list.iterator()

        then:
        mutations.empty
    }

    def "MutationNotifyingSet notifies '#expectedMessage'"() {
        given:
        List<String> mutations = []
        def set = new MutationNotifyingSet<>(new LinkedHashSet(["a", "b"]), { mutations << it })

        when:
        operation(set)

        then:
        mutations == [expectedMessage]

        where:
        expectedMessage         | operation
        "add(Object)"           | { s -> s.add("x") }
        "addAll(Collection)"    | { s -> s.addAll(["x"]) }
        "remove(Object)"        | { s -> s.remove("a") }
        "removeAll(Collection)" | { s -> s.removeAll(["a"]) }
        "retainAll(Collection)" | { s -> s.retainAll(["a"]) }
        "removeIf(Predicate)"   | { s -> s.removeIf { it == "nope" } }
        "clear()"               | { s -> s.clear() }
    }

    def "MutationNotifyingSet does not notify on read operations"() {
        List<String> mutations = []
        def set = new MutationNotifyingSet<>(new LinkedHashSet(["a", "b"]), { mutations << it })

        when:
        set.size()
        set.isEmpty()
        set.contains("a")
        set.containsAll(["a"])
        set.toArray()
        set.iterator()

        then:
        mutations.empty
    }

    def "MutationNotifyingMap notifies '#expectedMessage'"() {
        given:
        List<String> mutations = []
        def map = new MutationNotifyingMap<>(["a": "1", "b": "2"], { mutations << it })

        when:
        operation(map)

        then:
        mutations == [expectedMessage]

        where:
        expectedMessage                       | operation
        "put(Object, Object)"                 | { m -> m.put("c", "3") }
        "putIfAbsent(Object, Object)"         | { m -> m.putIfAbsent("c", "3") }
        "putAll(Map)"                         | { m -> m.putAll(["c": "3"]) }
        "remove(Object)"                      | { m -> m.remove("a") }
        "remove(Object, Object)"              | { m -> m.remove("a", "1") }
        "replace(Object, Object)"             | { m -> m.replace("a", "9") }
        "replace(Object, Object, Object)"     | { m -> m.replace("a", "1", "9") }
        "replaceAll(BiFunction)"              | { m -> m.replaceAll { k, v -> v } }
        "merge(Object, Object, BiFunction)"   | { m -> m.merge("a", "9", { x, y -> y }) }
        "compute(Object, BiFunction)"         | { m -> m.compute("a") { k, v -> "9" } }
        "computeIfAbsent(Object, Function)"   | { m -> m.computeIfAbsent("c") { "9" } }
        "computeIfPresent(Object, BiFunction)"| { m -> m.computeIfPresent("a") { k, v -> "9" } }
        "clear()"                             | { m -> m.clear() }
    }

    def "MutationNotifyingMap does not notify on read operations"() {
        List<String> mutations = []
        def map = new MutationNotifyingMap<>(["a": "1", "b": "2"], { mutations << it })

        when:
        map.size()
        map.isEmpty()
        map.containsKey("a")
        map.containsValue("1")
        map.get("a")
        map.keySet()
        map.values()
        map.entrySet()

        then:
        mutations.empty
    }

    def "MutationNotifyingList notifies on mutation through iterators and sublists"() {
        List<String> mutations = []
        def backing = ["a", "b", "c"]
        def list = new MutationNotifyingList<>(backing, { mutations << it })

        when:
        def it = list.iterator()
        it.next()
        it.remove()
        then:
        mutations == ["iterator().remove()"]
        backing == ["b", "c"]

        when:
        mutations.clear()
        def listIt = list.listIterator()
        listIt.next()
        listIt.set("x")
        listIt.add("y")
        listIt.previous()
        listIt.remove()
        then:
        mutations == ["listIterator().set(Object)", "listIterator().add(Object)", "listIterator().remove()"]

        when:
        mutations.clear()
        list.subList(0, 1).clear()
        then:
        mutations == ["subList().clear()"]

        when:
        mutations.clear()
        list.subList(0, 1).iterator() // read-only use of a sublist
        list.removeIf { it == "nope" }
        then:
        mutations == ["removeIf(Predicate)"]
    }

    def "MutationNotifyingSet notifies on mutation through iterator and removeIf"() {
        List<String> mutations = []
        def backing = new LinkedHashSet(["a", "b"])
        def set = new MutationNotifyingSet<>(backing, { mutations << it })

        when:
        def it = set.iterator()
        it.next()
        it.remove()
        then:
        mutations == ["iterator().remove()"]
        backing == ["b"] as Set

        when:
        mutations.clear()
        set.removeIf { it == "nope" }
        then:
        mutations == ["removeIf(Predicate)"]
    }

    def "MutationNotifyingMap notifies on mutation through its views"() {
        List<String> mutations = []
        def backing = ["a": "1", "b": "2", "c": "3"]
        def map = new MutationNotifyingMap<>(backing, { mutations << it })

        when:
        map.keySet().remove("a")
        then:
        mutations == ["keySet().remove(Object)"]
        !backing.containsKey("a")

        when:
        mutations.clear()
        def keyIt = map.keySet().iterator()
        keyIt.next()
        keyIt.remove()
        then:
        mutations == ["keySet().iterator().remove()"]
        !backing.containsKey("b")

        when:
        mutations.clear()
        map.values().remove("3")
        then:
        mutations == ["values().remove(Object)"]
        backing.isEmpty()
    }

    def "MutationNotifyingMap notifies on mutation through its entry set"() {
        List<String> mutations = []
        def backing = ["a": "1", "b": "2"]
        def map = new MutationNotifyingMap<>(backing, { mutations << it })

        when:
        def entryIt = map.entrySet().iterator()
        def entry = entryIt.next()
        entry.setValue("9")
        then:
        mutations == ["entrySet().Entry.setValue(Object)"]
        backing["a"] == "9"

        when:
        mutations.clear()
        entryIt.remove()
        then:
        mutations == ["entrySet().iterator().remove()"]
        !backing.containsKey("a")

        when:
        mutations.clear()
        map.entrySet().forEach { it.setValue("7") }
        then:
        mutations == ["entrySet().Entry.setValue(Object)"]
        backing["b"] == "7"

        when:
        mutations.clear()
        map.entrySet().stream().forEach { it.setValue("5") }
        then:
        mutations == ["entrySet().Entry.setValue(Object)"]
        backing["b"] == "5"

        when:
        mutations.clear()
        map.entrySet().removeIf { it.key == "b" }
        then:
        mutations == ["entrySet().removeIf(Predicate)"]
        backing.isEmpty()
    }

    def "map view reads do not notify"() {
        List<String> mutations = []
        def map = new MutationNotifyingMap<>(["a": "1", "b": "2"], { mutations << it })

        when:
        map.keySet().contains("a")
        map.keySet().each { }
        map.values().contains("1")
        map.entrySet().each { it.key; it.value }
        map.entrySet().stream().map { it.value }.count()

        then:
        mutations.empty
    }

    def "wrappers serialize as their delegate"() {
        // Wrappers may be captured in task state and serialized to the configuration cache;
        // the notification callback is runtime-only and must not be dragged along.
        given:
        def list = new MutationNotifyingList<>(["a"], { })
        def set = new MutationNotifyingSet<>(new LinkedHashSet(["a"]), { })
        def map = new MutationNotifyingMap<>(["a": "1"], { })

        expect:
        javaRoundTrip(list) == ["a"]
        javaRoundTrip(list) instanceof ArrayList
        javaRoundTrip(set) == (["a"] as Set)
        javaRoundTrip(set) instanceof LinkedHashSet
        javaRoundTrip(map) == ["a": "1"]
        javaRoundTrip(map) instanceof LinkedHashMap
    }

    private static Object javaRoundTrip(Object o) {
        def bytes = new ByteArrayOutputStream()
        new ObjectOutputStream(bytes).withCloseable { it.writeObject(o) }
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).withCloseable { it.readObject() }
    }

    def "MutationNotifyingList notifies on mutation through positioned list iterator"() {
        given:
        List<String> mutations = []
        def list = new MutationNotifyingList<>(new ArrayList(["a", "b", "c"]), { mutations << it })

        when:
        def it = list.listIterator(1)
        it.next()
        it.remove()

        then:
        mutations == ["listIterator().remove()"]
        !list.contains("b")
    }

    def "exotic map mutators delegate results and write through"() {
        given:
        List<String> mutations = []
        def backing = ["a": "1"]
        def map = new MutationNotifyingMap<>(backing, { mutations << it })

        expect:
        map.merge("a", "2", { x, y -> x + y }) == "12"
        backing["a"] == "12"
        map.compute("a") { k, v -> v + "!" } == "12!"
        backing["a"] == "12!"
        map.computeIfAbsent("b") { "9" } == "9"
        backing["b"] == "9"
        map.computeIfPresent("missing") { k, v -> "x" } == null
        !backing.containsKey("missing")
        mutations.size() == 4
    }

    def "entrySet toArray returns live entries that do not notify on setValue"() {
        // Deliberate coverage boundary: wrapping toArray() results is not worth the complexity,
        // see MutationNotifyingEntrySet. If this behavior changes, it is a design decision, not a bug.
        given:
        List<String> mutations = []
        def backing = ["a": "1"]
        def map = new MutationNotifyingMap<>(backing, { mutations << it })

        when:
        def entry = map.entrySet().toArray()[0] as Map.Entry
        entry.setValue("9")

        then:
        backing["a"] == "9"
        mutations.empty
    }

}
