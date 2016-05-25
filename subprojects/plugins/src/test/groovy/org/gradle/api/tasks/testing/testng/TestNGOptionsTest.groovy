/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.tasks.testing.testng

import org.gradle.api.internal.tasks.testing.testng.TestNGTestFramework
import org.gradle.api.tasks.testing.AbstractTestFrameworkOptionsTest
import org.junit.Before
import org.junit.Test

import static org.hamcrest.Matchers.hasItems
import static org.junit.Assert.*

public class TestNGOptionsTest extends AbstractTestFrameworkOptionsTest<TestNGTestFramework> {

    TestNGOptions testngOptions;

    String[] groups = ['fast', 'unit']

    @Before public void setUp()
    {
        super.setUp(TestNGTestFramework)

        testngOptions = new TestNGOptions(new File("projectDir"))
    }

    @Test public void verifyDefaults()
    {
        assertNotNull(testngOptions.includeGroups)
        assertTrue(testngOptions.includeGroups.empty)

        assertNotNull(testngOptions.excludeGroups)
        assertTrue(testngOptions.excludeGroups.empty)

        assertNotNull(testngOptions.listeners)
        assertTrue(testngOptions.listeners.empty)

        assertNull(testngOptions.parallel)

        assertEquals(testngOptions.threadCount, 1)

        assertEquals('Gradle suite', testngOptions.suiteName)

        assertEquals('Gradle test', testngOptions.testName)

        assertEquals(TestNGOptions.DEFAULT_CONFIG_FAILURE_POLICY, testngOptions.configFailurePolicy)

        assertFalse(testngOptions.preserveOrder)

        assertFalse(testngOptions.groupByInstances)
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
