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
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.sameInstance
import static org.junit.Assert.assertThat

@RunWith(JMock)
public class DefaultCopySpecResolutionTest {

    @Rule
    public TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider();
    private TestFile baseFile = testDir.testDirectory
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery();
    private FileResolver fileResolver = [resolve: { it as File }, getPatternSetFactory: { TestFiles.getPatternSetFactory() }] as FileResolver
    private final Instantiator instantiator = DirectInstantiator.INSTANCE
    private final DefaultCopySpec parentSpec = new DefaultCopySpec(fileResolver, instantiator)

    @Test
    public void testSpecHasRootPathAsDestinationByDefault() {
        assertThat(parentSpec.buildRootResolver().getDestPath(), equalTo(new RelativePath(false)))
    }

    @Test
    public void testChildResolvesUsingParentDestinationPathAsDefault() {
        parentSpec.into 'parent'
        CopySpecResolver parentContext = parentSpec.buildRootResolver()
        assertThat(parentContext.destPath, equalTo(new RelativePath(false, 'parent')))

        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        CopySpecResolver childResolver = child.buildResolverRelativeToParent(parentContext)
        assertThat(childResolver.destPath, equalTo(new RelativePath(false, 'parent')))
    }

    @Test
    public void testChildDestinationPathIsResolvedAsNestedWithinParent() {
        parentSpec.into 'parent'
        CopySpecResolver parentContext = parentSpec.buildRootResolver()
        assertThat(parentContext.destPath, equalTo(new RelativePath(false, 'parent')))

        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        child.into 'child'
        CopySpecResolver childResolver = child.buildResolverRelativeToParent(parentContext)
        assertThat(childResolver.destPath, equalTo(new RelativePath(false, 'parent', 'child')))
    }

    @Test
    public void testChildUsesParentPatternsAsDefault() {

        Spec specInclude = [:] as Spec
        Spec specExclude = [:] as Spec

        parentSpec.include('parent-include')
        parentSpec.exclude('parent-exclude')
        parentSpec.include(specInclude)
        parentSpec.exclude(specExclude)

        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)


        DefaultCopySpec.DefaultCopySpecResolver childResolver = child.buildResolverRelativeToParent(parentSpec.buildRootResolver())
        PatternSet patterns = childResolver.patternSet

