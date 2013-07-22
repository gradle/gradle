/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.util

import org.junit.Before
import org.junit.Test

import static org.gradle.util.Matchers.isEmpty
import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.sameInstance
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertThat

abstract class AbstractTestForPatternSet {
    static final String TEST_PATTERN_1 = 'pattern1'
    static final String TEST_PATTERN_2 = 'pattern2'
    static final String TEST_PATTERN_3 = 'pattern3'

    abstract PatternFilterable getPatternSet()

    def contextObject

    @Before public void setUp()  {
        contextObject = new Object()
    }

    @Test public void testDefaultValues() {
        assertThat(patternSet.includes, isEmpty())
        assertThat(patternSet.excludes, isEmpty())
    }

    @Test public void testInclude() {
        checkIncludesExcludes(patternSet, 'include', 'includes')
    }

    @Test public void testExclude() {
        checkIncludesExcludes(patternSet, 'exclude', 'excludes')
    }

    void checkIncludesExcludes(PatternFilterable patternSet, String methodName, String propertyName) {
        assertThat(patternSet."$methodName"(TEST_PATTERN_1, TEST_PATTERN_2), sameInstance(patternSet))
        assertThat(patternSet."$propertyName", equalTo([TEST_PATTERN_1, TEST_PATTERN_2] as Set))

        assertThat(patternSet."$methodName"(TEST_PATTERN_3), sameInstance(patternSet))
        assertThat(patternSet."$propertyName", equalTo([TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3] as Set))

        patternSet."$propertyName" = {[TEST_PATTERN_2].iterator()} as Iterable
        assertThat(patternSet."$propertyName", equalTo([TEST_PATTERN_2] as Set))

        assertThat(patternSet."$methodName"([TEST_PATTERN_3]), sameInstance(patternSet))
        assertThat(patternSet."$propertyName", equalTo([TEST_PATTERN_2, TEST_PATTERN_3] as Set))
    }

    void preparePatternSetForAntBuilderTest(PatternFilterable patternSet) {
        patternSet.include('i')
        patternSet.exclude('e')
    }

    void checkPatternSetForAntBuilderTest(antPatternSet, PatternFilterable patternSet) {
        assertEquals(patternSet.includes as String[], antPatternSet.getIncludePatterns())
        assertEquals(patternSet.excludes as String[], antPatternSet.getExcludePatterns())
    }
}
