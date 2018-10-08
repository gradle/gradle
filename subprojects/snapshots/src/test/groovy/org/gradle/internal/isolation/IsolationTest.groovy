/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.isolation

import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher
import org.gradle.internal.snapshot.impl.DefaultValueSnapshotter
import spock.lang.Specification

/**
 * Tests that Isolation is implemented for various types of Snapshots.
 */
class IsolationTest extends Specification {
    def snapshotter = new DefaultValueSnapshotter(Stub(ClassLoaderHierarchyHasher), NamedObjectInstantiator.INSTANCE)

    def "can isolate Strings"() {
        given:
        def original = "Original String"
        def snapshot = snapshotter.snapshot(original)

        expect:
        snapshot instanceof Isolatable<String>
        (snapshot as Isolatable<String>).isolate() == original
    }

    def "can isolate an array of scalars"() {
        given:
        def original = ["Hello", "GoodBye"] as String[]
        def snapshot = snapshotter.snapshot(original)

        expect:
        snapshot instanceof Isolatable<Object[]>
        def isolated = (snapshot as Isolatable<Object[]>).isolate()
        isolated == original
        !isolated.is(original)
    }

    def "can isolate an array of an array of scalars"() {
        given:
        def original = [["Hello", "GoodBye"], ["Up", "Down"], ["Left", "Right"]] as String[][]
        def snapshot = snapshotter.snapshot(original)

        expect:
        snapshot instanceof Isolatable<Object[]>
        def isolated = (snapshot as Isolatable<Object[]>).isolate()
        isolated == original
        !isolated.is(original)
    }

    def "can isolate a List"() {
        given:
        def original = ["Hello", "GoodBye"]
        def snapshot = snapshotter.snapshot(original)

        expect:
        snapshot instanceof Isolatable<List>
        def isolated = (snapshot as Isolatable<List>).isolate()
        isolated == original
        !isolated.is(original)
    }
}
