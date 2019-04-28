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

import com.google.common.collect.ImmutableList
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.simple.DefaultExcludeFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.IvyArtifactName
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class NormalizingExcludeFactoryTest extends Specification {

    @Shared
    private ExcludeFactory delegate = new DefaultExcludeFactory()

    @Subject
    private NormalizingExcludeFactory factory = new NormalizingExcludeFactory(delegate)

    @Shared
    private DefaultIvyArtifactName artifactName = new DefaultIvyArtifactName("a", "b", "c")

    @Unroll("#left ∪ #right = #expected")
    def "union of two elements"() {
        expect:
        factory.anyOf(left, right) == expected

        and: "union is commutative"
        factory.anyOf(right, left) == expected

        where:
        left                               | right                | expected
        everything()                       | nothing()            | everything()
        everything()                       | everything()         | everything()
        nothing()                          | nothing()            | nothing()
        everything()                       | group("foo")         | everything()
        nothing()                          | group("foo")         | group("foo")
        //group("foo")                       | group("bar")         | anyOf(group("foo"), group("bar"))
        group("foo")                       | module("bar")        | anyOf(group("foo"), module("bar"))
        //anyOf(group("foo"), group("bar"))  | group("foo")         | anyOf(group("foo"), group("bar"))
        anyOf(group("foo"), module("bar")) | module("bar")        | anyOf(module("bar"), group("foo"))
    }

    @Unroll("#one ∪ #two ∪ #three = #expected")
    def "union of three elements"() {
        expect:
        [one, two, three].combinations().each { list ->
            assert factory.anyOf(list) == expected
        }

        where:
        one          | two          | three        | expected
        everything() | nothing()    | nothing()    | everything()
        everything() | everything() | everything() | everything()
        nothing()    | nothing()    | nothing()    | nothing()
        everything() | group("foo") | everything() | everything()
        //group("foo") | group("bar") | group("baz") | anyOf(group("foo"), group("bar"), group("baz"))
    }

    @Unroll("#left ∩ #right = #expected")
    def "intersection of two elements"() {
        expect:
        factory.allOf(left, right) == expected

        and: "intersection is commutative"
        factory.allOf(right, left) == expected

        where:
        left                                                 | right                                                                             | expected
        everything()                                         | nothing()                                                                         | nothing()
        everything()                                         | everything()                                                                      | everything()
        nothing()                                            | nothing()                                                                         | nothing()
        everything()                                         | group("foo")                                                                      | group("foo")
        nothing()                                            | group("foo")                                                                      | nothing()
        group("foo")                                         | group("foo")                                                                      | group("foo")
        allOf(group("foo"), group("foo2"))                   | module("bar")                                                                     | allOf(group("foo2"), group("foo"), module("bar"))
    }

    private ExcludeSpec nothing() {
        delegate.nothing()
    }

    private ExcludeSpec everything() {
        delegate.everything()
    }

    private ExcludeSpec group(String group) {
        delegate.group(group)
    }

    private ExcludeSpec module(String module) {
        delegate.module(module)
    }

    private ExcludeSpec module(String group, String name) {
        delegate.moduleId(DefaultModuleIdentifier.newId(group, name))
    }

    private ExcludeSpec anyOf(ExcludeSpec... specs) {
        delegate.anyOf(ImmutableList.copyOf(specs))
    }

    private ExcludeSpec allOf(ExcludeSpec... specs) {
        delegate.allOf(ImmutableList.copyOf(specs))
    }

    private ExcludeSpec ivy(String group, String module, IvyArtifactName artifact, String matcher) {
        delegate.ivyPatternExclude(
            DefaultModuleIdentifier.newId(group, module),
            artifact,
            matcher
        )
    }

    private static IvyArtifactName artifact(String name) {
        new DefaultIvyArtifactName(name, "jar", "jar")
    }
}
