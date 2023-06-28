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

import groovy.transform.CompileStatic
import org.apache.tools.ant.DirectoryScanner
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.file.ReadOnlyFileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.internal.PatternSpecFactory
import spock.lang.Issue

import static org.gradle.util.Matchers.strictlyEquals
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.MatcherAssert.assertThat

class PatternSetTest extends AbstractTestForPatternSet {
    PatternSet patternSet = new PatternSet()

    def cleanup() {
        DirectoryScanner.resetDefaultExcludes()
        updateSettingsDefaults()
    }

    def testConstructionFromMap() {
        Map map = [includes: [TEST_PATTERN_1], excludes: [TEST_PATTERN_2]]
        PatternFilterable patternSet = new PatternSet(map)

        expect:
        patternSet.includes == [TEST_PATTERN_1] as Set
        assertThat(patternSet.excludes, equalTo([TEST_PATTERN_2] as Set))
    }

    def patternSetsAreEqualWhenAllPropertiesAreEqual() {
        expect:
        strictlyEquals(new PatternSet(), new PatternSet())
        strictlyEquals(new PatternSet(caseSensitive: false), new PatternSet(caseSensitive: false))
        strictlyEquals(new PatternSet(includes: ['i']), new PatternSet(includes: ['i']))
        strictlyEquals(new PatternSet(excludes: ['e']), new PatternSet(excludes: ['e']))
        strictlyEquals(new PatternSet(includes: ['i'], excludes: ['e']), new PatternSet(includes: ['i'], excludes: ['e']))

        new PatternSet() != new PatternSet(caseSensitive: false)
        new PatternSet() != new PatternSet(includes: ['i'])
        new PatternSet() != new PatternSet(excludes: ['e'])
        new PatternSet(includes: ['i']) != new PatternSet(includes: ['other'])
        new PatternSet(excludes: ['e']) != new PatternSet(excludes: ['other'])
    }

    def canCopyFromAnotherPatternSet() {
        PatternSet other = new PatternSet()
        other.include 'a', 'b'
        other.exclude 'c'
        other.include({ true } as Spec)
        other.exclude({ false } as Spec)

        when:
        patternSet.copyFrom(other)

        then:
        patternSet.includes == ['a', 'b'] as Set
        patternSet.excludes == ['c'] as Set
        !patternSet.includes.is(other.includes)
        !patternSet.excludes.is(other.excludes)
        patternSet.includeSpecs == other.includeSpecs
        patternSet.excludeSpecs == other.excludeSpecs
    }

    def createsSpecForEmptyPatternSet() {
        expect:
        included file('a')
        included file('b')
    }

    def usesDefaultGlobalExcludes() {
        expect:
        excluded dir('.svn')
        excluded file('.svn', 'abc')
        excluded dir('a', 'b', '.svn')
        excluded file('a', 'b', '.svn', 'c')
        excluded file('foo', '.DS_Store')
    }

    def takesGlobalExcludesFromAnt() {
        when:
        DirectoryScanner.defaultExcludes.each {
            DirectoryScanner.removeDefaultExclude(it)
        }
        updateSettingsDefaults()

        then:
        included dir('.svn')
        included file('.svn', 'abc')
        included file('foo', '.DS_Store')

        when:
        DirectoryScanner.addDefaultExclude('*X*')
        updateSettingsDefaults()

        then:
        excluded file('X')
    }

    def "fails if default excludes are updated without changing the settings defaults"() {
        given:
        def previousExcludes = (DirectoryScanner.defaultExcludes as List).sort()
        DirectoryScanner.defaultExcludes.each {
            DirectoryScanner.removeDefaultExclude(it)
        }

        when:
        included dir('.svn')

        then:
        InvalidUserCodeException ex = thrown()

        and:
        ex.message == "Cannot change default excludes during the build. They were changed from ${previousExcludes} to []. Configure default excludes in the settings script instead."
    }

    def createsSpecForIncludePatterns() {
        when:
        patternSet.include '*a*'
        patternSet.include '*b*'
        then:
        included file('a')
        included file('b')
        excluded file('c')
    }

    def createsSpecForExcludePatterns() {
        when:
        patternSet.exclude '*b*'
        patternSet.exclude '*c*'
        then:
        included file('a')
        excluded file('b')
        excluded file('c')
    }

    def createsSpecForIncludeAndExcludePatterns() {
        when:
        patternSet.include '*a*'
        patternSet.exclude '*b*'
        then:
        included file('a')
        excluded file('ab')
        excluded file('ba')
        excluded file('c')
        excluded file('b')
    }

    def createsSpecForIncludeSpecs() {
        when:
        patternSet.include({ ReadOnlyFileTreeElement element -> element.name.contains('a') } as Spec)
        then:
        included file('a')
        excluded file('b')
    }

    def createsSpecForExcludeSpecs() {
        when:
        patternSet.exclude({ ReadOnlyFileTreeElement element -> element.name.contains('b') } as Spec)
        then:
        included file('a')
        excluded file('b')
    }

