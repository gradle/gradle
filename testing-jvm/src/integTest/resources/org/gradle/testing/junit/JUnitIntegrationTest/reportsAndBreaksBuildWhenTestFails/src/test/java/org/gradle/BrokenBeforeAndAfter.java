package org.gradle;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class BrokenBeforeAndAfter {
    @Before
    public void brokenBefore() {
        fail("before failed");
    }

    @After
    public void brokenAfter() {
        fail("after failed");
    }

    @Test
    public void ok() {
    }
}