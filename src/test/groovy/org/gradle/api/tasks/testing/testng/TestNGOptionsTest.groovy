package org.gradle.api.tasks.testing.testng

import org.junit.Before
import org.junit.Test
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

/**
 * @author Tom Eyckmans
 */

public class TestNGOptionsTest {

    TestNGOptions testngOptions;

    @Before public void setUp()
    {
        testngOptions = new TestNGOptions()
    }

    @Test public void verifyDefaults()
    {
        assertThat(testngOptions.annotations, equalToIgnoringCase('JDK'))

        assertNull(testngOptions.testResources)

        assertFalse(testngOptions.dumpCommand)

        assertTrue(testngOptions.enableAssert)

        assertNotNull(testngOptions.groups)
        assertTrue(testngOptions.groups.empty)

        assertNotNull(testngOptions.excludedGroups)
        assertTrue(testngOptions.excludedGroups.empty)

        assertNull(testngOptions.jvm)

        assertNotNull(testngOptions.listeners)
        assertTrue(testngOptions.listeners.empty)

        assertNull(testngOptions.outputDir)

        assertNull(testngOptions.skippedProperty)

        assertNull(testngOptions.suiteRunnerClass)

        assertNull(testngOptions.parallel)

        assertEquals(testngOptions.threadCount, 1)

        assertEquals(testngOptions.timeOut, Long.MAX_VALUE)

        assertNull(testngOptions.workingDir)

        assertNull(testngOptions.suiteName)

        assertNull(testngOptions.testName)
    }

}