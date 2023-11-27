package org.gradle.sample;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ModuleBTest {
    @Test
    public void testSomething() {
        assertEquals("org.gradle.sample", ModuleB.class.getModule().getName());
        assertEquals("text", new ModuleB().print());
    }
}
