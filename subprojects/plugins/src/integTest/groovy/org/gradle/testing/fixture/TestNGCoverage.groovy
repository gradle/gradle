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

import org.gradle.internal.jvm.Jvm;

class TestNGCoverage {
    final static String NEWEST = Jvm.current().javaVersion.java7Compatible ? '6.9.4' : '6.8.7'
    final static String[] STANDARD_COVERAGE = ['5.14.10', '6.2', '6.8.7', NEWEST]

    /**
     * Adds java plugin and configures TestNG support in given build script file.
     */
    static void enableTestNG(File buildFile) {
        buildFile << """
            apply plugin: 'java'
            repositories { jcenter() }
            dependencies { testCompile "org.testng:testng:${NEWEST}" }
            test.useTestNG()
        """
    }
}
