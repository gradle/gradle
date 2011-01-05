package org.gradle;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class BrokenBeforeClass {
    @BeforeClass
    public static void broken() {
        fail("failed");
    }

    @Test
    public void ok() {
    }
}