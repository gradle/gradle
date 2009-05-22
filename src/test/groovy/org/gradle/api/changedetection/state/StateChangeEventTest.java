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
