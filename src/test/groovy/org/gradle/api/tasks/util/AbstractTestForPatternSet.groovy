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

/**
 * @author Hans Dockter
 */
abstract class AbstractTestForPatternSet extends GroovyTestCase {
    static final String TEST_PATTERN_1 = 'pattern1'
    static final String TEST_PATTERN_2 = 'pattern2'
    static final String TEST_PATTERN_3 = 'pattern3'

    abstract PatternSet getPatternSet()
    abstract Class getPatternSetType()

    def contextObject

    void setUp() {
        contextObject = new Object()
    }

    void testPatternSet() {
        assert patternSet.contextObject.is(contextObject)
        assertEquals([] as LinkedHashSet, patternSet.includes)

        PatternSet patternSet = patternSetType.newInstance([] as Object[])
        assert patternSet.is(patternSet.contextObject)

        patternSet = patternSetType.newInstance([null] as Object[])
        assert patternSet.is(patternSet.contextObject)
    }

    void testInclude() {
        checkIncludesExcludes(patternSet, 'include', 'includes')
    }

    void testExclude() {
        checkIncludesExcludes(patternSet, 'exclude', 'excludes')
    }

    void checkIncludesExcludes(PatternSet patternSet, String methodName, String propertyName) {
        assert patternSet."$methodName"(TEST_PATTERN_1, TEST_PATTERN_2).is(contextObject)
        assertEquals([TEST_PATTERN_1, TEST_PATTERN_2] as Set, patternSet."$propertyName")
        patternSet."$methodName"(TEST_PATTERN_3)
        assertEquals([TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3] as Set, patternSet."$propertyName")
    }

    void preparePatternSetForAntBuilderTest(PatternSet patternSet) {
        patternSet.include('i')
        patternSet.exclude('e')
    }

    void checkPatternSetForAntBuilderTest(antPatternSet, PatternSet patternSet) {
        assertEquals(patternSet.includes as String[], antPatternSet.getIncludePatterns())
        assertEquals(patternSet.excludes as String[], antPatternSet.getExcludePatterns())
    }

    void testAddToAntBuilder() {
        preparePatternSetForAntBuilderTest(patternSet)
        AntBuilder antBuilder = new AntBuilder()
        patternSet.addToAntBuilder(antBuilder)
//        checkPatternSetForAntBuilderTest(antBuilder.collectorTarget.children[0].realThing, patternSet)
    }


}
