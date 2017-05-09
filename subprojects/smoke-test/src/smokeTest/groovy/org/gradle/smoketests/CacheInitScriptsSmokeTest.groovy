/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.smoketests

import static org.gradle.testkit.runner.TaskOutcome.FROM_CACHE

class CacheInitScriptsSmokeTest extends AbstractSmokeTest {

    static final NUMBER_PATTERN = /\d+(?:\.\d+)?/
    static final TIME_PATTERN = /\d+(?:\.\d+)? ms/

    def 'cache init script plain text output'() {
        given:
        def expectedLines = """
            Overlapping task outputs while executing 'build':

              No tasks with overlapping outputs found

            Detailed cache statistics

              All tasks - 13 tasks took 67 ms (avg 5.15 ms, stddev 6.48 ms, min 0 ms, max 23 ms)
                FROM_CACHE - 1 task took 23 ms
                  Cacheable - 1 task took 23 ms
                    org.gradle.api.tasks.compile.JavaCompile (*) - 1 task took 23 ms
                UP_TO_DATE - 3 tasks took 4 ms (avg 1.33 ms, stddev 0.47 ms, min 1 ms, max 2 ms)
                  Not cacheable - 3 tasks took 4 ms (avg 1.33 ms, stddev 0.47 ms, min 1 ms, max 2 ms)
                    org.gradle.api.DefaultTask - 3 tasks took 4 ms (avg 1.33 ms, stddev 0.47 ms, min 1 ms, max 2 ms)
                NO_SOURCE - 6 tasks took 25 ms (avg 4.17 ms, stddev 3.02 ms, min 1 ms, max 10 ms)
                  Not cacheable - 6 tasks took 4 ms (avg 2.00 ms, stddev 1.00 ms, min 1 ms, max 3 ms)
                    org.gradle.api.tasks.compile.GroovyCompile - 2 tasks took 9 ms (avg 4.50 ms, stddev 1.50 ms, min 3 ms, max 6 ms)
                    org.gradle.api.tasks.compile.JavaCompile - 1 task took 2 ms
                    org.gradle.api.tasks.testing.Test - 1 task took 10 ms
                    org.gradle.language.jvm.tasks.ProcessResources - 2 tasks took 4 ms (avg 2.00 ms, stddev 1.00 ms, min 1 ms, max 3 ms)
                EXECUTED - 3 tasks took 15 ms (avg 5.00 ms, stddev 6.38 ms, min 0 ms, max 14 ms)
                  Not cacheable - 3 tasks took 15 ms (avg 5.00 ms, stddev 6.38 ms, min 0 ms, max 14 ms)
                    org.gradle.api.DefaultTask - 2 tasks took 1 ms (avg 0.50 ms, stddev 0.50 ms, min 0 ms, max 1 ms)
                    org.gradle.api.tasks.bundling.Jar - 1 task took 14 ms

              (*) denotes tasks with custom actions
        """.stripIndent().split("\n").findAll { !it.empty }.collect { it.replaceAll(TIME_PATTERN, "TIME") }

        useSample("cache-init-scripts")

        // Prepare the cache
        runner('-I', 'taskCacheInit.gradle', 'build').build()
        runner('clean').build()

        when:
        def result = runner('-I', 'taskCacheInit.gradle', 'build').build()
        println result.output
        def actualLines = result.output.split("\n").collect { it.replaceAll(TIME_PATTERN, "TIME") }

        then:
        result.task(':compileJava').outcome == FROM_CACHE
        expectedLines.each { expectedLine ->
            assert actualLines.contains(expectedLine)
        }
    }

    def 'cache init script CSV output'() {
        given:
        def expectedLines = """
            "Build time",746
            "Outcome","Cacheable","Task","Package","Count","Sum","Mean","StdDev","Min","Max"
            "EXECUTED","Not cacheable","Jar","org.gradle.api.tasks.bundling",1,6,6.0,0.0,6,6
            "FROM_CACHE","Cacheable","JavaCompile (*)","org.gradle.api.tasks.compile",1,5,5.0,0.0,5,5
            "UP_TO_DATE","Not cacheable","DefaultTask","org.gradle.api",5,1,0.2,0.39999999999999997,0,1
            "NO_SOURCE","Not cacheable","GroovyCompile","org.gradle.api.tasks.compile",2,0,0.0,0.0,0,0
            "NO_SOURCE","Not cacheable","JavaCompile","org.gradle.api.tasks.compile",1,2,2.0,0.0,2,2
            "NO_SOURCE","Not cacheable","Test","org.gradle.api.tasks.testing",1,1,1.0,0.0,1,1
            "NO_SOURCE","Not cacheable","ProcessResources","org.gradle.language.jvm.tasks",2,1,0.5,0.5,0,1
        """.stripIndent().split("\n").findAll { !it.empty }.collect { it.replaceAll(NUMBER_PATTERN, "NUMBER") }

        useSample("cache-init-scripts")

        // Prepare the cache
        runner('-I', 'taskCacheInit.gradle', 'build').build()
        runner('clean').build()

        when:
        def result = runner('-I', 'taskCacheInit.gradle', 'build', '-Dcsv').build()
        def actualLines = result.output.split("\n").collect { it.replaceAll(NUMBER_PATTERN, "NUMBER") }

        then:
        result.task(':compileJava').outcome == FROM_CACHE
        expectedLines.each { expectedLine ->
            assert actualLines.contains(expectedLine)
        }
    }
}
