package org.gradle.api.tasks

import org.gradle.api.internal.AbstractTask
import org.gradle.api.internal.file.BreadthFirstDirectoryWalker
import org.gradle.api.internal.file.CopyActionImpl
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.api.file.FileCollection

@RunWith (org.jmock.integration.junit4.JMock)
public class CopyTest extends AbstractTaskTest {
    Copy copyTask;
    BreadthFirstDirectoryWalker walker;
    CopyActionImpl action;

    JUnit4GroovyMockery context = new JUnit4GroovyMockery();

    @Before
    public void setUp() {
        super.setUp()
        context.setImposteriser(ClassImposteriser.INSTANCE)
        walker = context.mock(BreadthFirstDirectoryWalker.class)
        action = context.mock(CopyActionImpl.class)
        copyTask = createTask(Copy.class)
        copyTask.copyAction = action
    }

    public AbstractTask getTask() {
        return copyTask;
    }

    @Test public void executesActionOnExecute() {
        context.checking {
            one(action).from('src')
            one(action).into('dest')
            one(action).execute()
            one(action).getDidWork()
        }

        copyTask.from('src')
        copyTask.into('dest')
        copyTask.copy()
    }
    
    @Test public void usesConventionValuesForDestDirWhenNotSpecified() {
        copyTask.conventionMapping.destinationDir = { new File('dest') }

        context.checking {
            one(action).from('src')
            one(action).getDestDir()
            will(returnValue(null))
            one(action).into(new File('dest'))
            one(action).execute()
            one(action).getDidWork()
        }

        copyTask.from('src')
        copyTask.copy()
    }

    @Test public void usesConventionValuesForSourceWhenNotSpecified() {
        FileCollection source = project.files('src')
        copyTask.conventionMapping.defaultSource = { source }

        context.checking {
            one(action).into('dest')
            one(action).from(source)
            one(action).execute()
            one(action).getDidWork()
        }

        copyTask.into('dest')
        copyTask.copy()
    }
}