package org.gradle;

import org.junit.Test;

import static org.junit.Assert.*;

public class BrokenTest {
    @Test
    public void failure() {
        fail("failed");
    }

    @Test
    public void broken() {
        throw new IllegalStateException("html: <> cdata: ]]>");
    }
}