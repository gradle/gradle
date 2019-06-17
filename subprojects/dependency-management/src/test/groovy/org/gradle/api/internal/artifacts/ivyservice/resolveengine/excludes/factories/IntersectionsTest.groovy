/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories


import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.simple.DefaultExcludeFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static org.gradle.api.internal.artifacts.DefaultModuleIdentifier.newId

class IntersectionsTest extends Specification {
    @Shared
    private ExcludeFactory factory = new DefaultExcludeFactory()

    @Subject
    private Intersections ops = new Intersections(factory)

    def "intersects identical specs"() {
        expect:
        ops.tryIntersect(spec, spec) == spec

        where:
        spec << [
            group("foo"),
            groupSet("foo", "bar"),
            module("foo"),
            moduleSet("foo", "bar"),
            moduleId("foo", "bar"),
            moduleIdSet(["foo", "bar"], ["foo", "baz"])
        ]
    }

    @Unroll
    def "intersection of #one with #other = #expected"() {
        expect:
        ops.tryIntersect(one, other) == expected
        ops.tryIntersect(other, one) == expected

        where:
        one                                                     | other                                                                                                    | expected
        group("org")                                            | group("foo")                                                                                             | factory.nothing()
        group("org")                                            | group("org")                                                                                             | group("org")
        group("org")                                            | groupSet("foo", "org")                                                                                   | group("org")
        group("org")                                            | groupSet("foo", "org", "baz")                                                                            | group("org")
        group("org")                                            | groupSet("foo", "baz")                                                                                   | factory.nothing()
        group("org")                                            | moduleId("org", "bar")                                                                                   | moduleId("org", "bar")
        group("org")                                            | moduleId("com", "bar")                                                                                   | factory.nothing()
        group("org")                                            | moduleIdSet(["foo", "bar"], ["org", "bar"])                                                              | moduleId("org", "bar")
        group("org")                                            | moduleIdSet(["foo", "bar"], ["org", "bar"], ["org", "baz"])                                              | moduleIdSet(["org", "bar"], ["org", "baz"])
        group("org")                                            | module("mod")                                                                                            | null
        group("org")                                            | moduleSet("mod", "mod2")                                                                                 | null
        module("foo")                                           | module("bar")                                                                                            | factory.nothing()
        module("foo")                                           | moduleSet("foo", "bar")                                                                                  | module("foo")
        module("foo")                                           | moduleId("org", "foo")                                                                                   | moduleId("org", "foo")
        module("foo")                                           | moduleId("org", "bar")                                                                                   | factory.nothing()
        module("foo")                                           | moduleIdSet(["org", "foo"], ["org", "bar"])                                                              | moduleId("org", "foo")
        module("foo")                                           | moduleIdSet(["org", "foo"], ["org", "bar"], ["com", "foo"])                                              | moduleIdSet(["org", "foo"], ["com", "foo"])
        groupSet("org", "org2")                                 | groupSet("org2", "org3")                                                                                 | group("org2")
        groupSet("org", "org2", "org3")                         | groupSet("org", "org3", "org4")                                                                          | groupSet("org", "org3")
        groupSet("org", "org2")                                 | moduleId("org", "foo")                                                                                   | moduleId("org", "foo")
        groupSet("org", "org2")                                 | moduleIdSet(["org", "foo"], ["org2", "bar"], ["org3", "baz"])                                            | moduleIdSet(["org", "foo"], ["org2", "bar"])
        moduleIdSet(["org", "foo"], ["org", "bar"])             | moduleIdSet(["org", "bar"], ["org2", "baz"])                                                             | moduleId("org", "bar")
        moduleIdSet(["org", "foo"], ["org", "bar"])             | moduleIdSet(["org", "bar"], ["org2", "baz"], ["org", "foo"])                                             | moduleIdSet(["org", "bar"], ["org", "foo"])

        anyOf(group("org.slf4j"), module("py4j"))               | anyOf(group("org.slf4j"), module("py4j"), moduleIdSet(["org.jboss.netty", "netty"], ["jline", "jline"])) | anyOf(group("org.slf4j"), module("py4j"))
        anyOf(group("G1"), module("M1"), group("G2"))           | anyOf(group("G1"), module("M2"))                                                                         | anyOf(group("G1"), allOf(anyOf(module("M1"), group("G2")), module("M2")))

        // for operations below the result can be further simplified, but it's not this class responsibility to do it
        anyOf(group("foo"), group("bar"))                       | anyOf(group("foo"), group("baz"))                                                                        | anyOf(group("foo"), allOf(group("bar"), group("baz")))
        anyOf(group("g1"), moduleIdSet(["a", "b"], ["a", "c"])) | anyOf(group("g1"), moduleIdSet(["a", "b"], ["a", "d"]))                                                  | anyOf(group("g1"), allOf(moduleIdSet(["a", "b"], ["a", "c"]), moduleIdSet(["a", "b"], ["a", "d"])))
    }

    @Unroll("intersection of #one with #other = #expected using normalizing factory")
    def "further simplifications are performed by the normalizing factory"() {
        given:
        factory = new NormalizingExcludeFactory(factory)
        ops = new Intersections(factory)

        expect:
        ops.tryIntersect(one, other) == expected
        ops.tryIntersect(other, one) == expected

        where:
        one                                                     | other                                                   | expected
        anyOf(group("foo"), group("bar"))                       | anyOf(group("foo"), group("baz"))                       | group("foo")
        anyOf(group("g1"), moduleIdSet(["a", "b"], ["a", "c"])) | anyOf(group("g1"), moduleIdSet(["a", "b"], ["a", "d"])) | anyOf(group("g1"), moduleId("a", "b"))
    }

    private ExcludeSpec group(String group) {
        factory.group(group)
    }

    private ExcludeSpec module(String name) {
        factory.module(name)
    }

    private ExcludeSpec moduleSet(String... names) {
        factory.moduleSet(names as Set<String>)
    }

    private ExcludeSpec groupSet(String... groups) {
        factory.groupSet(groups as Set<String>)
    }

    private ExcludeSpec moduleId(String group, String name) {
        factory.moduleId(newId(group, name))
    }

    private ExcludeSpec moduleIdSet(List<String>... ids) {
        factory.moduleIdSet(ids.collect { newId(it[0], it[1]) } as Set)
    }

    private ExcludeSpec anyOf(ExcludeSpec... specs) {
        switch (specs.length) {
            case 0:
                return factory.nothing()
            case 1:
                return specs[0]
            case 2:
                return factory.anyOf(specs[0], specs[1])
            default:
                return factory.anyOf(specs as Set)
        }
    }

    private ExcludeSpec allOf(ExcludeSpec... specs) {
        switch (specs.length) {
            case 0:
                return factory.nothing()
            case 1:
                return specs[0]
            case 2:
                return factory.allOf(specs[0], specs[1])
            default:
                return factory.allOf(specs as Set)
        }
    }
}
