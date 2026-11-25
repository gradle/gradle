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

package org.gradle.api.internal.provider

import org.gradle.api.file.FileTreeElement
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.FilePatterns
import org.gradle.api.tasks.util.PatternSet
import spock.lang.Specification

class LazyBackedPatternSetTest extends Specification {

    def property = new DefaultProperty<FilePatterns>(PropertyHost.NO_OP, FilePatterns).convention(FilePatterns.empty())
    def patterns = new LazyBackedPatternSet(new PatternSet(), property)
    def plain = new PatternSet()

    def "eager changes match a plain pattern set"() {
        Spec<FileTreeElement> spec = { true } as Spec

        when:
        [patterns, plain].each {
            it.include("a", "b")
            it.include(["c"])
            it.exclude("d")
            it.exclude(["${'e'}"])
            it.include(spec)
            it.exclude(spec)
            it.setIncludes(["x", "a"])
            it.caseSensitive = false
        }

        then:
        patterns == plain
        plain == patterns
        patterns.hashCode() == plain.hashCode()
        patterns.includes == ["x", "a"] as Set
        patterns.excludes == ["d", "e"] as Set
        patterns.includeSpecs == [spec] as Set
        patterns.excludeSpecs == [spec] as Set
        !patterns.caseSensitive
        !patterns.empty
        property.get().includes == ["x", "a"] as Set

        when:
        patterns.exclude { false }

        then:
        patterns.excludeSpecs.size() == 2
    }

    def "a plain value is changed in place, not through a chain of providers"() {
        when:
        50.times { patterns.exclude("p$it") }

        then:
        !patterns.boundLazily
        property.get().excludes.size() == 50
    }

    def "eager changes on a lazy binding do not evaluate it"() {
        def evaluations = 0
        property.set(new DefaultProvider<FilePatterns>({ evaluations++; FilePatterns.empty().include("a") }))

        when:
        patterns.exclude("b")
        patterns.include("c")
        patterns.excludes.add("d")
        patterns.includes.remove("a")
        patterns.excludeSpecs.clear()
        patterns.setExcludes(["e"])
        patterns.caseSensitive = false

        then:
        evaluations == 0
        patterns.boundLazily

        when:
        def value = property.get()

        then:
        value.includes == ["c"] as Set
        value.excludes == ["e"] as Set
        !value.caseSensitive
    }

    def "an eager change keeps the convention"() {
        property.convention(FilePatterns.empty().include("a"))

        when:
        patterns.exclude("b")

        then:
        property.get().includes == ["a"] as Set
        property.get().excludes == ["b"] as Set
    }

    def "views write through"() {
        when:
        patterns.includes.add("a")
        patterns.includes.addAll(["b", "c"])
        patterns.includes.remove("b")
        def iterator = patterns.includes.iterator()
        iterator.next()
        iterator.remove()
        patterns.excludes.addAll(["x", "y", "z"])
        patterns.excludes.retainAll(["y"])

        then:
        patterns.includes == ["c"] as Set
        patterns.excludes == ["y"] as Set
        patterns.includes.contains("c")
        patterns.includes.toString() == "[c]"
    }

    def "copies from and into a plain pattern set"() {
        patterns.include("a").exclude("b")

        when:
        def copy = new PatternSet().copyFrom(patterns)

        then:
        copy.includes == ["a"] as Set
        copy.excludes == ["b"] as Set
        copy == patterns

        when:
        property.set(new DefaultProvider<FilePatterns>({ FilePatterns.empty().include("lazy") }))
        patterns.copyFrom(new PatternSet().include("c"))

        then:
        !patterns.boundLazily
        patterns.includes == ["c"] as Set
        patterns.excludes.empty
    }

    def "intersect sees later changes"() {
        patterns.include("a")

        when:
        def intersection = patterns.intersect()
        patterns.include("b")

        then:
        intersection.other.is(patterns)
        intersection.other.includes == ["a", "b"] as Set
    }
}
