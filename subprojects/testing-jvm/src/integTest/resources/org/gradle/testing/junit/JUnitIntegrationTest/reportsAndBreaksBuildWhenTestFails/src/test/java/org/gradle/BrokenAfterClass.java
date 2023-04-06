package org.gradle;

import org.junit.AfterClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class BrokenAfterClass {
    @AfterClass
    public static void broken() {
        fail("failed");
    }

    @Test
    public void ok() {
    }
}