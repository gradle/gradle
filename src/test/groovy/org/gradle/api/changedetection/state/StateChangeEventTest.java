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

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.lib.legacy.ClassImposteriser;

import java.io.File;

/**
 * @author Tom Eyckmans
 */
public class StateChangeEventTest {

    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery();

    private StateFileItem oldStateMock;
    private StateFileItem newStateMock;
    private File okFileOrDirectory = new File("fileOrDirectory");

    private StateChangeEvent stateChangeEvent;

    @Before
    public void setUp() throws Exception {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        
        oldStateMock = context.mock(StateFileItem.class, "old");
        newStateMock = context.mock(StateFileItem.class, "new");
    }

    @Test ( expected = IllegalArgumentException.class )
    public void createWithNullFile() {
        stateChangeEvent = new StateChangeEvent(null, oldStateMock, newStateMock);
    }


    @Test ( expected = IllegalArgumentException.class )
    public void createWithNullStates() {
        stateChangeEvent = new StateChangeEvent(okFileOrDirectory, null, null);
    }

    @Test
    public void createDeletedItemEvent() {
        stateChangeEvent = new StateChangeEvent(okFileOrDirectory, oldStateMock, null);

        assertNotNull(stateChangeEvent);
        assertEquals(okFileOrDirectory, stateChangeEvent.getFileOrDirectory());
        assertEquals(oldStateMock, stateChangeEvent.getOldState());
        assertNull(stateChangeEvent.getNewState());
    }

    @Test
    public void createCreatedItemEvent() {
        stateChangeEvent = new StateChangeEvent(okFileOrDirectory, null, newStateMock);

        assertNotNull(stateChangeEvent);
        assertEquals(okFileOrDirectory, stateChangeEvent.getFileOrDirectory());
        assertNull(stateChangeEvent.getOldState());
        assertEquals(newStateMock, stateChangeEvent.getNewState());
    }

    @Test
    public void createChangedItemEvent() {
        stateChangeEvent = new StateChangeEvent(okFileOrDirectory, oldStateMock, newStateMock);

        assertNotNull(stateChangeEvent);
        assertEquals(okFileOrDirectory, stateChangeEvent.getFileOrDirectory());
        assertEquals(oldStateMock, stateChangeEvent.getOldState());
        assertEquals(newStateMock, stateChangeEvent.getNewState());
    }
}
