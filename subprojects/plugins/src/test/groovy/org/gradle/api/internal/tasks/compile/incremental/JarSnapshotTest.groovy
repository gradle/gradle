/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental

import spock.lang.Specification

class JarSnapshotTest extends Specification {

    def "knows when snapshots are the same"() {
        JarSnapshot s1 = new JarSnapshot(["com.Foo": "f".bytes, "Bar": "b".bytes])
        JarSnapshot s2 = new JarSnapshot(["com.Foo": "f".bytes, "Bar": "b".bytes])

        expect:
        s1.compareToSnapshot(s2).changedClasses.isEmpty()
        s2.compareToSnapshot(s1).changedClasses.isEmpty()
    }

    def "knows when other snapshots have extra/missing classes"() {
        JarSnapshot s1 = new JarSnapshot(["com.Foo": "f".bytes, "Bar": "b".bytes, "Car": "c".bytes])
        JarSnapshot s2 = new JarSnapshot(["com.Foo": "f".bytes])

        expect:
        s1.compareToSnapshot(s2).changedClasses == ["Bar", "Car"]
        s2.compareToSnapshot(s1).changedClasses == [] //ignore class additions
    }

    def "knows when other snapshots have class with different hash"() {
        JarSnapshot s1 = new JarSnapshot(["com.Foo": "f".bytes, "Bar": "b".bytes, "Car": "c".bytes])
        JarSnapshot s2 = new JarSnapshot(["Car": "xxx".bytes, "com.Foo": "f".bytes])

        expect:
        s1.compareToSnapshot(s2).changedClasses == ["Bar", "Car"]
        s2.compareToSnapshot(s1).changedClasses == ["Car"]
    }
}
