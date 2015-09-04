/*
 * Copyright 2010 the original author or authors.
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

import org.apache.tools.ant.filters.HeadFilter
import org.apache.tools.ant.filters.StripJavaComments
import org.gradle.api.Action
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.specs.Spec
import org.gradle.internal.Actions
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

@RunWith(JMock)
public class DefaultCopySpecTest {

    @Rule
    public TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider();
    private TestFile baseFile = testDir.testDirectory
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery();
    private final FileResolver fileResolver = context.mock(FileResolver);
    private final Instantiator instantiator = DirectInstantiator.INSTANCE
    private final DefaultCopySpec spec = new DefaultCopySpec(fileResolver, instantiator)

    private List<String> getTestSourceFileNames() {
        ['first', 'second']
    }

    private List<File> getAbsoluteTestSources() {
        testSourceFileNames.collect { new File(baseFile, it) }
    }

    @Test
    public void testAbsoluteFromList() {
        List<File> sources = getAbsoluteTestSources();
        spec.from(sources);
        assertEquals([sources], spec.sourcePaths as List);
    }

    @Test
    public void testFromArray() {
        List<File> sources = getAbsoluteTestSources();
        spec.from(sources as File[]);
        assertEquals(sources, spec.sourcePaths as List);
    }

    @Test
    public void testSourceWithClosure() {
        CopySpec child = spec.from('source') {
        }

        assertThat(child, not(sameInstance(spec as CopySpec)))
        assertEquals(['source'], unpackWrapper(child).sourcePaths as List);
    }

    @Test
    public void testMultipleSourcesWithClosure() {
        CopySpec child = spec.from(['source1', 'source2']) {
        }

        assertThat(child, not(sameInstance(spec as CopySpec)))
        assertEquals(['source1', 'source2'], unpackWrapper(child).sourcePaths.flatten() as List);
    }

    @Test
    public void testDefaultDestinationPathForRootSpec() {
        assertThat(spec.buildRootResolver().destPath, equalTo(new RelativePath(false)))
    }

    @Test
    public void testInto() {
        spec.into 'spec'
        assertThat(spec.buildRootResolver().destPath, equalTo(new RelativePath(false, 'spec')))
        spec.into '/'
        assertThat(spec.buildRootResolver().destPath, equalTo(new RelativePath(false)))
    }

    @Test
    public void testIntoWithAClosure() {
        spec.into { 'spec' }
        assertThat(spec.buildRootResolver().destPath, equalTo(new RelativePath(false, 'spec')))
        spec.into { return { 'spec' } }
        assertThat(spec.buildRootResolver().destPath, equalTo(new RelativePath(false, 'spec')))
    }

    @Test
    public void testWithSpec() {
        DefaultCopySpec other1 = new DefaultCopySpec(fileResolver, instantiator)
        DefaultCopySpec other2 = new DefaultCopySpec(fileResolver, instantiator)

        spec.with other1, other2
        assertTrue(spec.sourcePaths.empty)
        assertThat(spec.childSpecs.size(), equalTo(2))
    }

    @Test
    public void testDestinationWithClosure() {
        CopySpec child = spec.into('target') {
        }

        assertThat(child, not(sameInstance(spec as CopySpec)))
        assertThat(unpackWrapper(child).buildRootResolver().destPath, equalTo(new RelativePath(false, 'target')))
    }

    @Test
    public void testNoArgFilter() {
        spec.filter(StripJavaComments)
        assertThat(spec.copyActions.size(), equalTo(1))
    }

    @Test
    public void testArgFilter() {
        spec.filter(HeadFilter, lines: 15, skip: 2)
        assertThat(spec.copyActions.size(), equalTo(1))
    }

    @Test
    public void testExpand() {
        spec.expand(version: '1.2', skip: 2)
        assertThat(spec.copyActions.size(), equalTo(1))
    }

    @Test
    public void testTwoFilters() {
        spec.filter(StripJavaComments)
        spec.filter(HeadFilter, lines: 15, skip: 2)

        assertThat(spec.copyActions.size(), equalTo(2))
    }

    @Test
    public void testAddsStringNameTransformerToActions() {
        spec.rename("regexp", "replacement")

        assertThat(spec.copyActions.size(), equalTo(1))
        assertThat(spec.copyActions[0], instanceOf(RenamingCopyAction))
        assertThat(spec.copyActions[0].transformer, instanceOf(RegExpNameMapper))
    }

    @Test
    public void testAddsPatternNameTransformerToActions() {
        spec.rename(/regexp/, "replacement")

        assertThat(spec.copyActions.size(), equalTo(1))
        assertThat(spec.copyActions[0], instanceOf(RenamingCopyAction))
        assertThat(spec.copyActions[0].transformer, instanceOf(RegExpNameMapper))
    }

    @Test
    public void testAddsClosureToActions() {
        spec.rename {}

        assertThat(spec.copyActions.size(), equalTo(1))
        assertThat(spec.copyActions[0], instanceOf(RenamingCopyAction))
    }

    @Test
    public void testAddAction() {
        def action = context.mock(Action)
        spec.eachFile(action)

        assertThat(spec.copyActions, equalTo([action]))
    }

    @Test
    public void testAddActionAsClosure() {
        def action = {}
        spec.eachFile(action)

        assertThat(spec.copyActions.size(), equalTo(1))
    }


    @Test
    public void testHasNoSourceByDefault() {
        assertFalse(spec.hasSource())
    }

    @Test
    public void testHasSourceWhenSpecHasSource() {
        spec.from 'source'
        assertTrue(spec.hasSource())
    }

    @Test
    public void testHasSourceWhenChildSpecHasSource() {
        spec.from('source') {}
        assertTrue(spec.hasSource())
    }


    @Test
    public void testMatchingCreatesAppropriateAction() {
        spec.filesMatching "root/**/a*", Actions.doNothing()
        assertEquals(1, spec.copyActions.size())
        assertThat(spec.copyActions[0], instanceOf(MatchingCopyAction))

        Spec<RelativePath> matchSpec = spec.copyActions[0].matchSpec
        assertTrue(matchSpec.isSatisfiedBy(RelativePath.parse(true, '/root/folder/abc')))
        assertTrue(matchSpec.isSatisfiedBy(RelativePath.parse(true, '/root/abc')))
        assertFalse(matchSpec.isSatisfiedBy(RelativePath.parse(true, '/notRoot/abc')))
        assertFalse(matchSpec.isSatisfiedBy(RelativePath.parse(true, '/root/bbc')))
        assertFalse(matchSpec.isSatisfiedBy(RelativePath.parse(true, '/notRoot/bbc')))
    }

    @Test
    public void testNotMatchingCreatesAppropriateAction() {
        // no path component starting with an a
        spec.filesNotMatching("**/a*/**", Actions.doNothing())
        assertEquals(1, spec.copyActions.size())
        assertThat(spec.copyActions[0], instanceOf(MatchingCopyAction))

        Spec<RelativePath> matchSpec = spec.copyActions[0].matchSpec
        assertTrue(matchSpec.isSatisfiedBy(RelativePath.parse(true, 'root/folder1/folder2')))
        assertTrue(matchSpec.isSatisfiedBy(RelativePath.parse(true, 'modules/project1')))
        assertFalse(matchSpec.isSatisfiedBy(RelativePath.parse(true, 'archives/folder/file')))
        assertFalse(matchSpec.isSatisfiedBy(RelativePath.parse(true, 'root/archives/file')))
        assertFalse(matchSpec.isSatisfiedBy(RelativePath.parse(true, 'root/folder/abc')))
    }

    @Test
    void "Add spec as first child"() {
        DefaultCopySpec child1 = spec.addFirst()
        assert child1 != null
        assert spec.childSpecs.size() == 1
        assert spec.childSpecs[0] == child1
        DefaultCopySpec child2 = spec.addFirst()
        assert child2 != null
        assert spec.childSpecs.size() == 2
        assert spec.childSpecs[0] == child2
        assert spec.childSpecs[1] == child1
    }

    @Test
    void "Add spec in between two child specs if given child exists"() {
        DefaultCopySpec child1 = spec.addChild()
        DefaultCopySpec child2 = spec.addChild()
        assert child1 != null
        assert child2 != null
        assert spec.childSpecs.size() == 2
        assert spec.childSpecs[0] == child1
        assert spec.childSpecs[1] == child2
        DefaultCopySpec child3 = spec.addChildBeforeSpec(child2)
        assert child3 != null
        assert spec.childSpecs.size() == 3
        assert spec.childSpecs[0] == child1
        assert spec.childSpecs[1] == child3
        assert spec.childSpecs[2] == child2
    }

    @Test
    void "Add spec in between two child specs if given child does not exist"() {
        DefaultCopySpec child1 = spec.addChild()
        DefaultCopySpec child2 = spec.addChild()
        assert child1 != null
        assert child2 != null
        assert spec.childSpecs.size() == 2
        assert spec.childSpecs[0] == child1
        assert spec.childSpecs[1] == child2
        DefaultCopySpec unknownChild = new DefaultCopySpec(fileResolver, instantiator)
        DefaultCopySpec child3 = spec.addChildBeforeSpec(unknownChild)
        assert child3 != null
        assert spec.childSpecs.size() == 3
        assert spec.childSpecs[0] == child1
        assert spec.childSpecs[1] == child2
        assert spec.childSpecs[2] == child3
    }

    @Test
    void "Add spec in between two child specs if given child is null"() {
        DefaultCopySpec child1 = spec.addChild()
        DefaultCopySpec child2 = spec.addChild()
        assert child1 != null
        assert child2 != null
        assert spec.childSpecs.size() == 2
        assert spec.childSpecs[0] == child1
        assert spec.childSpecs[1] == child2
        DefaultCopySpec child3 = spec.addChildBeforeSpec(null)
        assert child3 != null
        assert spec.childSpecs.size() == 3
        assert spec.childSpecs[0] == child1
        assert spec.childSpecs[1] == child2
        assert spec.childSpecs[2] == child3
    }

    @Test
    void "properties accessed directly have defaults"() {

        assert spec.caseSensitive == true;
        assert spec.getIncludeEmptyDirs() == true;
        assert spec.duplicatesStrategy == DuplicatesStrategy.INCLUDE
        assert spec.fileMode == null
        assert spec.dirMode == null

        spec.caseSensitive = false
        spec.includeEmptyDirs = false
        spec.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        spec.fileMode = 1
        spec.dirMode = 2

        assert spec.caseSensitive == false;
        assert spec.getIncludeEmptyDirs() == false;
        assert spec.duplicatesStrategy == DuplicatesStrategy.EXCLUDE
        assert spec.fileMode == 1
        assert spec.dirMode == 2


    }

    @Test
    void "properties accessed directly on specs created using from inherit from parents"() {

        //set non defaults on root
        spec.caseSensitive = false
        spec.includeEmptyDirs = false
        spec.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        spec.fileMode = 1
        spec.dirMode = 2

        DefaultCopySpec child = unpackWrapper(spec.from("child") {

        })

        //children still have defaults
        assert child.caseSensitive == false;
        assert child.getIncludeEmptyDirs() == false;
        assert child.duplicatesStrategy == DuplicatesStrategy.EXCLUDE
        assert child.fileMode == 1
        assert child.dirMode == 2

    }

    @Test
    void "properties accessed directly on specs created using into inherit from parents"() {

        //set non defaults on root
        spec.caseSensitive = false
        spec.includeEmptyDirs = false
        spec.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        spec.fileMode = 1
        spec.dirMode = 2

        DefaultCopySpec child = unpackWrapper(spec.into("child") {

        })

        //children still have defaults
        assert child.caseSensitive == false;
        assert child.getIncludeEmptyDirs() == false;
        assert child.duplicatesStrategy == DuplicatesStrategy.EXCLUDE
        assert child.fileMode == 1
        assert child.dirMode == 2

    }


    DefaultCopySpec unpackWrapper(CopySpec copySpec) {
        (copySpec as CopySpecWrapper).delegate as DefaultCopySpec
    }
}

