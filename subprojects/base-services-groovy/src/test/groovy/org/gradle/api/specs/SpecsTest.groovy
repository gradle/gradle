/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.specs

import spock.lang.Issue
import spock.lang.Specification

class SpecsTest extends Specification {

    def "can convert closures to specs"() {
        given:
        def spec = Specs.convertClosureToSpec {
            it > 10
        }

        expect:
        !spec.isSatisfiedBy(5)
        spec.isSatisfiedBy(15)
    }

    @Issue("GRADLE-2288")
    def "closure specs use groovy truth"() {
        def spec = Specs.convertClosureToSpec {
            it
        }

        expect:
        !spec.isSatisfiedBy("")
        spec.isSatisfiedBy([1,2,3])
    }

    def "negation of all is none"() {
        expect:
        Specs.negate(Specs.satisfyAll()) == Specs.satisfyNone()
    }

    def "negation of none is all"() {
        expect:
        Specs.negate(Specs.satisfyNone()) == Specs.satisfyAll()
    }

    def "negation of spec is !spec"() {
        def spec = Stub(Spec)

        expect:
        def negation = Specs.negate(spec)
        negation instanceof NotSpec
        negation.sourceSpec == spec
    }

    def "negation of !spec is spec"() {
        def spec = Stub(Spec)

        expect:
        Specs.negate(Specs.negate(spec)) == spec
    }

    def "intersection of no specs is all"() {
        expect:
        Specs.intersect() == Specs.satisfyAll()
        Specs.intersect([]) == Specs.satisfyAll()
    }

    def "intersection of a spec is that spec"() {
        def spec = Stub(Spec)

        expect:
        Specs.intersect(spec) == spec
        Specs.intersect([spec]) == spec
    }

    def "intersection of all spec is all"() {
        expect:
        Specs.intersect(Specs.satisfyAll()) == Specs.satisfyAll()
        Specs.intersect([Specs.satisfyAll()]) == Specs.satisfyAll()
        Specs.intersect(Specs.satisfyAll(), Specs.satisfyAll()) == Specs.satisfyAll()
        Specs.intersect([Specs.satisfyAll(), Specs.satisfyAll()]) == Specs.satisfyAll()
    }

    def "intersection of all spec and other specs is other specs"() {
        def spec1 = Stub(Spec)
        def spec2 = Stub(Spec)

        expect:
        Specs.intersect(spec1, Specs.satisfyAll(), spec2).specs == [spec1, spec2]
        Specs.intersect([spec1, Specs.satisfyAll(), spec2]).specs == [spec1, spec2]
        Specs.intersect(spec1, Specs.satisfyAll()) == spec1
        Specs.intersect([spec1, Specs.satisfyAll()]) == spec1
        Specs.intersect(spec1, Specs.satisfyAll(), Specs.satisfyAll()) == spec1
        Specs.intersect([spec1, Specs.satisfyAll(), Specs.satisfyAll()]) == spec1
    }

    def "intersection of multiple specs is AndSpec"() {
        def spec1 = Stub(Spec)
        def spec2 = Stub(Spec)

        expect:
        def intersect1 = Specs.intersect(spec1, spec2)
        intersect1 instanceof AndSpec
        intersect1.specs == [spec1, spec2]

        def intersect2 = Specs.intersect([spec1, spec2])
        intersect2 instanceof AndSpec
        intersect2.specs == [spec1, spec2]
    }

    def "intersection of nothing and other specs is nothing"() {
        def spec1 = Stub(Spec)
        def spec2 = Stub(Spec)

        expect:
        Specs.intersect(Specs.satisfyNone()) == Specs.satisfyNone()
        Specs.intersect(spec1, Specs.satisfyNone(), spec2) == Specs.satisfyNone()
        Specs.intersect([spec1, Specs.satisfyNone(), spec2]) == Specs.satisfyNone()
    }

    def "union of no specs is all"() {
        expect:
        Specs.union() == Specs.satisfyAll()
        Specs.union([]) == Specs.satisfyAll()
    }

    def "union of a spec is that spec"() {
        def spec = Stub(Spec)

        expect:
        Specs.union(spec) == spec
        Specs.union([spec]) == spec
    }

    def "union of multiple specs is OrSpec"() {
        def spec1 = Stub(Spec)
        def spec2 = Stub(Spec)

        expect:
        def union1 = Specs.union(spec1, spec2)
        union1 instanceof OrSpec
        union1.specs == [spec1, spec2]

        def union2 = Specs.union([spec1, spec2])
        union2 instanceof OrSpec
        union2.specs == [spec1, spec2]
    }

    def "union of none is none"() {
        expect:
        Specs.union(Specs.satisfyNone()) == Specs.satisfyNone()
        Specs.union([Specs.satisfyNone()]) == Specs.satisfyNone()
        Specs.union(Specs.satisfyNone(), Specs.satisfyNone()) == Specs.satisfyNone()
        Specs.union([Specs.satisfyNone(), Specs.satisfyNone()]) == Specs.satisfyNone()
    }

    def "union of all and other specs is all"() {
        def spec1 = Stub(Spec)
        def spec2 = Stub(Spec)

        expect:
        Specs.union(spec1, Specs.satisfyAll(), spec2) == Specs.satisfyAll()
        Specs.union([spec1, Specs.satisfyAll(), spec2]) == Specs.satisfyAll()
        Specs.union(Specs.satisfyAll()) == Specs.satisfyAll()
        Specs.union([Specs.satisfyAll()]) == Specs.satisfyAll()
    }

    def "union of none and other specs is other specs"() {
        def spec1 = Stub(Spec)
        def spec2 = Stub(Spec)

        expect:
        Specs.union(spec1, Specs.satisfyNone(), spec2).specs == [spec1, spec2]
        Specs.union([spec1, Specs.satisfyNone(), spec2]).specs == [spec1, spec2]
        Specs.union(spec1, Specs.satisfyNone()) == spec1
        Specs.union([spec1, Specs.satisfyNone()]) == spec1
        Specs.union(Specs.satisfyNone(), spec1, Specs.satisfyNone()) == spec1
        Specs.union([Specs.satisfyNone(), spec1, Specs.satisfyNone()]) == spec1
        Specs.union(Specs.satisfyNone(), spec1, Specs.satisfyNone(), spec2).specs == [spec1, spec2]
        Specs.union([Specs.satisfyNone(), spec1, Specs.satisfyNone(), spec2]).specs == [spec1, spec2]
    }
}
