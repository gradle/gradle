/*
 * Copyright 2007 the original author or authors.
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
 
package org.gradle.api.tasks.util

import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import static org.gradle.util.Matchers.*
import org.gradle.api.internal.file.RelativePath
import org.gradle.api.specs.Spec

/**
* @author Hans Dockter
*/
class PatternSetTest extends AbstractTestForPatternSet {
    PatternSet patternSet = new PatternSet()

    PatternSet getPatternSet() {
        patternSet
    }

    Class getPatternSetType() {
        PatternSet
    }

    @Test public void patternSetsAreEqualWhenIncludesAndExcludesAreTheEqual() {
        assertThat(new PatternSet(), strictlyEqual(new PatternSet()))
        assertThat(new PatternSet(includes: ['i']), strictlyEqual(new PatternSet(includes: ['i'])))
        assertThat(new PatternSet(excludes: ['e']), strictlyEqual(new PatternSet(excludes: ['e'])))
        assertThat(new PatternSet(includes: ['i'], excludes: ['e']), strictlyEqual(new PatternSet(includes: ['i'], excludes: ['e'])))

        assertThat(new PatternSet(), not(equalTo(new PatternSet(includes: ['i']))))
        assertThat(new PatternSet(), not(equalTo(new PatternSet(excludes: ['e']))))
        assertThat(new PatternSet(includes: ['i']), not(equalTo(new PatternSet(includes: ['other']))))
        assertThat(new PatternSet(excludes: ['e']), not(equalTo(new PatternSet(excludes: ['other']))))
    }
    
    @Test public void canCopyFromAnotherPatternSet() {
        PatternSet other = new PatternSet()
        other.include 'a', 'b'
        other.exclude 'c'
        patternSet.copyFrom(other)
        assertThat(patternSet.includes, equalTo(['a', 'b'] as Set))
        assertThat(patternSet.excludes, equalTo(['c'] as Set))
        assertThat(patternSet.includes, not(sameInstance(other.includes)))
        assertThat(patternSet.excludes, not(sameInstance(other.excludes)))
    }

    @Test public void createsSpecForEmptyPatternSet() {
        Spec<RelativePath> spec = patternSet.asSpec

        assertTrue(spec.isSatisfiedBy(new RelativePath(true, 'a')))
        assertTrue(spec.isSatisfiedBy(new RelativePath(true, 'b')))
    }

    @Test public void createsSpecForIncludeOnlyPatternSet() {
        patternSet.include '*a*'
        patternSet.include '*b*'
        Spec<RelativePath> spec = patternSet.asSpec

        assertTrue(spec.isSatisfiedBy(new RelativePath(true, 'a')))
        assertTrue(spec.isSatisfiedBy(new RelativePath(true, 'b')))
        assertFalse(spec.isSatisfiedBy(new RelativePath(true, 'c')))
    }

    @Test public void createsSpecForExcludeOnlyPatternSet() {
        patternSet.exclude '*b*'
        patternSet.exclude '*c*'
        Spec<RelativePath> spec = patternSet.asSpec

        assertTrue(spec.isSatisfiedBy(new RelativePath(true, 'a')))
        assertFalse(spec.isSatisfiedBy(new RelativePath(true, 'b')))
        assertFalse(spec.isSatisfiedBy(new RelativePath(true, 'c')))
    }

    @Test public void createsSpecForIncludeAndExcludePatternSet() {
        patternSet.include '*a*'
        patternSet.exclude '*b*'
        Spec<RelativePath> spec = patternSet.asSpec

        assertTrue(spec.isSatisfiedBy(new RelativePath(true, 'a')))
        assertFalse(spec.isSatisfiedBy(new RelativePath(true, 'ab')))
        assertFalse(spec.isSatisfiedBy(new RelativePath(true, 'ba')))
    }

    @Test public void isCaseSensitiveByDefault() {
        patternSet.include '*a*'
        patternSet.exclude '*b*'
        Spec<RelativePath> spec = patternSet.asSpec

        assertTrue(spec.isSatisfiedBy(new RelativePath(true, 'a')))
        assertFalse(spec.isSatisfiedBy(new RelativePath(true, 'A')))
        assertFalse(spec.isSatisfiedBy(new RelativePath(true, 'Ab')))
        assertTrue(spec.isSatisfiedBy(new RelativePath(true, 'aB')))
    }
    
    @Test public void createsSpecForCaseInsensitivePatternSet() {
        patternSet.include '*a*'
        patternSet.exclude '*b*'
        patternSet.caseSensitive = false
        Spec<RelativePath> spec = patternSet.asSpec

        assertTrue(spec.isSatisfiedBy(new RelativePath(true, 'A')))
        assertTrue(spec.isSatisfiedBy(new RelativePath(true, 'a')))
        assertFalse(spec.isSatisfiedBy(new RelativePath(true, 'AB')))
        assertFalse(spec.isSatisfiedBy(new RelativePath(true, 'bA')))
    }

    @Test public void createIntersectPatternSet() {
        patternSet.include '*a*'
        patternSet.exclude '*b*'
        PatternSet intersection = patternSet.intersect()
        intersection.include '*c*'
        intersection.exclude '*d*'
        Spec<RelativePath> spec = intersection.asSpec

        assertTrue(spec.isSatisfiedBy(new RelativePath(true, 'ac')))
        assertFalse(spec.isSatisfiedBy(new RelativePath(true, 'a')))
        assertFalse(spec.isSatisfiedBy(new RelativePath(true, 'c')))
        assertFalse(spec.isSatisfiedBy(new RelativePath(true, 'acb')))
        assertFalse(spec.isSatisfiedBy(new RelativePath(true, 'acd')))
    }
}
