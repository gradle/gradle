package org.gradle;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

public class BrokenAfter {
    @After
    public void broken() {
        fail("failed");
    }

    @Test
    public void ok() {
    }
}