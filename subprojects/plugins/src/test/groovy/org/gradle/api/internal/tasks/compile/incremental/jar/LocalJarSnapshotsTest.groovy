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



package org.gradle.api.internal.tasks.compile.incremental.jar

import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfo
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.api.internal.tasks.compile.incremental.deps.DefaultDependentsSet.dependents

class LocalJarSnapshotsTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()
    @Subject cache = new LocalJarSnapshots(temp.file("cache.bin"))

    def "empty cache"() {
        expect:
        !cache.getSnapshot(new File("foo.jar"))
    }

    def "caches snapshots"() {
        def info = new ClassDependencyInfo(["Foo": dependents("Bar"), "Bar": dependents()])
        cache.putSnapshots([(new File("foo.jar")): new JarSnapshot(["Foo": "f".bytes], info)])

        when:
        def cache2 = new LocalJarSnapshots(temp.file("cache.bin"))
        def sn = cache2.getSnapshot(new File("foo.jar"))

        then:
        sn == cache2.getSnapshot(new File("foo.jar"))
        sn.hashes == ["Foo": "f".bytes]
        sn.info.getRelevantDependents("Foo").dependentClasses == ["Bar"] as Set
    }
}
