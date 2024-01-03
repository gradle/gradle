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


import spock.lang.Specification
import spock.lang.Subject

class IntersectionsTest extends Specification implements ExcludeTestSupport {

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

    def "intersection of #one with #other = #expected"() {
        expect:
        ops.tryIntersect(one, other) == expected
        ops.tryIntersect(other, one) == expected

        where:
        one                                                                                                            | other                                                                                                                 | expected
        group("org")                                                                                                   | group("foo")                                                                                                          | factory.nothing()
        group("org")                                                                                                   | group("org")                                                                                                          | group("org")
        group("org")                                                                                                   | groupSet("foo", "org")                                                                                                | group("org")
        group("org")                                                                                                   | groupSet("foo", "org", "baz")                                                                                         | group("org")
        group("org")                                                                                                   | groupSet("foo", "baz")                                                                                                | factory.nothing()
        group("org")                                                                                                   | moduleId("org", "bar")                                                                                                | moduleId("org", "bar")
        group("org")                                                                                                   | moduleId("com", "bar")                                                                                                | factory.nothing()
        group("org")                                                                                                   | moduleIdSet(["foo", "bar"], ["org", "bar"])                                                                           | moduleId("org", "bar")
        group("org")                                                                                                   | moduleIdSet(["foo", "bar"], ["org", "bar"], ["org", "baz"])                                                           | moduleIdSet(["org", "bar"], ["org", "baz"])
        group("org")                                                                                                   | module("mod")                                                                                                         | moduleId("org", "mod")
        group("org")                                                                                                   | moduleSet("mod", "mod2")                                                                                              | moduleIdSet(["org", "mod"], ["org", "mod2"])
        module("foo")                                                                                                  | module("bar")                                                                                                         | factory.nothing()
        module("foo")                                                                                                  | moduleSet("foo", "bar")                                                                                               | module("foo")
        module("foo")                                                                                                  | moduleSet("bar", "baz")                                                                                               | factory.nothing()
        module("foo")                                                                                                  | moduleId("org", "foo")                                                                                                | moduleId("org", "foo")
        module("foo")                                                                                                  | moduleId("org", "bar")                                                                                                | factory.nothing()
        module("foo")                                                                                                  | moduleIdSet(["org", "foo"], ["org", "bar"])                                                                           | moduleId("org", "foo")
        module("foo")                                                                                                  | moduleIdSet(["org", "foo"], ["org", "bar"], ["com", "foo"])                                                           | moduleIdSet(["org", "foo"], ["com", "foo"])
        groupSet("org", "org2")                                                                                        | groupSet("org2", "org3")                                                                                              | group("org2")
        groupSet("org", "org2", "org3")                                                                                | groupSet("org", "org3", "org4")                                                                                       | groupSet("org", "org3")
        groupSet("org", "org2")                                                                                        | moduleId("org", "foo")                                                                                                | moduleId("org", "foo")
        groupSet("org", "org2")                                                                                        | moduleIdSet(["org", "foo"], ["org2", "bar"], ["org3", "baz"])                                                         | moduleIdSet(["org", "foo"], ["org2", "bar"])
        moduleIdSet(["org", "foo"], ["org", "bar"])                                                                    | moduleIdSet(["org", "bar"], ["org2", "baz"])                                                                          | moduleId("org", "bar")
        moduleIdSet(["org", "foo"], ["org", "bar"])                                                                    | moduleIdSet(["org", "bar"], ["org2", "baz"], ["org", "foo"])                                                          | moduleIdSet(["org", "bar"], ["org", "foo"])
        moduleIdSet(["org", "foo"], ["org", "bar"], ["org2", "bar"])                                                   | module("bar")                                                                                                         | moduleIdSet(["org", "bar"], ["org2", "bar"])
        moduleIdSet(["org", "foo"], ["org", "bar"], ["org2", "bar"])                                                   | module("foo")                                                                                                         | moduleId("org", "foo")
        moduleIdSet(["org", "foo"], ["org", "bar"], ["org2", "bar"])                                                   | moduleSet("foo", "bar")                                                                                               | moduleIdSet(["org", "foo"], ["org", "bar"], ["org2", "bar"])
        moduleIdSet(["org", "foo"], ["org", "bar"], ["org2", "bar"])                                                   | moduleSet("meh", "foo")                                                                                               | moduleId("org", "foo")
        moduleIdSet(["org", "foo"], ["org", "bar"], ["org2", "bar"])                                                   | moduleSet("meh")                                                                                                      | nothing()
        moduleIdSet(["org", "foo"], ["org", "bar"], ["org2", "bar"])                                                   | group("org")                                                                                                          | moduleIdSet(["org", "foo"], ["org", "bar"])
        moduleIdSet(["org", "foo"], ["org", "bar"], ["org2", "bar"])                                                   | group("org2")                                                                                                         | moduleId("org2", "bar")
        moduleIdSet(["org", "foo"], ["org", "bar"], ["org2", "bar"])                                                   | groupSet("org3", "org2")                                                                                              | moduleId("org2", "bar")
        moduleIdSet(["org", "foo"], ["org", "bar"], ["org2", "bar"])                                                   | groupSet("org3")                                                                                                      | nothing()
        moduleId("com.google.collections", "google-collections")                                                       | moduleIdSet(["com.sun.jmx", "jmxri"], ["javax.jms", "jms"], ["com.sun.jdmk", "jmxtools"])                             | nothing()
        moduleId("com.google.collections", "google-collections")                                                       | moduleIdSet(["com.sun.jmx", "jmxri"], ["com.google.collections", "google-collections"], ["com.sun.jdmk", "jmxtools"]) | moduleId("com.google.collections", "google-collections")
        moduleId("org", "foo")                                                                                         | moduleId("org2", "baz")                                                                                               | nothing()
        moduleId("org", "foo")                                                                                         | moduleSet("foo", "baz")                                                                                               | moduleId("org", "foo")

        anyOf(group("org.slf4j"), module("py4j"))                                                                      | anyOf(group("org.slf4j"), module("py4j"), moduleIdSet(["org.jboss.netty", "netty"], ["jline", "jline"]))              | anyOf(group("org.slf4j"), module("py4j"))
        anyOf(group("G1"), module("M1"), group("G2"))                                                                  | anyOf(group("G1"), module("M2"))                                                                                      | anyOf(group("G1"), allOf(anyOf(module("M1"), group("G2")), module("M2")))

        anyOf(group("foo"), group("bar"))                                                                              | group("baz")                                                                                                          | nothing()
        anyOf(moduleIdSet(["G:A"], ["G2:P"]), groupSet("G3", "G4"))                                                    | moduleIdSet(["G5", "A"], ["G6", "B"])                                                                                 | nothing()
        anyOf(moduleIdSet(["G:A"], ["G2:P"]), groupSet("G3", "G4"))                                                    | groupSet("G3", "G4")                                                                                                  | groupSet("G3", "G4")

        // should not cause stack overflow because components cannot be reduced
        anyOf(ivy("org", "mod", artifact("mod"), "exact"), ivy("org", "mod2", artifact("mod"), "exact"))               | group("org")                                                                                                          | null
        anyOf(ivy("org", "mod", artifact("mod"), "exact"), ivy("org", "mod2", artifact("mod"), "exact"), group("bar")) | group("org")                                                                                                          | anyOf(allOf(ivy("org", "mod", artifact("mod"), "exact"), group("org")), allOf(ivy("org", "mod2", artifact("mod"), "exact"), group("org")))

        // for operations below the result can be further simplified, but it's not this class responsibility to do it
        anyOf(group("foo"), group("bar"))                                                                              | anyOf(group("foo"), group("baz"))                                                                                     | anyOf(group("foo"), allOf(group("bar"), group("baz")))
        anyOf(group("g1"), moduleIdSet(["a", "b"], ["a", "c"]))                                                        | anyOf(group("g1"), moduleIdSet(["a", "b"], ["a", "d"]))                                                               | anyOf(group("g1"), allOf(moduleIdSet(["a", "b"], ["a", "c"]), moduleIdSet(["a", "b"], ["a", "d"])))
    }

