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

import org.apache.tools.ant.DirectoryScanner
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.specs.Spec
import org.junit.After
import org.junit.Test
import spock.lang.Issue

import static org.gradle.util.Matchers.strictlyEqual
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

class PatternSetTest extends AbstractTestForPatternSet {
    PatternSet patternSet = new PatternSet()

    @After
    void resetDefaultExcludes() {
        DirectoryScanner.resetDefaultExcludes()
    }

    @Test
    void testConstructionFromMap() {
        Map map = [includes: [TEST_PATTERN_1], excludes: [TEST_PATTERN_2]]
        PatternFilterable patternSet = new PatternSet(map)
        assertThat(patternSet.includes, equalTo([TEST_PATTERN_1] as Set))
        assertThat(patternSet.excludes, equalTo([TEST_PATTERN_2] as Set))
    }

    @Test
    void patternSetsAreEqualWhenAllPropertiesAreEqual() {
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

    @Test
    void canCopyFromAnotherPatternSet() {
        PatternSet other = new PatternSet()
        other.include 'a', 'b'
        other.exclude 'c'
        other.include({ true } as Spec)
        other.exclude({ false } as Spec)
        patternSet.copyFrom(other)
        assertThat(patternSet.includes, equalTo(['a', 'b'] as Set))
        assertThat(patternSet.excludes, equalTo(['c'] as Set))
        assertThat(patternSet.includes, not(sameInstance(other.includes)))
        assertThat(patternSet.excludes, not(sameInstance(other.excludes)))
        assertThat(patternSet.includeSpecs, equalTo(other.includeSpecs))
        assertThat(patternSet.excludeSpecs, equalTo(other.excludeSpecs))
    }

    @Test
    void createsSpecForEmptyPatternSet() {
        included file('a')
        included file('b')
    }

    @Test
    void usesDefaultGlobalExcludes() {
        excluded dir('.svn')
        excluded file('.svn', 'abc')
        excluded dir('a', 'b', '.svn')
        excluded file('a', 'b', '.svn', 'c')
        excluded file('foo', '.DS_Store')
    }

    @Test
    void takesGlobalExcludesFromAnt() {
        DirectoryScanner.defaultExcludes.each {
            DirectoryScanner.removeDefaultExclude(it)
        }
        included dir('.svn')
        included file('.svn', 'abc')
        included file('foo', '.DS_Store')

        DirectoryScanner.addDefaultExclude('*X*')

        excluded file('X')
    }

    @Test
    void createsSpecForIncludePatterns() {
        patternSet.include '*a*'
        patternSet.include '*b*'

        included file('a')
        included file('b')
        excluded file('c')
    }

    @Test
    void createsSpecForExcludePatterns() {
        patternSet.exclude '*b*'
        patternSet.exclude '*c*'

        included file('a')
        excluded file('b')
        excluded file('c')
    }

    @Test
    void createsSpecForIncludeAndExcludePatterns() {
        patternSet.include '*a*'
        patternSet.exclude '*b*'

        included file('a')
        excluded file('ab')
        excluded file('ba')
        excluded file('c')
        excluded file('b')
    }

    @Test
    void createsSpecForIncludeSpecs() {
        patternSet.include({ FileTreeElement element -> element.file.name.contains('a') } as Spec)

        included file('a')
        excluded file('b')
    }

    @Test
    void createsSpecForExcludeSpecs() {
        patternSet.exclude({ FileTreeElement element -> element.file.name.contains('b') } as Spec)

        included file('a')
        excluded file('b')
    }

    @Test
    void createsSpecForIncludeAndExcludeSpecs() {
        patternSet.include({ FileTreeElement element -> element.file.name.contains('a') } as Spec)
        patternSet.exclude({ FileTreeElement element -> element.file.name.contains('b') } as Spec)

        included file('a')
        excluded file('ab')
        excluded file('b')
        excluded file('c')
    }

    @Test
    void createsSpecForIncludeClosure() {
        patternSet.include { FileTreeElement element -> element.file.name.contains('a') }

        included file('a')
        excluded file('b')
    }

    @Test
    void createsSpecForExcludeClosure() {
        patternSet.exclude { FileTreeElement element -> element.file.name.contains('b') }

        included file('a')
        excluded file('b')
    }

    @Test
    void createsSpecForIncludeAndExcludeClosures() {
        patternSet.include { FileTreeElement element -> element.file.name.contains('a') }
        patternSet.exclude { FileTreeElement element -> element.file.name.contains('b') }

        included file('a')
        excluded file('ab')
        excluded file('c')
    }

    @Test
    void isCaseSensitiveByDefault() {
        patternSet.include '*a*'
        patternSet.exclude '*b*'

        included file('a')
        excluded file('A')
        excluded file('Ab')
        included file('aB')
    }

    @Test
    void createsSpecForCaseInsensitivePatternSet() {
        patternSet.include '*a*'
        patternSet.exclude '*b*'
        patternSet.caseSensitive = false

        included file('A')
        included file('a')
        excluded file('AB')
        excluded file('bA')
    }

    @Test
    void createIntersectPatternSet() {
        PatternSet basePatternSet = new PatternSet()
        basePatternSet.include '*a*'
        basePatternSet.include { FileTreeElement element -> element.file.name.contains('1') }
        basePatternSet.exclude '*b*'
        basePatternSet.exclude { FileTreeElement element -> element.file.name.contains('2') }

        patternSet = basePatternSet.intersect()
        patternSet.include '*c*'
        patternSet.include { FileTreeElement element -> element.file.name.contains('3') }
        patternSet.exclude '*d*'
        patternSet.exclude { FileTreeElement element -> element.file.name.contains('4') }

        included file('ac')
        included file('13')
        excluded file('a')
        excluded file('1')
        excluded file('c')
        excluded file('3')
        excluded file('acb')
        excluded file('acd')
        excluded file('132')
        excluded file('132')

        patternSet = new PatternSet().copyFrom(patternSet)
        included file('ac')
        included file('13')
        excluded file('a')
        excluded file('1')
        excluded file('c')
        excluded file('3')
        excluded file('acb')
        excluded file('acd')
        excluded file('132')
        excluded file('132')
    }

    @Test
    void testIntersectPatternSetEqualsAndHashCode() {
        PatternSet basePatternSet = new PatternSet()
        basePatternSet.include '*a*'
        basePatternSet.exclude '*b*'

        patternSet = basePatternSet.intersect()
        patternSet.include '*a*'
        patternSet.exclude '*b*'

        assert patternSet.hashCode() != basePatternSet.hashCode()
        assert !patternSet.equals(basePatternSet)
    }

    @Issue("GRADLE-2566")
    @Test
    void canUseGStringsAsIncludes() {
        def a = "a*"
        def b = "b*"

        patternSet.includes = ["$a"]
        patternSet.include("$b")

        included file("aaa")
        included file("bbb")
        excluded file("ccc")
    }

    @Issue("GRADLE-2566")
    @Test
    void canUseGStringsAsExcludes() {
        def a = "a"
        def b = "b"

        patternSet.excludes = ["${a}*"]
        patternSet.exclude("${b}*")

        excluded file("aaa")
        excluded file("bbb")
        included file("ccc")
    }

    @Test
    void supportIsEmptyMethod() {
        assert patternSet.isEmpty()
        assert patternSet.intersect().isEmpty()

        patternSet = new PatternSet()
        patternSet.include { false }
        assert !patternSet.isEmpty()
        assert !patternSet.intersect().isEmpty()

        patternSet = new PatternSet()
        patternSet.include("*.txt")
        assert !patternSet.isEmpty()
        assert !patternSet.intersect().isEmpty()

        patternSet = new PatternSet()
        patternSet.exclude { false }
        assert !patternSet.isEmpty()
        assert !patternSet.intersect().isEmpty()

        patternSet = new PatternSet()
        patternSet.exclude("*.txt")
        assert !patternSet.isEmpty()
        assert !patternSet.intersect().isEmpty()
    }

    void included(FileTreeElement file) {
        assertTrue(patternSet.asSpec.isSatisfiedBy(file))
    }

    void excluded(FileTreeElement file) {
        assertFalse(patternSet.asSpec.isSatisfiedBy(file))
    }

    private FileTreeElement element(boolean isFile, String... elements) {
        [
            getRelativePath: { return new RelativePath(isFile, elements) },
            getFile        : { return new File(elements.join('/')) }
        ] as FileTreeElement
    }

    private FileTreeElement file(String... elements) {
        element(true, elements)
    }

    private FileTreeElement dir(String... elements) {
        element(false, elements)
    }
}
