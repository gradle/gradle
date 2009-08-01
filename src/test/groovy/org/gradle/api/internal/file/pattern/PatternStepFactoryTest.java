package org.gradle.api.internal.file.pattern;

import org.junit.Test;
import static org.junit.Assert.*;

public class PatternStepFactoryTest {
    @Test public void testGreedy() {
        PatternStep step = PatternStepFactory.getStep("**", true, true);
        assertTrue(step.isGreedy());
        assertTrue(step.matches("anything", true));
        assertTrue(step.matches("anything", false));
    }

    @Test public void testNormal() {
        PatternStep step = PatternStepFactory.getStep("*.jsp", true, true);
        assertFalse(step.isGreedy());
        assertTrue(step.matches("fred.jsp", true));

        // check case sensitivity param
        assertFalse(step.matches("fred.JSP", true));
        step = PatternStepFactory.getStep("*.jsp", true, false);
        assertTrue(step.matches("fred.JSP", true));
    }
}
