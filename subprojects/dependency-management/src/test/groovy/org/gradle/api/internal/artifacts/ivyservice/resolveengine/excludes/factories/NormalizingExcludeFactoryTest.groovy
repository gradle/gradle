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

import org.gradle.internal.component.model.DefaultIvyArtifactName
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class NormalizingExcludeFactoryTest extends Specification implements ExcludeTestSupport {

    def setup() {
        factory = new NormalizingExcludeFactory(factory)
    }

    @Shared
    private DefaultIvyArtifactName artifactName = new DefaultIvyArtifactName("a", "b", "c")

    @Unroll("#left ∪ #right = #expected")
    def "union of two elements"() {
        expect:
        factory.anyOf(left, right) == expected

        and: "union is commutative"
        factory.anyOf(right, left) == expected

        where:
        left                                        | right                                       | expected
        everything()                                | nothing()                                   | everything()
        everything()                                | everything()                                | everything()
        nothing()                                   | nothing()                                   | nothing()
        everything()                                | group("foo")                                | everything()
        nothing()                                   | group("foo")                                | group("foo")
        group("foo")                                | group("bar")                                | groupSet("foo", "bar")
        group("foo")                                | module("bar")                               | anyOf(group("foo"), module("bar"))
        anyOf(group("foo"), group("bar"))           | group("foo")                                | groupSet("foo", "bar")
        anyOf(group("foo"), module("bar"))          | module("bar")                               | anyOf(module("bar"), group("foo"))
        moduleId("org", "a")                        | moduleId("org", "b")                        | moduleIdSet(["org", "a"], ["org", "b"])
        module("org")                               | module("org2")                              | moduleSet("org", "org2")
        groupSet("org", "org2")                     | groupSet("org3", "org4")                    | groupSet("org", "org2", "org3", "org4")
        moduleSet("mod", "mod2")                    | moduleSet("mod3", "mod4")                   | moduleSet("mod", "mod2", "mod3", "mod4")
        moduleIdSet(["org", "foo"], ["org", "bar"]) | moduleIdSet(["org", "baz"], ["org", "quz"]) | moduleIdSet(["org", "foo"], ["org", "bar"], ["org", "baz"], ["org", "quz"])
        module("mod")                               | moduleSet("m1", "m2")                       | moduleSet("m1", "m2", "mod")
        group("g1")                                 | groupSet("g2", "g3")                        | groupSet("g1", "g2", "g3")
        moduleId("g1", "m1")                        | moduleIdSet(["g2", "m2"], ["g3", "m3"])     | moduleIdSet(["g1", "m1"], ["g2", "m2"], ["g3", "m3"])
    }

    @Unroll("#one ∪ #two ∪ #three = #expected")
    def "union of three elements"() {
        expect:
        [one, two, three].combinations().each { list ->
            assert factory.anyOf(list as Set) == expected
        }

        where:
        one          | two          | three        | expected
        everything() | nothing()    | nothing()    | everything()
        everything() | everything() | everything() | everything()
        nothing()    | nothing()    | nothing()    | nothing()
        everything() | group("foo") | everything() | everything()
        group("foo") | group("bar") | group("baz") | groupSet("foo", "bar", "baz")
    }

    @Unroll("#left ∩ #right = #expected")
    def "intersection of two elements"() {
        expect:
        factory.allOf(left, right) == expected

        and: "intersection is commutative"
        factory.allOf(right, left) == expected

        where:
        left                               | right         | expected
        everything()                       | nothing()     | nothing()
        everything()                       | everything()  | everything()
        nothing()                          | nothing()     | nothing()
        everything()                       | group("foo")  | group("foo")
        nothing()                          | group("foo")  | nothing()
        group("foo")                       | group("foo")  | group("foo")
        allOf(group("foo"), group("foo2")) | module("bar") | nothing()
        allOf(group("foo"), module("bar")) | module("bar") | moduleId("foo", "bar")
    }

    @Unroll("#one ∩ #two ∩ #three = #expected")
    def "intersection of three elements"() {
        expect:
        [one, two, three].combinations().each { list ->
            assert factory.allOf(list as Set) == expected
        }

        where:
        one                                                   | two                                         | three                                                                      | expected
        everything()                                          | nothing()                                   | nothing()                                                                  | nothing()
        everything()                                          | everything()                                | everything()                                                               | everything()
        nothing()                                             | nothing()                                   | nothing()                                                                  | nothing()
        everything()                                          | group("foo")                                | everything()                                                               | group("foo")
        group("foo")                                          | group("bar")                                | group("baz")                                                               | nothing()
        group("foo")                                          | module("bar")                               | groupSet("foo", "bar")                                                     | moduleId("foo", "bar")
        moduleId("org", "foo")                                | ivy("org", "mod", artifact("mod"), "exact") | group("org")                                                               | allOf(moduleId("org", "foo"), ivy("org", "mod", artifact("mod"), "exact"))
        anyOf(moduleId("org", "foo"), moduleId("org", "bar")) | ivy("org", "mod", artifact("mod"), "exact") | anyOf(group("org"), module("bar"))                                         | allOf(moduleIdSet(["org", "foo"], ["org", "bar"]), ivy("org", "mod", artifact("mod"), "exact"))
        anyOf(moduleId("org", "foo"), moduleId("org", "bar")) | ivy("org", "mod", artifact("mod"), "exact") | anyOf(moduleId("org", "foo"), module("bar"))                               | allOf(moduleIdSet(["org", "foo"], ["org", "bar"]), ivy("org", "mod", artifact("mod"), "exact"))
        anyOf(moduleId("org", "foo"), moduleId("org", "bar")) | ivy("org", "mod", artifact("mod"), "exact") | anyOf(moduleId("org", "foo"), ivy("org", "mod", artifact("mod"), "exact")) | allOf(anyOf(moduleId("org", "foo"), moduleId("org", "bar")), ivy("org", "mod", artifact("mod"), "exact"))
    }
}
