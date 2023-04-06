package org.gradle;

import org.junit.Test;

import static org.junit.Assert.*;

public class BrokenConstructor {
    public BrokenConstructor() {
        fail("failed");
    }

    @Test
    public void ok() {
    }
}