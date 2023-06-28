/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.tasks.util.internal

import org.gradle.api.file.ReadOnlyFileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.util.PatternSet
import spock.lang.Specification

class PatternSpecFactoryTest extends Specification {
    def factory = new PatternSpecFactory()

    def "creates spec for empty patterns"() {
        def source = new PatternSet()

        expect:
        def spec = factory.createSpec(source)
        spec.isSatisfiedBy(element("a.java"))
        spec.isSatisfiedBy(element("/a/b/c.java"))

        // Default excludes
        !spec.isSatisfiedBy(element("/.git/refs"))
    }

    def "creates spec for include patterns"() {
        def source = new PatternSet()
        source.include("a/b/**", "**/c/d/*.java")

        expect:
        def spec = factory.createSpec(source)
        spec.isSatisfiedBy(element("a/b/c.groovy"))
        spec.isSatisfiedBy(element("1/c/d/e.java"))

        !spec.isSatisfiedBy(element("c/a/b/d.java"))
        !spec.isSatisfiedBy(element("a/c/d.java"))
        !spec.isSatisfiedBy(element("a/c/d/e.groovy"))

        // Default excludes
        !spec.isSatisfiedBy(element("/.git/refs"))
    }

    def "creates spec for exclude patterns"() {
        def source = new PatternSet()
        source.exclude("a/b/**", "**/c/d/*.java")

        expect:
        def spec = factory.createSpec(source)
        spec.isSatisfiedBy(element("a/c/d.java"))
        spec.isSatisfiedBy(element("c/a/b/d.java"))
        spec.isSatisfiedBy(element("a/c/d/d.groovy"))

        !spec.isSatisfiedBy(element("a/b/c.groovy"))
        !spec.isSatisfiedBy(element("1/c/d/e.java"))

        // Default excludes
        !spec.isSatisfiedBy(element("/.git/refs"))
    }

    ReadOnlyFileTreeElement element(String path) {
        return Stub(ReadOnlyFileTreeElement) {
            getRelativePath() >> new RelativePath(true, path.split("/"))
        }
    }
}
