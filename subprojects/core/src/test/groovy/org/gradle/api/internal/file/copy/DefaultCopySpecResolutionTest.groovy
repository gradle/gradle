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
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.reflect.Instantiator
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultCopySpecResolutionTest extends Specification {

    @Rule
    public TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider()
    private FileResolver fileResolver = [resolve: { it as File }, getPatternSetFactory: { TestFiles.getPatternSetFactory() }] as FileResolver
    private final Instantiator instantiator = TestUtil.instantiatorFactory().decorateLenient()
    private final DefaultCopySpec parentSpec = new DefaultCopySpec(fileResolver, instantiator)

    def testSpecHasRootPathAsDestinationByDefault() {
        expect:
        parentSpec.buildRootResolver().getDestPath() == new RelativePath(false)
    }

    def testChildResolvesUsingParentDestinationPathAsDefault() {
        when:
        parentSpec.into 'parent'
        CopySpecResolver parentContext = parentSpec.buildRootResolver()

        then:
        parentContext.destPath == new RelativePath(false, 'parent')

        when:
        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        CopySpecResolver childResolver = child.buildResolverRelativeToParent(parentContext)

        then:
        childResolver.destPath == new RelativePath(false, 'parent')
    }

    def testChildDestinationPathIsResolvedAsNestedWithinParent() {
        given:
        parentSpec.into 'parent'
        CopySpecResolver parentContext = parentSpec.buildRootResolver()

        when:
        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        child.into 'child'
        CopySpecResolver childResolver = child.buildResolverRelativeToParent(parentContext)

        then:
        childResolver.destPath == new RelativePath(false, 'parent', 'child')
    }

    def testChildUsesParentPatternsAsDefault() {
        when:
        Spec specInclude = [:] as Spec
        Spec specExclude = [:] as Spec

        parentSpec.include('parent-include')
        parentSpec.exclude('parent-exclude')
        parentSpec.include(specInclude)
        parentSpec.exclude(specExclude)

        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        PatternSet patterns = child.buildResolverRelativeToParent(parentSpec.buildRootResolver()).patternSet

        then:
        patterns.includes == ['parent-include'] as Set
        patterns.excludes == ['parent-exclude'] as Set
        patterns.includeSpecs == [specInclude] as Set
        patterns.excludeSpecs == [specExclude] as Set
    }

    def testChildUsesPatternsFromParent() {
        when:
        Spec specInclude = [:] as Spec
        Spec specExclude = [:] as Spec


        parentSpec.include('parent-include')
        parentSpec.exclude('parent-exclude')
        parentSpec.include(specInclude)
        parentSpec.exclude(specExclude)

        Spec childInclude = [:] as Spec
        Spec childExclude = [:] as Spec

        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        child.include('child-include')
        child.exclude('child-exclude')
        child.include(childInclude)
        child.exclude(childExclude)

        PatternSet patterns = child.buildResolverRelativeToParent(parentSpec.buildRootResolver()).patternSet

        then:
        patterns.includes == ['parent-include', 'child-include'] as Set
        patterns.excludes == ['parent-exclude', 'child-exclude'] as Set
        patterns.includeSpecs == [specInclude, childInclude] as Set
        patterns.excludeSpecs == [specExclude, childExclude] as Set
    }

    def duplicatesStrategyDefaultsToInclude() {
        expect:
        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        DefaultCopySpec.DefaultCopySpecResolver childResolver = child.buildResolverRelativeToParent(parentSpec.buildRootResolver())
        assert childResolver.duplicatesStrategy == DuplicatesStrategy.INCLUDE
    }

    def childInheritsDuplicatesStrategyFromParent() {
        when:
        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        DefaultCopySpec.DefaultCopySpecResolver childResolver = child.buildResolverRelativeToParent(parentSpec.buildRootResolver())

        then:
        childResolver.duplicatesStrategy == DuplicatesStrategy.INCLUDE

        when:
        parentSpec.duplicatesStrategy = 'EXCLUDE'

        then:
        childResolver.duplicatesStrategy == DuplicatesStrategy.EXCLUDE

        when:
        child.duplicatesStrategy = 'INCLUDE'

        then:
        childResolver.duplicatesStrategy == DuplicatesStrategy.INCLUDE
    }

    def caseSensitiveFlagDefaultsToTrue() {
        when:
        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        DefaultCopySpec.DefaultCopySpecResolver childResolver = child.buildResolverRelativeToParent(parentSpec.buildRootResolver())

        then:
        childResolver.caseSensitive
        childResolver.patternSet.caseSensitive
    }

    def childUsesCaseSensitiveFlagFromParentAsDefault() {
        when:
        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        DefaultCopySpec.DefaultCopySpecResolver childResolver = child.buildResolverRelativeToParent(parentSpec.buildRootResolver())

        then:
        childResolver.caseSensitive
        childResolver.patternSet.caseSensitive

        when:
        parentSpec.caseSensitive = false

        then:
        !childResolver.caseSensitive
        !childResolver.patternSet.caseSensitive

        when:
        child.caseSensitive = true

        then:
        childResolver.caseSensitive
        childResolver.patternSet.caseSensitive
    }

    def includeEmptyDirsFlagDefaultsToTrue() {
        when:
        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        DefaultCopySpec.DefaultCopySpecResolver childResolver = child.buildResolverRelativeToParent(parentSpec.buildRootResolver())

        then:
        childResolver.includeEmptyDirs
    }

    def childUsesIncludeEmptyDirsFlagFromParentAsDefault() {
        when:
        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        DefaultCopySpec.DefaultCopySpecResolver childResolver = child.buildResolverRelativeToParent(parentSpec.buildRootResolver())

        then:
        childResolver.includeEmptyDirs

        when:
        parentSpec.includeEmptyDirs = false

        then:
        !childResolver.includeEmptyDirs

        when:
        child.includeEmptyDirs = true

        then:
        childResolver.includeEmptyDirs
    }

    def testSpecInheritsActionsFromParent() {
        Action parentAction = Mock()
        Action childAction = Mock()

        when:
        parentSpec.eachFile parentAction

        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        child.eachFile childAction

        DefaultCopySpec.DefaultCopySpecResolver childResolver = child.buildResolverRelativeToParent(parentSpec.buildRootResolver())

        then:
        childResolver.allCopyActions == [parentAction, childAction]
    }

    def testHasNoPermissionsByDefault() {
        when:
        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        DefaultCopySpec.DefaultCopySpecResolver childResolver = child.buildResolverRelativeToParent(parentSpec.buildRootResolver())

        then:
        childResolver.fileMode == null
        childResolver.dirMode == null
    }

    def testInheritsPermissionsFromParent() {
        when:
        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        DefaultCopySpec.DefaultCopySpecResolver childResolver = child.buildResolverRelativeToParent(parentSpec.buildRootResolver())
        parentSpec.fileMode = 0x1
        parentSpec.dirMode = 0x2

        then:
        childResolver.fileMode == 0x1
        childResolver.dirMode == 0x2

        when:
        child.fileMode = 0x3
        child.dirMode = 0x4

        then:
        childResolver.fileMode == 0x3
        childResolver.dirMode == 0x4
    }

    def canWalkDownTreeCreatedUsingFromIntegrationTest() {

        CopySpec child = parentSpec.from('somedir') { into 'child' }
        child.from('somedir') { into 'grandchild' }
        child.from('somedir') { into '/grandchild' }

        when:
        List<RelativePath> paths = new ArrayList<RelativePath>()
        parentSpec.buildRootResolver().walk(new Action<CopySpecResolver>() {
            void execute(CopySpecResolver csr) {
                paths.add(csr.destPath)
            }
        })

        then:
        paths == [
            new RelativePath(false),
            new RelativePath(false, 'child'),
            new RelativePath(false, 'child', 'grandchild'),
            new RelativePath(false, 'grandchild')
        ]
    }

    def canWalkDownTreeCreatedUsingWithIntegrationTest() {
        when:
        DefaultCopySpec childOne = new DefaultCopySpec(fileResolver, instantiator)
        childOne.into("child_one")
        parentSpec.with(childOne)

        DefaultCopySpec childTwo = new DefaultCopySpec(fileResolver, instantiator)
         childTwo.into("child_two")
        parentSpec.with( childTwo)

        DefaultCopySpec grandchild = new DefaultCopySpec(fileResolver, instantiator)
        grandchild.into("grandchild")
        childOne.with(grandchild)
        childTwo.with(grandchild)

        List<RelativePath> paths = new ArrayList<RelativePath>()
        parentSpec.buildRootResolver().walk(new Action<CopySpecResolver>() {
            void execute(CopySpecResolver csr) {
                paths.add(csr.destPath)
            }
        })

        then:
        paths == [
            new RelativePath(false),
            new RelativePath(false, 'child_one'),
            new RelativePath(false, 'child_one', 'grandchild'),
            new RelativePath(false, 'child_two'),
            new RelativePath(false, 'child_two', 'grandchild')
        ]
    }
}


