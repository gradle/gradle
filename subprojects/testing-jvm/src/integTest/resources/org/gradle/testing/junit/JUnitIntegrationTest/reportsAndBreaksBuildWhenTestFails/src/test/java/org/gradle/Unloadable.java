package org.gradle;

import org.junit.Test;

import static org.junit.Assert.*;

public class Unloadable {
    static {
        fail("failed");
    }

    @Test
    public void ok() {
    }

    @Test
    public void ok2() {
    }
}