        assertThat(patterns.includes, equalTo(['parent-include'] as Set))
        assertThat(patterns.excludes, equalTo(['parent-exclude'] as Set))
        assertThat(patterns.includeSpecs, equalTo([specInclude] as Set))
        assertThat(patterns.excludeSpecs, equalTo([specExclude] as Set))
    }


    @Test
    public void testChildUsesPatternsFromParent() {
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

        DefaultCopySpec.DefaultCopySpecResolver childResolver = child.buildResolverRelativeToParent(parentSpec.buildRootResolver())
        PatternSet patterns = childResolver.patternSet

        assertThat(patterns.includes, equalTo(['parent-include', 'child-include'] as Set))
        assertThat(patterns.excludes, equalTo(['parent-exclude', 'child-exclude'] as Set))
        assertThat(patterns.includeSpecs, equalTo([specInclude, childInclude] as Set))
        assertThat(patterns.excludeSpecs, equalTo([specExclude, childExclude] as Set))
    }

    @Test
    public void testResolvesSourceUsingOwnSourceFilteredByPatternset() {
        fileResolver = context.mock(FileResolver)
        context.checking {
            allowing(fileResolver).getPatternSetFactory();
            will(returnValue(TestFiles.getPatternSetFactory()));
        }

        //Does not get source from root
        parentSpec.from 'x'
        parentSpec.from 'y'

        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        child.from 'a'
        child.from 'b'
        DefaultCopySpec.DefaultCopySpecResolver childResolver = child.buildResolverRelativeToParent(parentSpec.buildRootResolver())

        def filteredTree = context.mock(FileTreeInternal, 'filtered')

        context.checking {
            one(fileResolver).resolveFilesAsTree(['a', 'b'] as Set)
            def tree = context.mock(FileTreeInternal, 'source')
            will(returnValue(tree))
            one(tree).matching((PatternFilterable)withParam(equalTo(parentSpec.patternSet)))
            will(returnValue(filteredTree))
        }

        assertThat(childResolver.source, sameInstance(filteredTree))
    }



    @Test
    public void duplicatesStrategyDefaultsToInclude() {
        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        DefaultCopySpec.DefaultCopySpecResolver childResolver = child.buildResolverRelativeToParent(parentSpec.buildRootResolver())
        assert childResolver.duplicatesStrategy == DuplicatesStrategy.INCLUDE
    }

    @Test
    public void childInheritsDuplicatesStrategyFromParent() {
        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        DefaultCopySpec.DefaultCopySpecResolver childResolver = child.buildResolverRelativeToParent(parentSpec.buildRootResolver())

        assert childResolver.duplicatesStrategy == DuplicatesStrategy.INCLUDE

        parentSpec.duplicatesStrategy = 'EXCLUDE'
        assert childResolver.duplicatesStrategy == DuplicatesStrategy.EXCLUDE

        child.duplicatesStrategy = 'INCLUDE'
        assert childResolver.duplicatesStrategy == DuplicatesStrategy.INCLUDE
    }

    @Test
    public void caseSensitiveFlagDefaultsToTrue() {
        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        DefaultCopySpec.DefaultCopySpecResolver childResolver = child.buildResolverRelativeToParent(parentSpec.buildRootResolver())
        assert childResolver.caseSensitive
        assert childResolver.patternSet.caseSensitive
    }

    @Test
    public void childUsesCaseSensitiveFlagFromParentAsDefault() {
        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        DefaultCopySpec.DefaultCopySpecResolver childResolver = child.buildResolverRelativeToParent(parentSpec.buildRootResolver())

        assert childResolver.caseSensitive
        assert childResolver.patternSet.caseSensitive

        parentSpec.caseSensitive = false
        assert !childResolver.caseSensitive
        assert !childResolver.patternSet.caseSensitive

        child.caseSensitive = true
        assert childResolver.caseSensitive
        assert childResolver.patternSet.caseSensitive
    }

    @Test
    public void includeEmptyDirsFlagDefaultsToTrue() {
        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        DefaultCopySpec.DefaultCopySpecResolver childResolver = child.buildResolverRelativeToParent(parentSpec.buildRootResolver())
        assert childResolver.includeEmptyDirs

    }

    @Test
    public void childUsesIncludeEmptyDirsFlagFromParentAsDefault() {

        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        DefaultCopySpec.DefaultCopySpecResolver childResolver = child.buildResolverRelativeToParent(parentSpec.buildRootResolver())

        assert childResolver.includeEmptyDirs

        parentSpec.includeEmptyDirs = false
        assert !childResolver.includeEmptyDirs

        child.includeEmptyDirs = true
        assert childResolver.includeEmptyDirs
    }


    @Test
    public void testSpecInheritsActionsFromParent() {
        Action parentAction = context.mock(Action, 'parent')
        parentSpec.eachFile parentAction

        Action childAction = context.mock(Action, 'child')
        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        child.eachFile childAction

        DefaultCopySpec.DefaultCopySpecResolver childResolver = child.buildResolverRelativeToParent(parentSpec.buildRootResolver())

        assertThat(childResolver.allCopyActions, equalTo([parentAction, childAction]))
    }

    @Test
    public void testHasNoPermissionsByDefault() {
        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        DefaultCopySpec.DefaultCopySpecResolver childResolver = child.buildResolverRelativeToParent(parentSpec.buildRootResolver())
        assert childResolver.fileMode == null
        assert childResolver.dirMode == null
    }

    @Test
    public void testInheritsPermissionsFromParent() {
        DefaultCopySpec child = new DefaultCopySpec(fileResolver, instantiator)
        DefaultCopySpec.DefaultCopySpecResolver childResolver = child.buildResolverRelativeToParent(parentSpec.buildRootResolver())
        parentSpec.fileMode = 0x1
        parentSpec.dirMode = 0x2

        Assert.assertEquals(0x1, childResolver.fileMode)
        Assert.assertEquals(0x2, childResolver.dirMode)

        child.fileMode = 0x3
        child.dirMode = 0x4

        Assert.assertEquals(0x3, childResolver.fileMode)
        Assert.assertEquals(0x4, childResolver.dirMode)
    }

    @Test
    public void canWalkDownTreeCreatedUsingFromIntegrationTest() {

        CopySpec child = parentSpec.from('somedir') { into 'child' }
        child.from('somedir') { into 'grandchild' }
        child.from('somedir') { into '/grandchild' }

        List<RelativePath> paths = new ArrayList<RelativePath>()
        parentSpec.buildRootResolver().walk(new Action<CopySpecResolver>() {
            void execute(CopySpecResolver csr) {
                paths.add(csr.destPath)
            }
        })

        assertThat(paths.size(), equalTo(4))
        assertThat(paths.get(0), equalTo(new RelativePath(false)))
        assertThat(paths.get(1), equalTo(new RelativePath(false, 'child')))
        assertThat(paths.get(2), equalTo(new RelativePath(false, 'child', 'grandchild')))
        assertThat(paths.get(3), equalTo(new RelativePath(false, 'grandchild')))
    }

    @Test
    public void canWalkDownTreeCreatedUsingWithIntegrationTest() {

        DefaultCopySpec childOne = new DefaultCopySpec(fileResolver, instantiator)
        childOne.into("child_one");
        parentSpec.with(childOne);

        DefaultCopySpec childTwo = new DefaultCopySpec(fileResolver, instantiator)
         childTwo.into("child_two");
        parentSpec.with( childTwo);

        DefaultCopySpec grandchild = new DefaultCopySpec(fileResolver, instantiator)
        grandchild.into("grandchild");
        childOne.with(grandchild);
         childTwo.with(grandchild);

        List<RelativePath> paths = new ArrayList<RelativePath>()
        parentSpec.buildRootResolver().walk(new Action<CopySpecResolver>() {
            void execute(CopySpecResolver csr) {
                paths.add(csr.destPath)
            }
        })

        assertThat(paths.size(), equalTo(5))
        assertThat(paths.get(0), equalTo(new RelativePath(false)))
        assertThat(paths.get(1), equalTo(new RelativePath(false, 'child_one')))
        assertThat(paths.get(2), equalTo(new RelativePath(false, 'child_one', 'grandchild')))
        assertThat(paths.get(3), equalTo(new RelativePath(false, 'child_two')))
        assertThat(paths.get(4), equalTo(new RelativePath(false, 'child_two', 'grandchild')))
    }


}


