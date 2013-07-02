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
package org.gradle.api.tasks

import org.gradle.api.internal.AbstractTask
import org.gradle.api.internal.file.copy.FileCopyActionImpl
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith (org.jmock.integration.junit4.JMock)
public class CopyTest extends AbstractTaskTest {
    Copy copyTask;
    FileCopyActionImpl action;

    JUnit4GroovyMockery context = new JUnit4GroovyMockery();

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE)
        action = context.mock(FileCopyActionImpl.class)
        copyTask = createTask(Copy.class)
        copyTask.copyAction = action
    }

    public AbstractTask getTask() {
        return copyTask;
    }

    @Test public void executesActionOnExecute() {
        context.checking {
            one(action).hasSource(); will(returnValue(true))
            exactly(2).of(action).getDestinationDir(); will(returnValue(new File('dest')))
            one(action).execute()
            one(action).getDidWork()
        }

        copyTask.copy()
    }
    
    @Test public void usesConventionValuesForDestDirWhenNotSpecified() {
        copyTask.conventionMapping.destinationDir = { new File('convention') }

        context.checking {
            exactly(2).of(action).getDestinationDir()
            will(returnValue(null))
            one(action).into(new File('convention'))
            one(action).hasSource(); will(returnValue(true))
        }

        copyTask.configureRootSpec()
    }

    @Test public void doesNotUseConventionValueForDestDirWhenSpecified() {
        copyTask.conventionMapping.destinationDir = { new File('convention') }

        context.checking {
            one(action).getDestinationDir()
            will(returnValue(new File('dest')))
            one(action).hasSource(); will(returnValue(true))
        }

        copyTask.configureRootSpec()
    }
}
