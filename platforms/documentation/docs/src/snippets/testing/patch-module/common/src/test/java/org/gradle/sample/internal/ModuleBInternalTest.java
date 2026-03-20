package org.gradle.sample.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ModuleBInternalTest {
    @Test
    public void testSomething() {
        assertEquals("org.gradle.sample", ModuleBInternal.class.getModule().getName());
        assertEquals("internal text", new ModuleBInternal().print());
    }
}
