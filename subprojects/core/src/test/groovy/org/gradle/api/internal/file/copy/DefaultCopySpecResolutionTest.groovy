/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.file.copy

import org.gradle.api.Action
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.specs.Spec
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.testing.internal.util.Specification

class DefaultCopySpecResolutionTest extends Specification implements CopySpecTestSpec {

    def fileResolver = Mock(FileResolver) {
        getPatternSetFactory() >> TestFiles.getPatternSetFactory()
    }
    def instantiator = DirectInstantiator.INSTANCE
    final CopySpecInternal spec = new DefaultCopySpec(fileResolver, instantiator)

    def "spec has root path as destination by default"() {
        expect:
        resolvedSpec().getDestPath() == RelativePath.EMPTY_ROOT
    }

    def "child resolves using parent destination path as default"() {
        given:
        spec.into 'parent'
        spec.addChild()

        expect:
        resolvedSpec().destPath == relativeDirectory('parent')
        resolvedChild().destPath == relativeDirectory('parent')
    }

    def "child destination path is resolved ads nested within parent"() {
        given:
        spec.into 'parent'
        spec.addChild().into 'child'

        expect:
        resolvedSpec().destPath == relativeDirectory('parent')
        resolvedChild().destPath == relativeDirectory('parent', 'child')
    }

    def "child uses parent patterns as default"() {
        given:
        Spec specInclude = Mock(Spec)
        Spec specExclude = Mock(Spec)

        spec.include('parent-include')
        spec.exclude('parent-exclude')
        spec.include(specInclude)
        spec.exclude(specExclude)

        spec.addChild()

        when:
        def patterns = resolvedChild().patternSet
        then:
        patterns.includes == (['parent-include'] as Set)
        patterns.excludes == (['parent-exclude'] as Set)
        patterns.includeSpecs == ([specInclude] as Set)
        patterns.excludeSpecs == ([specExclude] as Set)
    }

    def "child uses patterns from parent"() {
        given:
        Spec specInclude = Mock(Spec)
        Spec specExclude = Mock(Spec)

        spec.include('parent-include')
        spec.exclude('parent-exclude')
        spec.include(specInclude)
        spec.exclude(specExclude)

        Spec childInclude = Mock(Spec)
        Spec childExclude = Mock(Spec)

        CopySpec child = spec.addChild()
        child.include('child-include')
        child.exclude('child-exclude')
        child.include(childInclude)
        child.exclude(childExclude)

        when:
        def patterns = resolvedChild().patternSet
        then:
        patterns.includes == (['parent-include', 'child-include'] as Set)
        patterns.excludes == (['parent-exclude', 'child-exclude'] as Set)
        patterns.includeSpecs == ([specInclude, childInclude] as Set)
        patterns.excludeSpecs == ([specExclude, childExclude] as Set)
    }

    def "resolves source using own source filtered by pattern set"() {
        //Does not get source from root
        spec.from 'x'
        spec.from 'y'

        CopySpec child = spec.addChild()
        child.from 'a'
        child.from 'b'
        child.exclude("ex")

        def filteredTree = Mock(FileTreeInternal)
        def tree = Mock(FileTreeInternal)

        when:
        def childSource = resolvedChild().source

        then:
        childSource.is(filteredTree)
        1 * fileResolver.resolveFilesAsTree([['a', 'b'] as Set] as Object[]) >> tree
        1 * tree.matching(_) >> filteredTree
    }

    def "duplicates Strategy defaults to include"() {
        given:
        spec.addChild()

        expect:
        resolvedChild().duplicatesStrategy == DuplicatesStrategy.INCLUDE
    }

    def "inherits duplicate strategy from parent"() {
        given:
        def child = spec.addChild()

        when:
        spec.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        then:
        resolvedChild().duplicatesStrategy == DuplicatesStrategy.EXCLUDE

        when:
        child.duplicatesStrategy = DuplicatesStrategy.INCLUDE
        then:
        resolvedChild().duplicatesStrategy == DuplicatesStrategy.INCLUDE
    }

    def "case sensitive flag defaults to true"() {
        given:
        spec.addChild()

        expect:
        resolvedChild().caseSensitive
        resolvedChild().patternSet.caseSensitive
    }

    def "child uses case sensitive flag from parent as default"() {
        given:
        def child = spec.addChild()

        when:
        spec.caseSensitive = false
        then:
        !resolvedChild().caseSensitive
        !resolvedChild().patternSet.caseSensitive

        when:
        child.caseSensitive = true
        then:
        resolvedChild().caseSensitive
        resolvedChild().patternSet.caseSensitive
    }

    def "include empty dirs flag defaults to true"() {
        given:
        spec.addChild()

        expect:
        resolvedChild().includeEmptyDirs
    }

    def "child uses incldue empty dirs flag from parent as default"() {
        given:
        def child = spec.addChild()

        when:
        spec.includeEmptyDirs = false
        then:
        !resolvedChild().includeEmptyDirs

        when:
        child.includeEmptyDirs = true
        then:
        resolvedChild().includeEmptyDirs
    }

    def "inherits actions from parent"() {
        given:
        def parentAction = Mock(Action)
        spec.eachFile parentAction

        def childAction = Mock(Action)
        def child = spec.addChild()
        child.eachFile childAction

        expect:
        resolvedChild().copyActions as List == [parentAction, childAction]
    }

    def "has no permissions by default"() {
        given:
        spec.addChild()

        expect:
        resolvedChild().fileMode == null
        resolvedChild().dirMode == null
    }

    def "inherits permissions from parent"() {
        given:
        def child = spec.addChild()

        when:
        spec.fileMode = 0x1
        spec.dirMode = 0x2
        then:
        resolvedChild().fileMode == 0x1
        resolvedChild().dirMode == 0x2

        when:
        child.fileMode = 0x3
        child.dirMode = 0x4
        then:
        resolvedChild().fileMode == 0x3
        resolvedChild().dirMode == 0x4
    }

    def "matching spec inherited"() {
        given:
        spec.addChild()
        spec.filesMatching("**/*.java") {}

        expect:
        resolvedChild().copyActions*.class == [MatchingCopyAction]
    }


    def "can walk down tree created using from"() {
        CopySpec child = spec.from('somedir') { into 'child' }
        child.from('somedir') { into 'grandchild' }
        child.from('somedir') { into '/grandchild' }

        expect:
        resolvedDestPaths() == [
            RelativePath.EMPTY_ROOT,
            relativeDirectory('child'),
            relativeDirectory('child', 'grandchild'),
            relativeDirectory('grandchild'),
        ]
    }

    def "can walk down tree created using with"() {
        given:
        CopySpec childOne = new DefaultCopySpec(fileResolver, instantiator)
        childOne.into("child_one")
        spec.with(childOne)

        CopySpec childTwo = new DefaultCopySpec(fileResolver, instantiator)
         childTwo.into("child_two")
        spec.with( childTwo)

        CopySpec grandchild = new DefaultCopySpec(fileResolver, instantiator)
        grandchild.into("grandchild")
        childOne.with(grandchild)
        childTwo.with(grandchild)

        expect:
        resolvedDestPaths() == [
            RelativePath.EMPTY_ROOT,
            relativeDirectory('child_one'),
            relativeDirectory('child_one', 'grandchild'),
            relativeDirectory('child_two'),
            relativeDirectory('child_two', 'grandchild'),
        ]
    }
}
