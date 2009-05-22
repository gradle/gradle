package org.gradle.api.changedetection.state;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Tom Eyckmans
 */
public class StateFileItemTest {

    private String okKey = "okKey";
    private String okDigest = "okDigest";

    private StateFileItem stateFileItem;

    @Test ( expected = IllegalArgumentException.class )
    public void createWithNullKey() {
        stateFileItem = new StateFileItem(null, okDigest);
    }

    @Test ( expected = IllegalArgumentException.class )
    public void createWithNullDigest() {
        stateFileItem = new StateFileItem(okKey, null);
    }

    @Test
    public void createWithEmptyKey() {
        stateFileItem = new StateFileItem("", okDigest);

        assertNotNull(stateFileItem);
        assertEquals("", stateFileItem.getKey());
        assertEquals(okDigest, stateFileItem.getDigest());
    }

    @Test
    public void create() {
        stateFileItem = new StateFileItem(okKey, okDigest);

        assertNotNull(stateFileItem);
        assertEquals(okKey, stateFileItem.getKey());
        assertEquals(okDigest, stateFileItem.getDigest());
    }
}
