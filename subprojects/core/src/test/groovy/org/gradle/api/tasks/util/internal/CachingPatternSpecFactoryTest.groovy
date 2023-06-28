/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.api.specs.AndSpec
import org.gradle.api.specs.NotSpec
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.PatternSet
import spock.lang.Specification

class CachingPatternSpecFactoryTest extends Specification {
    def "check that Spec<ReadOnlyFileTreeElement> instances added to include/exclude aren't cached"() {
        given:
        def patternSet = new PatternSet(new CachingPatternSpecFactory())
        boolean desiredResult = true
        def includeSpecClosure = { ReadOnlyFileTreeElement e -> desiredResult } as Spec
        patternSet.include(includeSpecClosure)
        def excludeSpecClosure = { ReadOnlyFileTreeElement e -> false } as Spec
        patternSet.exclude(excludeSpecClosure)
        def spec = patternSet.getAsSpec()
        def fileTreeElement = Stub(ReadOnlyFileTreeElement) {
            isFile() >> {
                true
            }
        }
        when:
        desiredResult = true
        then:
        spec.isSatisfiedBy(fileTreeElement)
        when:
        desiredResult = false
        then:
        !spec.isSatisfiedBy(fileTreeElement)
        when:
        desiredResult = true
        then:
        spec.isSatisfiedBy(fileTreeElement)
        when:
        desiredResult = false
        then:
        !spec.isSatisfiedBy(fileTreeElement)
        expect:
        spec.specs[0].is(includeSpecClosure)
        spec.specs[1].sourceSpec.specs[1].is(excludeSpecClosure)
    }

    def "check that patterns are cached"() {
        given:
        def patternSet = new PatternSet(new CachingPatternSpecFactory())
        patternSet.include("pattern")
        def spec = patternSet.getAsSpec()
        expect:
        spec instanceof AndSpec
        spec.specs.size() == 2
        spec.specs[0] instanceof CachingPatternSpecFactory.CachingSpec
        spec.specs[1] instanceof NotSpec
        spec.specs[1].sourceSpec instanceof CachingPatternSpecFactory.CachingSpec
    }
}