    def "intersection of #one with #other = #expected using normalizing factory"() {
        given:
        factory = new NormalizingExcludeFactory(factory)

        expect:
        factory.allOf(one, other) == expected
        factory.allOf(other, one) == expected

        where:
        one                                                                                                            | other                                                                                  | expected
        anyOf(group("foo"), group("bar"))                                                                              | anyOf(group("foo"), group("baz"))                                                      | group("foo")
        anyOf(group("g1"), moduleIdSet(["a", "b"], ["a", "c"]))                                                        | anyOf(group("g1"), moduleIdSet(["a", "b"], ["a", "d"]))                                | anyOf(group("g1"), moduleId("a", "b"))

        anyOf(ivy("org", "foo", artifact("foo"), "exact"), module("foo"))                                              | anyOf(ivy("org", "foo", artifact("foo"), "exact"), module("bar"))                      | ivy("org", "foo", artifact("foo"), "exact")
        anyOf(allOf(ivy("org", "foo", artifact("foo"), "exact"), module("foo")), group("bar"))                         | anyOf(allOf(ivy("org", "foo", artifact("foo"), "exact"), module("mod")), group("bar")) | group("bar")

        // should not cause stack overflow because components cannot be reduced
        anyOf(ivy("org", "mod", artifact("mod"), "exact"), ivy("org", "mod2", artifact("mod"), "exact"))               | group("org")                                                                           | allOf(anyOf(ivy("org", "mod", artifact("mod"), "exact"), ivy("org", "mod2", artifact("mod"), "exact")), group("org"))
        anyOf(ivy("org", "mod", artifact("mod"), "exact"), ivy("org", "mod2", artifact("mod"), "exact"), group("bar")) | group("org")                                                                           | anyOf(allOf(ivy("org", "mod", artifact("mod"), "exact"), group("org")), allOf(ivy("org", "mod2", artifact("mod"), "exact"), group("org")))

    }


}
