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

import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.specs.Spec
import org.junit.Test
import spock.lang.Issue

import static org.gradle.util.Matchers.strictlyEqual
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

class PatternSetTest extends AbstractTestForPatternSet {
    PatternSet patternSet = new PatternSet()

    @Test void testConstructionFromMap() {
        Map map = [includes: [TEST_PATTERN_1], excludes: [TEST_PATTERN_2]]
        PatternFilterable patternSet = new PatternSet(map)
        assertThat(patternSet.includes, equalTo([TEST_PATTERN_1] as Set))
        assertThat(patternSet.excludes, equalTo([TEST_PATTERN_2] as Set))
    }

    @Test void patternSetsAreEqualWhenAllPropertiesAreEqual() {
        assertThat(new PatternSet(), strictlyEqual(new PatternSet()))
        assertThat(new PatternSet(caseSensitive: false), strictlyEqual(new PatternSet(caseSensitive: false)))
        assertThat(new PatternSet(includes: ['i']), strictlyEqual(new PatternSet(includes: ['i'])))
        assertThat(new PatternSet(excludes: ['e']), strictlyEqual(new PatternSet(excludes: ['e'])))
        assertThat(new PatternSet(includes: ['i'], excludes: ['e']), strictlyEqual(new PatternSet(includes: ['i'], excludes: ['e'])))

        assertThat(new PatternSet(), not(equalTo(new PatternSet(caseSensitive: false))))
        assertThat(new PatternSet(), not(equalTo(new PatternSet(includes: ['i']))))
        assertThat(new PatternSet(), not(equalTo(new PatternSet(excludes: ['e']))))
        assertThat(new PatternSet(includes: ['i']), not(equalTo(new PatternSet(includes: ['other']))))
        assertThat(new PatternSet(excludes: ['e']), not(equalTo(new PatternSet(excludes: ['other']))))
    }

    @Test void canCopyFromAnotherPatternSet() {
        PatternSet other = new PatternSet()
        other.include 'a', 'b'
        other.exclude 'c'
        other.include({true} as Spec)
        other.exclude({false} as Spec)
        patternSet.copyFrom(other)
        assertThat(patternSet.includes, equalTo(['a', 'b'] as Set))
        assertThat(patternSet.excludes, equalTo(['c'] as Set))
        assertThat(patternSet.includes, not(sameInstance(other.includes)))
        assertThat(patternSet.excludes, not(sameInstance(other.excludes)))
        assertThat(patternSet.includeSpecs, equalTo(other.includeSpecs))
        assertThat(patternSet.excludeSpecs, equalTo(other.excludeSpecs))
    }

    @Test void createsSpecForEmptyPatternSet() {
        Spec<FileTreeElement> spec = patternSet.asSpec

        assertTrue(spec.isSatisfiedBy(element(true, 'a')))
        assertTrue(spec.isSatisfiedBy(element(true, 'b')))
    }

    private FileTreeElement element(boolean isFile = true, String... elements) {
        [
                getRelativePath: { return new RelativePath(isFile, elements) },
                getFile: { return new File(elements.join('/')) }
        ] as FileTreeElement
    }

    @Test void createsSpecForIncludePatterns() {
        patternSet.include '*a*'
        patternSet.include '*b*'
        Spec<FileTreeElement> spec = patternSet.asSpec

        assertTrue(spec.isSatisfiedBy(element(true, 'a')))
        assertTrue(spec.isSatisfiedBy(element(true, 'b')))
        assertFalse(spec.isSatisfiedBy(element(true, 'c')))
    }

    @Test void createsSpecForExcludePatterns() {
        patternSet.exclude '*b*'
        patternSet.exclude '*c*'
        Spec<FileTreeElement> spec = patternSet.asSpec

        assertTrue(spec.isSatisfiedBy(element(true, 'a')))
        assertFalse(spec.isSatisfiedBy(element(true, 'b')))
        assertFalse(spec.isSatisfiedBy(element(true, 'c')))
    }

    @Test void createsSpecForIncludeAndExcludePatterns() {
        patternSet.include '*a*'
        patternSet.exclude '*b*'
        Spec<FileTreeElement> spec = patternSet.asSpec

        assertTrue(spec.isSatisfiedBy(element(true, 'a')))
        assertFalse(spec.isSatisfiedBy(element(true, 'ab')))
        assertFalse(spec.isSatisfiedBy(element(true, 'ba')))
        assertFalse(spec.isSatisfiedBy(element(true, 'c')))
        assertFalse(spec.isSatisfiedBy(element(true, 'b')))
    }

    @Test void createsSpecForIncludeSpecs() {
        patternSet.include({ FileTreeElement element -> element.file.name.contains('a') } as Spec)
        Spec<FileTreeElement> spec = patternSet.asSpec

        assertTrue(spec.isSatisfiedBy(element(true, 'a')))
        assertFalse(spec.isSatisfiedBy(element(true, 'b')))
    }

    @Test void createsSpecForExcludeSpecs() {
        patternSet.exclude({ FileTreeElement element -> element.file.name.contains('b') } as Spec)
        Spec<FileTreeElement> spec = patternSet.asSpec

        assertTrue(spec.isSatisfiedBy(element(true, 'a')))
        assertFalse(spec.isSatisfiedBy(element(true, 'b')))
    }

