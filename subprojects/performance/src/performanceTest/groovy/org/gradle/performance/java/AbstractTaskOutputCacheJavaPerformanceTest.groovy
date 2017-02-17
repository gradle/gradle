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

package org.gradle.performance.java

import org.gradle.performance.AbstractCrossVersionPerformanceTest

class AbstractTaskOutputCacheJavaPerformanceTest extends AbstractCrossVersionPerformanceTest{

    def setup() {
        /*
         * Since every second build is a 'clean', we need more iterations
         * than usual to get reliable results.
         */
        runner.runs = 40
        runner.setupCleanupOnOddRounds()
        runner.args = ['-Dorg.gradle.cache.tasks=true', '--parallel']
    }

    void setupHeapSize(String heapSize) {
        runner.gradleOpts = ["-Xms${heapSize}", "-Xmx${heapSize}"]
    }

    /**
     * In order to compare the different cache backends we define the scenarios for the
     * tests here.
     */
    def getScenarios() {
        [
            ['bigOldJava', '768m', ['assemble']],
            ['largeWithJUnit', '768m', ['build']],
            ['mixedSize', '1024m', ['assemble']]
        ]
    }
}
