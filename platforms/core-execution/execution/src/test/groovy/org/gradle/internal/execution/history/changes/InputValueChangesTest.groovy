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

package org.gradle.internal.execution.history.changes

import com.google.common.collect.ImmutableSortedMap
import org.gradle.api.Describable
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hasher
import org.gradle.internal.hash.Hashing
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.internal.snapshot.ValueSnapshotter
import org.gradle.internal.snapshot.impl.ClassImplementationSnapshot
import spock.lang.Specification

class InputValueChangesTest extends Specification {
    def executable = Stub(Describable) {
        getDisplayName() >> "task ':test'"
    }

    def "up-to-date when hash of the value is unchanged"() {
        expect:
        changesBetween(
            [prop: hashOf(value("value"))],
            [prop: value("value")]
        ).empty
    }

    def "not up-to-date when value changed"() {
        expect:
        changesBetween(
            [prop: hashOf(value("old"))],
            [prop: value("new")]
        ) == ["Value of input property 'prop' has changed for task ':test'"]
    }

    def "not up-to-date when nested implementation changed"() {
        expect:
        changesBetween(
            [prop: hashOf(implementation("Nested", TestHashCodes.hashCodeFrom(1)))],
            [prop: implementation("Nested", TestHashCodes.hashCodeFrom(2))]
        ) == ["Implementation of input property 'prop' has changed for task ':test'"]
    }

    def "ignores added and removed properties"() {
        expect:
        changesBetween(
            [removed: hashOf(value("value"))],
            [added: value("value")]
        ).empty
    }

    def "reports changed properties in property order"() {
        expect:
        changesBetween(
            [a: hashOf(value("a")), b: hashOf(value("b")), c: hashOf(value("c"))],
            [a: value("changed"), b: value("b"), c: value("changed")]
        ) == [
            "Value of input property 'a' has changed for task ':test'",
            "Value of input property 'c' has changed for task ':test'"
        ]
    }

    private List<String> changesBetween(Map<String, HashCode> previousHashes, Map<String, ValueSnapshot> current) {
        def visitor = new CollectingChangeVisitor()
        new InputValueChanges(ImmutableSortedMap.copyOf(previousHashes), ImmutableSortedMap.copyOf(current), executable).accept(visitor)
        return visitor.changes*.message
    }

    private static HashCode hashOf(ValueSnapshot snapshot) {
        Hashing.hashHashable(snapshot)
    }

    private static ValueSnapshot value(String value) {
        new TestValueSnapshot(value)
    }

    private static ValueSnapshot implementation(String className, HashCode classLoaderHash) {
        new ClassImplementationSnapshot(className, classLoaderHash)
    }

    private static class TestValueSnapshot implements ValueSnapshot {
        private final String value

        TestValueSnapshot(String value) {
            this.value = value
        }

        @Override
        void appendToHasher(Hasher hasher) {
            hasher.putString(value)
        }

        @Override
        ValueSnapshot snapshot(Object value, ValueSnapshotter snapshotter) {
            throw new UnsupportedOperationException()
        }
    }
}
