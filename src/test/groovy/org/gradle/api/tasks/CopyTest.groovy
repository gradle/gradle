package org.gradle.api.tasks

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.AbstractTask
import org.gradle.api.internal.file.BreadthFirstDirectoryWalker
import org.gradle.api.internal.file.CopyVisitor
import org.gradle.api.internal.file.FileVisitor
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import static org.junit.Assert.*

@RunWith (org.jmock.integration.junit4.JMock)
public class CopyTest extends AbstractTaskTest {
    Copy copyTask;
    BreadthFirstDirectoryWalker walker;
    FileVisitor visitor;

    JUnit4GroovyMockery context = new JUnit4GroovyMockery();

    @Before
    public void setUp() {
        super.setUp()
        context.setImposteriser(ClassImposteriser.INSTANCE)
        walker = context.mock(BreadthFirstDirectoryWalker.class)
        visitor = context.mock(CopyVisitor.class)
        copyTask = createTask(Copy.class)
        copyTask.copyAction.visitor = visitor
        copyTask.copyAction.directoryWalker = walker
    }

    public AbstractTask getTask() {
        return copyTask;
    }

    def executeWith(Closure c) {
        project.configure(copyTask, c)
        copyTask.execute()
    }

    // Assert that InvalidUserDataException is thrown with no destination set
    @Test public void noDest() {
        project.configure(copyTask) {
            from 'src'
        }
        try {
            copyTask.execute()
        } catch (RuntimeException ex) {
            assertTrue(ex.cause instanceof InvalidUserDataException)
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
        context.checking({
            one(walker).start(project.file('src1'))
            one(walker).match(new PatternSet(includes: ['a.b', 'c.d', 'e.f'], excludes: ['g.h']))
            allowing(visitor).getDidWork(); will(returnValue(true))
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
        assertTrue(copyTask.didWork)
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
        assertFalse(copyTask.didWork)
    }

    // from with closure sets from on child spec, not on root
    @Test public void fromWithClosure() {
        project.configure(copyTask) {
            from('parentdir') {
                from 'childdir'
            }
            into 'dest'
        }
        List specs = copyTask.getLeafSyncSpecs()
        assertEquals(1, specs.size())

        assertEquals([project.file('parentdir'), project.file('childdir')] as Set,
                specs.get(0).getAllSourceDirs())
    }

    @Test public void inheritFromRoot() {
        project.configure(copyTask) {
            include '*.a'
            from('src1') {
                include '*.b'
            }
            from('src2') {
                include '*.c'
            }
            into 'dest'
        }
        List specs = copyTask.getLeafSyncSpecs()
        assertEquals(2, specs.size())

        assertEquals([project.file('src1')] as Set, specs.get(0).getAllSourceDirs())
        assertEquals(['*.a', '*.b'], specs.get(0).getAllIncludes())
        assertEquals(project.file('dest'), specs.get(0).getDestDir())

        assertEquals([project.file('src2')] as Set, specs.get(1).getAllSourceDirs())
        assertEquals(['*.a', '*.c'], specs.get(1).getAllIncludes())
        assertEquals(project.file('dest'), specs.get(1).getDestDir())
    }

    /*
    todo - the following test does not work.
    I need to understand exactly what is causing the ConventionTask.conv method to be called
    when Copy.getSrcDirs is called.  This mechanism works for integration tests, but
    doesn't work from here, so the test is failing.
     */
    /*
    @Test public void usingConvention() {
         copyTask.conventionMapping(DefaultConventionsToPropertiesMapping.RESOURCES);
         project.setProperty ('srcDirs', 'src')
         project.setProperty ('destinationDir', 'dest')
         context.checking({
             one(walker).start(project.file('src'))
             allowing(walker).addIncludes(Collections.emptyList())
             allowing(walker).addExcludes(Collections.emptyList())
             allowing(visitor).getDidWork();  will(returnValue(true))
         })
         executeWith {
         }
         assertEquals([project.file('src')] as List, copyTask.rootSpec.getSourceDirs())
        assertEquals(project.file('dest'), copyTask.rootSpec.getDestDir())
    }
    */

    @Test public void globalExcludes() {
        try {
            Copy.globalExclude('**/.svn/')

            project.configure(copyTask) {
                from 'src1'
                into 'dest'
                exclude '*.bak'
            }
            copyTask.configureRootSpec()
            
            List specs = copyTask.getLeafSyncSpecs()
            assertEquals(1, specs.size())

            assertEquals(['**/.svn/', '*.bak'] as Set, new HashSet(specs.get(0).getAllExcludes()))

        } finally {
            // clear the list of global excludes so test doesn't have side effects
            Copy.globalExclude(null)
        }
    }
}