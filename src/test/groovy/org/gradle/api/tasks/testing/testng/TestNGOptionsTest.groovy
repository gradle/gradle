package org.gradle.api.tasks.testing.testng

import org.junit.Before
import org.junit.Test
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*
import org.gradle.api.tasks.testing.AbstractTestFrameworkOptionsTest
import org.gradle.api.JavaVersion;

/**
 * @author Tom Eyckmans
 */

public class TestNGOptionsTest extends AbstractTestFrameworkOptionsTest<TestNGTestFramework> {

    TestNGOptions testngOptions;

    String[] groups = ['fast', 'unit']

    @Before public void setUp()
    {
        super.setUp(TestNGTestFramework)

        testngOptions = new TestNGOptions(testFrameworkMock, new File("projectDir"))
    }

    @Test public void verifyDefaults()
    {
        assertNull(testngOptions.annotations)

        assertNull(testngOptions.testResources)

        assertFalse(testngOptions.dumpCommand)

        assertTrue(testngOptions.enableAssert)

        assertNotNull(testngOptions.includeGroups)
        assertTrue(testngOptions.includeGroups.empty)

        assertNotNull(testngOptions.excludeGroups)
        assertTrue(testngOptions.excludeGroups.empty)

        assertNull(testngOptions.jvm)

        assertNotNull(testngOptions.listeners)
        assertTrue(testngOptions.listeners.empty)

        assertNull(testngOptions.skippedProperty)

        assertNull(testngOptions.suiteRunnerClass)

        assertNull(testngOptions.parallel)

        assertEquals(testngOptions.threadCount, 1)

        assertEquals(testngOptions.timeOut, Long.MAX_VALUE)

        assertNull(testngOptions.suiteName)

        assertNull(testngOptions.testName)
    }

    @Test public void jdk14SourceCompatibilityAnnotationsDefaulting()
    {
        assertNull(testngOptions.annotations)

        testngOptions.setAnnotationsOnSourceCompatibility(JavaVersion.VERSION_1_4)

        assertEquals(testngOptions.annotations, TestNGOptions.JAVADOC_ANNOTATIONS)
    }

    @Test public void jdk15SourceCompatibilityAnnotationsDefaulting()
    {
        assertNull(testngOptions.annotations)

        testngOptions.setAnnotationsOnSourceCompatibility(JavaVersion.VERSION_1_5)

        assertEquals(testngOptions.annotations, TestNGOptions.JDK_ANNOTATIONS)
    }

    @Test public void jdk16SourceCompatibilityAnnotationsDefaulting()
    {
        assertNull(testngOptions.annotations)

        testngOptions.setAnnotationsOnSourceCompatibility(JavaVersion.VERSION_1_6)

        assertEquals(testngOptions.annotations, TestNGOptions.JDK_ANNOTATIONS)
    }

    @Test public void testIncludeGroups()
    {
        assertTrue(testngOptions.excludeGroups.empty);
        assertTrue(testngOptions.includeGroups.empty);

        testngOptions.includeGroups(groups);

        assertFalse(testngOptions.includeGroups.empty)
        assertThat(testngOptions.includeGroups, hasItems(groups))
        assertTrue(testngOptions.excludeGroups.empty);
    }

    @Test public void testExcludeGroups()
    {
        assertTrue(testngOptions.excludeGroups.empty);
        assertTrue(testngOptions.includeGroups.empty);

        testngOptions.excludeGroups(groups)

        assertFalse(testngOptions.excludeGroups.empty)
        assertThat(testngOptions.excludeGroups, hasItems(groups))
        assertTrue(testngOptions.includeGroups.empty);
    }

}
