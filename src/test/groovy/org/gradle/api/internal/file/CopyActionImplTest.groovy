package org.gradle.api.internal.file

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.util.HelperUtil
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import static org.junit.Assert.*
import org.gradle.api.tasks.util.PatternSet

@RunWith (org.jmock.integration.junit4.JMock)
public class CopyActionImplTest  {
    CopyActionImpl copyAction;
    BreadthFirstDirectoryWalker walker;
    FileVisitor visitor;
    ProjectInternal project

    JUnit4GroovyMockery context = new JUnit4GroovyMockery();

    @Before
    public void setUp() {
        project = HelperUtil.createRootProject();
        context.setImposteriser(ClassImposteriser.INSTANCE)
        walker = context.mock(BreadthFirstDirectoryWalker.class)
        visitor = context.mock(CopyVisitor.class)
        copyAction = new CopyActionImpl(project.fileResolver)
        copyAction.visitor = visitor
        copyAction.directoryWalker = walker
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
            one(walker).start(project.file('src1'))
            one(walker).start(project.file('src2'))
            exactly(2).of(walker).match(new PatternSet())
            allowing(visitor).getDidWork();  will(returnValue(true))
        })
        executeWith {
            from 'src1'
            from 'src2'
            into 'dest'
        }
    }

    @Test public void includeExclude() {
        context.checking( {
            one(walker).start(project.file('src1'))
            one(walker).match(new PatternSet(includes: ['a.b', 'c.d', 'e.f'], excludes: ['g.h']))
            allowing(visitor).getDidWork();  will(returnValue(true))
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
            one(walker).start(project.file('src1'))
            one(walker).match(new PatternSet())
            allowing(visitor).getDidWork();  will(returnValue(true))
        })

        executeWith {
            from 'src1'
            into 'dest'
        }
        assertTrue(copyAction.didWork)
    }


    @Test void testDidWorkFalse() {
        context.checking( {
            one(walker).start(project.file('src1'))
            one(walker).match(new PatternSet())
            allowing(visitor).getDidWork();  will(returnValue(false))
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

        assertEquals([project.file('parentdir'), project.file('childdir')],
                specs.get(0).getAllSourceDirs())
    }

    @Test public void inheritFromRoot() {
        project.configure(copyAction) {
            include '*.a'
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

        assertEquals([project.file('src1')], specs.get(0).getAllSourceDirs())
        assertEquals(['*.a', '*.b'], specs.get(0).getAllIncludes())
        assertEquals(project.file('dest'), specs.get(0).getDestDir())

        assertEquals([project.file('src2')], specs.get(1).getAllSourceDirs())
        assertEquals(['*.a', '*.c'], specs.get(1).getAllIncludes())
        assertEquals(project.file('dest'), specs.get(1).getDestDir())
    }

    @Test public void globalExcludes() {
        try {
            CopyActionImpl.globalExclude('**/.svn/')

            project.configure(copyAction) {
                from 'src1'
                into 'dest'
                exclude '*.bak'
            }
            copyAction.configureRootSpec()
            
            List specs = copyAction.getLeafSyncSpecs()
            assertEquals(1, specs.size())

            assertEquals(['**/.svn/', '*.bak'] as Set, new HashSet(specs.get(0).getAllExcludes()))

        } finally {
            // clear the list of global excludes so test doesn't have side effects
            CopyActionImpl.globalExclude(null)
        }
    }
}