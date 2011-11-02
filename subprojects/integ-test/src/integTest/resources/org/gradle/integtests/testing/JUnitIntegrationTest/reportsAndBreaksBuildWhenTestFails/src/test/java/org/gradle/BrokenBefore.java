package org.gradle;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class BrokenBefore {
    @Before
    public void broken() {
        fail("failed");
    }

    @Test
    public void ok() {
    }
}