/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.testing.fixture

import com.google.common.collect.ObjectArrays
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.internal.jvm.Jvm

class TestNGCoverage {
    final static String NEWEST = '7.5'
    final static String INITIAL_BROKEN_ICLASS_LISTENER = '6.9.10' // introduces initial, buggy IClassListener
    final static String FIXED_ICLASS_LISTENER = '6.9.13.3' // introduces fixed IClassListener
    final static String[] STANDARD_COVERAGE = ['5.14.10', '6.2', '6.8.7', '6.9.13.6', NEWEST]
    final static String[] STANDARD_COVERAGE_WITH_INITIAL_ICLASS_LISTENER =  ObjectArrays.concat(INITIAL_BROKEN_ICLASS_LISTENER, STANDARD_COVERAGE)
    final static String LEGACY =  '5.12.1' // lacks TestNG#setConfigFailurePolicy method
    final static String[] STANDARD_COVERAGE_WITH_LEGACY =  ObjectArrays.concat(LEGACY, STANDARD_COVERAGE_WITH_INITIAL_ICLASS_LISTENER)
    final static String[] PRESERVE_ORDER = Jvm.current().javaVersion.java7Compatible ? ['5.14.6', '6.1.1', '6.9.4', NEWEST] : ['5.14.6', '6.1.1'] // skipped NEWEST (6.8.7) because of cbeust/testng#639
    final static String[] GROUP_BY_INSTANCES = ['6.1', '6.8.7', NEWEST]

    /**
     * Adds java plugin and configures TestNG support in given build script file.
     */
    static void enableTestNG(File buildFile, version = NEWEST) {
        buildFile << """
            apply plugin: 'java'
            ${RepoScriptBlockUtil.mavenCentralRepository()}
            testing {
                suites {
                    test {
                        useTestNG('${version}')
                    }
                }
            }
        """
    }
}
