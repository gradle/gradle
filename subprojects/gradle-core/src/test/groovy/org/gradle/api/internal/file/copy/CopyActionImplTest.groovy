/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.HelperUtil
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.gradle.api.internal.file.FileResolver

@RunWith (org.jmock.integration.junit4.JMock)
public class CopyActionImplTest  {
    CopyActionImpl copyAction;
    FileVisitor visitor;
    ProjectInternal project
    FileResolver resolver
    FileTree sourceFileTree

    JUnit4GroovyMockery context = new JUnit4GroovyMockery();

    @Before
    public void setUp() {
        project = HelperUtil.createRootProject();
        context.setImposteriser(ClassImposteriser.INSTANCE)
        visitor = context.mock(FileCopySpecVisitor.class)
        resolver = context.mock(FileResolver.class)
        sourceFileTree = context.mock(FileTree.class)

        copyAction = new CopyActionImpl(resolver, visitor)
        copyAction.visitor = visitor

        context.checking {
            allowing(resolver).resolve('dest')
            will(returnValue(project.file('dest')))
        }
    }

    def executeWith(Closure c) {
        project.configure(copyAction, c)
        copyAction.execute()
    }

    @Test public void multipleSourceDirs() {
        context.checking {
            one(visitor).startVisit(copyAction)
            one(visitor).visitSpec(copyAction)
            one(resolver).resolveFilesAsTree(['src1', 'src2'] as Set)
            will(returnValue(sourceFileTree))
            one(sourceFileTree).matching(new PatternSet())
            will(returnValue(sourceFileTree))
            one(sourceFileTree).visit(visitor)
            one(visitor).endVisit()
            allowing(visitor).getDidWork()
            will(returnValue(true))
        }
        executeWith {
            from 'src1'
            from 'src2'
            into 'dest'
        }
    }

    @Test public void includeExclude() {
        context.checking({
            one(visitor).startVisit(copyAction)
            one(visitor).visitSpec(copyAction)
            one(resolver).resolveFilesAsTree(['src1'] as Set)
            will(returnValue(sourceFileTree))
            one(sourceFileTree).matching(new PatternSet(includes: ['a.b', 'c.d', 'e.f'], excludes: ['g.h']))
            will(returnValue(sourceFileTree))
            one(sourceFileTree).visit(visitor)
            one(visitor).endVisit()
            allowing(visitor).getDidWork()
            will(returnValue(true))
        })

        executeWith {
            from 'src1'
            into 'dest'
            include 'a.b', 'c.d'
            include 'e.f'
            exclude 'g.h'
        }
    }

    @Test void testDidWorkDelegatesToVisitor() {
        context.checking({
            one(visitor).getDidWork()
            will(returnValue(false))
            one(visitor).getDidWork()
            will(returnValue(true))
        })

        assertFalse(copyAction.didWork)
        assertTrue(copyAction.didWork)
    }

    // from with closure sets from on child spec, not on root
    @Test public void copiesEachSpec() {
        FileTree source1 = context.mock(FileTree, 'source1')
        FileTree source2 = context.mock(FileTree, 'source2')
        FileTree source3 = context.mock(FileTree, 'source3')
        FileTree filtered1 = context.mock(FileTree, 'filtered1')
        FileTree filtered2 = context.mock(FileTree, 'filtered2')
        FileTree filtered3 = context.mock(FileTree, 'filtered3')

        context.checking {
            one(visitor).startVisit(copyAction)

            one(visitor).visitSpec(copyAction)

            one(resolver).resolveFilesAsTree(['src'] as Set)
            will(returnValue(source1))
            one(source1).matching(withParam(notNullValue()))
            will(returnValue(filtered1))
            one(filtered1).visit(visitor)

            one(visitor).visitSpec(withParam(notNullValue()))

            one(resolver).resolveFilesAsTree(['src2'] as Set)
            will(returnValue(source2))
            one(source2).matching(withParam(notNullValue()))
            will(returnValue(filtered2))
            one(filtered2).visit(visitor)

            one(visitor).visitSpec(withParam(notNullValue()))

            one(resolver).resolveFilesAsTree(['src3'] as Set)
            will(returnValue(source3))
            one(source3).matching(withParam(notNullValue()))
            will(returnValue(filtered3))
            one(filtered3).visit(visitor)

            one(visitor).endVisit()
        }

        executeWith {
            from('src')
            from('src2') { }
            from('src3') { }
        }
    }

    @Test public void allSourceIncludesSourceFromAllSpecs() {
        FileTree source1 = context.mock(FileTree, 'source1')
        FileTree source2 = context.mock(FileTree, 'source2')
        FileTree source3 = context.mock(FileTree, 'source3')
        FileTree filtered1 = context.mock(FileTree, 'filtered1')
        FileTree filtered2 = context.mock(FileTree, 'filtered2')
        FileTree filtered3 = context.mock(FileTree, 'filtered3')

        project.configure(copyAction) {
            from('src')
            from('src2') { }
            from('src3') { }
        }

        context.checking {
            one(resolver).resolveFilesAsTree(['src'] as Set)
            will(returnValue(source1))
            one(source1).matching(withParam(notNullValue()))
            will(returnValue(filtered1))

            one(resolver).resolveFilesAsTree(['src2'] as Set)
            will(returnValue(source2))
            one(source2).matching(withParam(notNullValue()))
            will(returnValue(filtered2))
            one(resolver).resolveFilesAsTree(['src3'] as Set)
            will(returnValue(source3))
            one(source3).matching(withParam(notNullValue()))
            will(returnValue(filtered3))

            one(resolver).resolveFilesAsTree([filtered1, filtered2, filtered3])
            will(returnValue(sourceFileTree))
        }

        assertSame(sourceFileTree, copyAction.allSource)
    }
}
