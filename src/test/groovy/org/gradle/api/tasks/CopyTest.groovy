package org.gradle.api.tasks

import org.gradle.api.tasks.AbstractTaskTest
import org.gradle.api.internal.AbstractTask
import org.gradle.api.internal.tasks.copy.*
import org.gradle.api.plugins.DefaultConventionsToPropertiesMapping;
import org.junit.runner.RunWith
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.lib.legacy.ClassImposteriser
import static org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.tasks.copy.DirectoryWalker
import org.gradle.api.internal.tasks.copy.FileVisitor
import org.jmock.Expectations
import org.hamcrest.Matchers
import org.gradle.api.tasks.Copy



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
        copyTask = new Copy(project, AbstractTaskTest.TEST_TASK_NAME, visitor, walker)
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
            allowing(walker).addIncludes(Collections.emptyList())
            allowing(walker).addExcludes(Collections.emptyList())
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
            allowing(walker).addIncludes(['a.b', 'c.d', 'e.f'] as List)
            allowing(walker).addExcludes(['g.h'] as List)
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
            allowing(walker).addIncludes([] as List)
            allowing(walker).addExcludes([] as List)
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
            allowing(walker).addIncludes([] as List)
            allowing(walker).addExcludes([] as List)
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

        assertEquals([project.file('parentdir'), project.file('childdir')],
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

        assertEquals([project.file('src1')], specs.get(0).getAllSourceDirs())
        assertEquals(['*.a', '*.b'], specs.get(0).getAllIncludes())
        assertEquals(project.file('dest'), specs.get(0).getDestDir())

        assertEquals([project.file('src2')], specs.get(1).getAllSourceDirs())
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