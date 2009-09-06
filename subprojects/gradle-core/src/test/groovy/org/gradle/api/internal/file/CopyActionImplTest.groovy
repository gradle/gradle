package org.gradle.api.internal.file

import org.gradle.api.InvalidUserDataException
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
import static org.junit.Assert.*

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
        visitor = context.mock(CopyVisitor.class)
        resolver = context.mock(FileResolver.class)
        sourceFileTree = context.mock(FileTree.class)

        copyAction = new CopyActionImpl(resolver)
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

    // Assert that InvalidUserDataException is thrown with no destination set
    @Test public void noDest() {
        project.configure(copyAction) {
            from 'src'
        }
        try {
            copyAction.execute()
        } catch (RuntimeException ex) {
            assertTrue(ex instanceof InvalidUserDataException)
            return;
        }
        fail("Exception not thrown with no destination")
    }

    @Test public void multipleSourceDirs() {
        context.checking({
            one(resolver).resolveFilesAsTree(['src1', 'src2'] as Set)
            will(returnValue(sourceFileTree))
            one(sourceFileTree).matching(new PatternSet())
            will(returnValue(sourceFileTree))
            one(sourceFileTree).visit(visitor)
            allowing(visitor).getDidWork()
            will(returnValue(true))
        })
        executeWith {
            from 'src1'
            from 'src2'
            into 'dest'
        }
    }

    @Test public void includeExclude() {
        context.checking({
            one(resolver).resolveFilesAsTree(['src1'] as Set)
            will(returnValue(sourceFileTree))
            one(sourceFileTree).matching(new PatternSet(includes: ['a.b', 'c.d', 'e.f'], excludes: ['g.h']))
            will(returnValue(sourceFileTree))
            one(sourceFileTree).visit(visitor)
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


    @Test void testDidWorkTrue() {
        context.checking( {
            one(resolver).resolveFilesAsTree(['src1'] as Set)
            will(returnValue(sourceFileTree))
            one(sourceFileTree).matching(new PatternSet())
            will(returnValue(sourceFileTree))
            one(sourceFileTree).visit(visitor)
            allowing(visitor).getDidWork()
            will(returnValue(true))
        })

        executeWith {
            from 'src1'
            into 'dest'
        }
        assertTrue(copyAction.didWork)
    }


    @Test void testDidWorkFalse() {
        context.checking({
            one(resolver).resolveFilesAsTree(['src1'] as Set)
            will(returnValue(sourceFileTree))
            one(sourceFileTree).matching(new PatternSet())
            will(returnValue(sourceFileTree))
            one(sourceFileTree).visit(visitor)
            allowing(visitor).getDidWork()
            will(returnValue(false))
        })

        executeWith {
            from 'src1'
            into 'dest'
        }
        assertFalse(copyAction.didWork)
    }

    // from with closure sets from on child spec, not on root
    @Test public void fromWithClosure() {
        project.configure(copyAction) {
            from('parentdir') {
                from 'childdir'
            }
            into 'dest'
        }
        List specs = copyAction.getLeafSyncSpecs()
        assertEquals(1, specs.size())

        context.checking {
            one(resolver).resolveFilesAsTree(['parentdir', 'childdir'] as Set)
            will(returnValue(sourceFileTree))
        }
        assertSame(sourceFileTree, specs[0].getSource())
    }

    @Test public void inheritFromRoot() {
        project.configure(copyAction) {
            include '*.a'
            from('src')
            from('src1') {
                include '*.b'
            }
            from('src2') {
                include '*.c'
            }
            into 'dest'
        }
        List specs = copyAction.getLeafSyncSpecs()
        assertEquals(2, specs.size())

        context.checking {
            one(resolver).resolveFilesAsTree(['src', 'src1'] as Set)
            will(returnValue(sourceFileTree))
        }

        assertSame(sourceFileTree, specs[0].getSource())
        assertEquals(['*.a', '*.b'], specs[0].getAllIncludes())
        assertEquals(project.file('dest'), specs[0].getDestDir())

        context.checking {
            one(resolver).resolveFilesAsTree(['src', 'src2'] as Set)
            will(returnValue(sourceFileTree))
        }

        assertSame(sourceFileTree, specs[1].getSource())
        assertEquals(['*.a', '*.c'], specs[1].getAllIncludes())
        assertEquals(project.file('dest'), specs[1].getDestDir())
    }
}