    def createsSpecForIncludeAndExcludeSpecs() {
        when:
        patternSet.include({ ReadOnlyFileTreeElement element -> element.name.contains('a') } as Spec)
        patternSet.exclude({ ReadOnlyFileTreeElement element -> element.name.contains('b') } as Spec)
        then:
        included file('a')
        excluded file('ab')
        excluded file('b')
        excluded file('c')
    }

    def createsSpecForIncludeClosure() {
        when:
        patternSet.include { ReadOnlyFileTreeElement element -> element.name.contains('a') }
        then:
        included file('a')
        excluded file('b')
    }

    def createsSpecForExcludeClosure() {
        when:
        patternSet.exclude { ReadOnlyFileTreeElement element -> element.name.contains('b') }
        then:
        included file('a')
        excluded file('b')
    }

    def createsSpecForIncludeAndExcludeClosures() {
        when:
        patternSet.include { ReadOnlyFileTreeElement element -> element.name.contains('a') }
        patternSet.exclude { ReadOnlyFileTreeElement element -> element.name.contains('b') }
        then:
        included file('a')
        excluded file('ab')
        excluded file('c')
    }

    def isCaseSensitiveByDefault() {
        when:
        patternSet.include '*a*'
        patternSet.exclude '*b*'
        then:
        included file('a')
        excluded file('A')
        excluded file('Ab')
        included file('aB')
    }

    def createsSpecForCaseInsensitivePatternSet() {
        when:
        patternSet.include '*a*'
        patternSet.exclude '*b*'
        patternSet.caseSensitive = false
        then:
        included file('A')
        included file('a')
        excluded file('AB')
        excluded file('bA')
    }

    def createIntersectPatternSet() {
        when:
        PatternSet basePatternSet = new PatternSet()
        basePatternSet.include '*a*'
        basePatternSet.include { ReadOnlyFileTreeElement element -> element.name.contains('1') }
        basePatternSet.exclude '*b*'
        basePatternSet.exclude { ReadOnlyFileTreeElement element -> element.name.contains('2') }

        patternSet = basePatternSet.intersect()
        patternSet.include '*c*'
        patternSet.include { ReadOnlyFileTreeElement element -> element.name.contains('3') }
        patternSet.exclude '*d*'
        patternSet.exclude { ReadOnlyFileTreeElement element -> element.name.contains('4') }

        then:
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

        when:
        patternSet = new PatternSet().copyFrom(patternSet)
        then:
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

    def testIntersectPatternSetEqualsAndHashCode() {
        when:
        PatternSet basePatternSet = new PatternSet()
        basePatternSet.include '*a*'
        basePatternSet.exclude '*b*'

        patternSet = basePatternSet.intersect()
        patternSet.include '*a*'
        patternSet.exclude '*b*'

        then:
        patternSet.hashCode() != basePatternSet.hashCode()
        patternSet != basePatternSet
    }

    @Issue("GRADLE-2566")
    def canUseGStringsAsIncludes() {
        def a = "a*"
        def b = "b*"

        patternSet.includes = ["$a"]
        patternSet.include("$b")

        expect:
        included file("aaa")
        included file("bbb")
        excluded file("ccc")
    }

    @Issue("GRADLE-2566")
    def canUseGStringsAsExcludes() {
        def a = "a"
        def b = "b"

        patternSet.excludes = ["${a}*"]
        patternSet.exclude("${b}*")

        expect:
        excluded file("aaa")
        excluded file("bbb")
        included file("ccc")
    }

    def supportIsEmptyMethod() {
        expect:
        patternSet.isEmpty()
        patternSet.intersect().isEmpty()

        when:
        patternSet = new PatternSet()
        patternSet.include { false }
        then:
        !patternSet.isEmpty()
        !patternSet.intersect().isEmpty()

        when:
        patternSet = new PatternSet()
        patternSet.include("*.txt")
        then:
        !patternSet.isEmpty()
        !patternSet.intersect().isEmpty()

        when:
        patternSet = new PatternSet()
        patternSet.exclude { false }
        then:
        !patternSet.isEmpty()
        !patternSet.intersect().isEmpty()

        when:
        patternSet = new PatternSet()
        patternSet.exclude("*.txt")
        then:
        !patternSet.isEmpty()
        !patternSet.intersect().isEmpty()
    }

    boolean included(ReadOnlyFileTreeElement file) {
        patternSet.asSpec.isSatisfiedBy(file)
    }

    boolean excluded(ReadOnlyFileTreeElement file) {
        !patternSet.asSpec.isSatisfiedBy(file)
    }

    private static ReadOnlyFileTreeElement element(boolean isFile, String... elements) {
        File file = new File(elements.join('/'))
        [
            getRelativePath: { return new RelativePath(isFile, elements) },
            getName: { return file.name }
        ] as ReadOnlyFileTreeElement
    }

    private static ReadOnlyFileTreeElement file(String... elements) {
        element(true, elements)
    }

    private static ReadOnlyFileTreeElement dir(String... elements) {
        element(false, elements)
    }

    @CompileStatic
    private static void updateSettingsDefaults() {
        PatternSpecFactory.INSTANCE.setDefaultExcludesFromSettings(DirectoryScanner.getDefaultExcludes())
    }
}
