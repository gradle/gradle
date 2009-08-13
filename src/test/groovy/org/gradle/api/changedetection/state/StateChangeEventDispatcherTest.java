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

package org.gradle.api.changedetection.state;

import org.junit.Before;
import org.junit.Test;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.api.changedetection.ChangeProcessor;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.io.File;

/**
 * @author Tom Eyckmans
 */
public class StateChangeEventDispatcherTest {

    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery();

    private BlockingQueue<StateChangeEvent> blockingQueueMock;
    private ChangeProcessor changeProcessorMock;
    private StateChangeEvent stateChangeEventMock;
    private StateFileItem oldStateMock;
    private StateFileItem newStateMock;
    private File fileOrDirectory = new File("fileOrDirectory");

    private StateChangeEventDispatcher stateChangeEventDispatcher;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        blockingQueueMock = context.mock(BlockingQueue.class);
        changeProcessorMock = context.mock(ChangeProcessor.class);
        stateChangeEventMock = context.mock(StateChangeEvent.class);
        oldStateMock = context.mock(StateFileItem.class, "old");
        newStateMock = context.mock(StateFileItem.class, "new");

        stateChangeEventDispatcher = new StateChangeEventDispatcher(blockingQueueMock, 100L, TimeUnit.MILLISECONDS, changeProcessorMock);
    }

    @Test
    public void itemDeleted() {
        context.checking(new Expectations(){{
            one(stateChangeEventMock).getFileOrDirectory(); will(returnValue(fileOrDirectory));
            one(stateChangeEventMock).getNewState(); will(returnValue(null));
            one(stateChangeEventMock).getOldState(); will(returnValue(oldStateMock));
            one(changeProcessorMock).deletedFile(fileOrDirectory);
        }});

        stateChangeEventDispatcher.consume(stateChangeEventMock);
    }

    @Test
    public void itemChanged() {
        context.checking(new Expectations(){{
            one(stateChangeEventMock).getFileOrDirectory(); will(returnValue(fileOrDirectory));
            one(stateChangeEventMock).getNewState(); will(returnValue(newStateMock));
            one(stateChangeEventMock).getOldState(); will(returnValue(oldStateMock));
            one(changeProcessorMock).changedFile(fileOrDirectory);
        }});

        stateChangeEventDispatcher.consume(stateChangeEventMock);
    }

    @Test
    public void itemCreated() {
        context.checking(new Expectations(){{
            one(stateChangeEventMock).getFileOrDirectory(); will(returnValue(fileOrDirectory));
            one(stateChangeEventMock).getNewState(); will(returnValue(newStateMock));
            one(stateChangeEventMock).getOldState(); will(returnValue(null));
            one(changeProcessorMock).createdFile(fileOrDirectory);
        }});

        stateChangeEventDispatcher.consume(stateChangeEventMock);
    }
}
