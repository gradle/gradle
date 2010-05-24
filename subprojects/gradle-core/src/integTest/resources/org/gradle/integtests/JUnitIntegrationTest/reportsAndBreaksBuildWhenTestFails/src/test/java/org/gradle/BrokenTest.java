package org.gradle;

import org.junit.Test;

import static org.junit.Assert.*;

public class BrokenTest {
    @Test
    public void broken() {
        fail("failed");
    }
}