    @Test void createsSpecForIncludeAndExcludeSpecs() {
        patternSet.include({ FileTreeElement element -> element.file.name.contains('a') } as Spec)
        patternSet.exclude({ FileTreeElement element -> element.file.name.contains('b') } as Spec)
        Spec<FileTreeElement> spec = patternSet.asSpec

        assertTrue(spec.isSatisfiedBy(element(true, 'a')))
        assertFalse(spec.isSatisfiedBy(element(true, 'ab')))
        assertFalse(spec.isSatisfiedBy(element(true, 'b')))
        assertFalse(spec.isSatisfiedBy(element(true, 'c')))
    }

    @Test void createsSpecForIncludeClosure() {
        patternSet.include { FileTreeElement element -> element.file.name.contains('a') }
        Spec<FileTreeElement> spec = patternSet.asSpec

        assertTrue(spec.isSatisfiedBy(element(true, 'a')))
        assertFalse(spec.isSatisfiedBy(element(true, 'b')))
    }

    @Test void createsSpecForExcludeClosure() {
        patternSet.exclude { FileTreeElement element -> element.file.name.contains('b') }
        Spec<FileTreeElement> spec = patternSet.asSpec

        assertTrue(spec.isSatisfiedBy(element(true, 'a')))
        assertFalse(spec.isSatisfiedBy(element(true, 'b')))
    }

    @Test void createsSpecForIncludeAndExcludeClosures() {
        patternSet.include { FileTreeElement element -> element.file.name.contains('a') }
        patternSet.exclude { FileTreeElement element -> element.file.name.contains('b') }
        Spec<FileTreeElement> spec = patternSet.asSpec

        assertTrue(spec.isSatisfiedBy(element(true, 'a')))
        assertFalse(spec.isSatisfiedBy(element(true, 'ab')))
        assertFalse(spec.isSatisfiedBy(element(true, 'c')))
    }

    @Test void isCaseSensitiveByDefault() {
        patternSet.include '*a*'
        patternSet.exclude '*b*'
        Spec<FileTreeElement> spec = patternSet.asSpec

        assertTrue(spec.isSatisfiedBy(element(true, 'a')))
        assertFalse(spec.isSatisfiedBy(element(true, 'A')))
        assertFalse(spec.isSatisfiedBy(element(true, 'Ab')))
        assertTrue(spec.isSatisfiedBy(element(true, 'aB')))
    }

    @Test void createsSpecForCaseInsensitivePatternSet() {
        patternSet.include '*a*'
        patternSet.exclude '*b*'
        patternSet.caseSensitive = false
        Spec<FileTreeElement> spec = patternSet.asSpec

        assertTrue(spec.isSatisfiedBy(element(true, 'A')))
        assertTrue(spec.isSatisfiedBy(element(true, 'a')))
        assertFalse(spec.isSatisfiedBy(element(true, 'AB')))
        assertFalse(spec.isSatisfiedBy(element(true, 'bA')))
    }

    @Test void createIntersectPatternSet() {
        patternSet.include '*a*'
        patternSet.include { FileTreeElement element -> element.file.name.contains('1') }
        patternSet.exclude '*b*'
        patternSet.exclude { FileTreeElement element -> element.file.name.contains('2') }
        PatternSet intersection = patternSet.intersect()
        intersection.include '*c*'
        intersection.include { FileTreeElement element -> element.file.name.contains('3') }
        intersection.exclude '*d*'
        intersection.exclude { FileTreeElement element -> element.file.name.contains('4') }
        Spec<FileTreeElement> spec = intersection.asSpec

        assertTrue(spec.isSatisfiedBy(element(true, 'ac')))
        assertTrue(spec.isSatisfiedBy(element(true, '13')))
        assertFalse(spec.isSatisfiedBy(element(true, 'a')))
        assertFalse(spec.isSatisfiedBy(element(true, '1')))
        assertFalse(spec.isSatisfiedBy(element(true, 'c')))
        assertFalse(spec.isSatisfiedBy(element(true, '3')))
        assertFalse(spec.isSatisfiedBy(element(true, 'acb')))
        assertFalse(spec.isSatisfiedBy(element(true, 'acd')))
        assertFalse(spec.isSatisfiedBy(element(true, '132')))
        assertFalse(spec.isSatisfiedBy(element(true, '132')))
    }

    @Test void globalExcludes() {
        Spec<FileTreeElement> spec = patternSet.asSpec

        assertFalse(spec.isSatisfiedBy(element(false, '.svn')))
        assertFalse(spec.isSatisfiedBy(element(true, '.svn', 'abc')))
        assertFalse(spec.isSatisfiedBy(element(false, 'a', 'b', '.svn')))
        assertFalse(spec.isSatisfiedBy(element(true, 'a', 'b', '.svn', 'c')))
    }

    @Issue("GRADLE-2566")
    @Test void canUseGStringsAsIncludes() {
        def a = "a*"
        def b = "b*"

        patternSet.includes = ["$a"]
        patternSet.include("$b")

        Spec<FileTreeElement> spec = patternSet.asSpec

        assertTrue(spec.isSatisfiedBy(element("aaa")))
        assertTrue(spec.isSatisfiedBy(element("bbb")))
        assertFalse(spec.isSatisfiedBy(element("ccc")))
    }

    @Issue("GRADLE-2566")
    @Test void canUseGStringsAsExcludes() {
        def a = "a"
        def b = "b"

        patternSet.excludes = ["${a}*"]
        patternSet.exclude("${b}*")

        Spec<FileTreeElement> spec = patternSet.asSpec

        assertFalse(spec.isSatisfiedBy(element("aaa")))
        assertFalse(spec.isSatisfiedBy(element("bbb")))
        assertTrue(spec.isSatisfiedBy(element("ccc")))
    }
}
