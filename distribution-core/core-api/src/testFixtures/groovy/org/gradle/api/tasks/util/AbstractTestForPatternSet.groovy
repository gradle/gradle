/*
 * Copyright 2017 the original author or authors.
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

import spock.lang.Specification

abstract class AbstractTestForPatternSet extends Specification {
    static final String TEST_PATTERN_1 = 'pattern1'
    static final String TEST_PATTERN_2 = 'pattern2'
    static final String TEST_PATTERN_3 = 'pattern3'

    abstract PatternFilterable getPatternSet()

    def testDefaultValues() {
        expect:
        patternSet.includes.empty
        patternSet.excludes.empty
    }

    def testInclude() {
        expect:
        checkIncludesExcludes(patternSet, 'include', 'includes')
    }

    def testExclude() {
        expect:
        checkIncludesExcludes(patternSet, 'exclude', 'excludes')
    }

    void checkIncludesExcludes(PatternFilterable patternSet, String methodName, String propertyName) {
        assert patternSet."$methodName"(TEST_PATTERN_1, TEST_PATTERN_2).is(patternSet)
        assert patternSet."$propertyName" == [TEST_PATTERN_1, TEST_PATTERN_2] as Set

        assert patternSet."$methodName"(TEST_PATTERN_3).is(patternSet)
        assert patternSet."$propertyName" == [TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3] as Set

        patternSet."$propertyName" = {[TEST_PATTERN_2].iterator()} as Iterable
        assert patternSet."$propertyName" == [TEST_PATTERN_2] as Set

        assert patternSet."$methodName"([TEST_PATTERN_3]).is(patternSet)
        assert patternSet."$propertyName" == [TEST_PATTERN_2, TEST_PATTERN_3] as Set
    }

    void preparePatternSetForAntBuilderTest(PatternFilterable patternSet) {
        patternSet.include('i')
        patternSet.exclude('e')
    }

    void checkPatternSetForAntBuilderTest(antPatternSet, PatternFilterable patternSet) {
        assert (patternSet.includes as String[]).is(antPatternSet.getIncludePatterns())
        assert (patternSet.excludes as String[]).is(antPatternSet.getExcludePatterns())
    }
}
