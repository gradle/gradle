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

              - build/classes/main/ has overlap between:
                  - :compileGroovy.destinationDir (org.gradle.api.tasks.compile.GroovyCompile)
                  - :compileJava.destinationDir (org.gradle.api.tasks.compile.JavaCompile)
              - build/classes/test has overlap between:
                  - :compileTestGroovy.destinationDir (org.gradle.api.tasks.compile.GroovyCompile)
                  - :compileTestJava.destinationDir (org.gradle.api.tasks.compile.JavaCompile)

              Tasks affected by type:

                - org.gradle.api.tasks.compile.GroovyCompile
                  - :compileGroovy
                  - :compileTestGroovy
                - org.gradle.api.tasks.compile.JavaCompile
                  - :compileJava
                  - :compileTestJava

            Detailed cache statistics

              All tasks - 13 tasks took 50 ms (avg 3.57 ms, stddev 6.77 ms, min 0 ms, max 27 ms)
                FROM_CACHE - 2 tasks took 33 ms (avg 16.50 ms, stddev 10.50 ms, min 6 ms, max 27 ms)
                  Cacheable - 2 tasks took 33 ms (avg 16.50 ms, stddev 10.50 ms, min 6 ms, max 27 ms)
                    org.gradle.api.tasks.bundling.Jar - 1 task took 6 ms
                    org.gradle.api.tasks.compile.JavaCompile (*) - 1 task took 27 ms
                UP_TO_DATE - 5 tasks took 2 ms (avg 0.40 ms, stddev 0.49 ms, min 0 ms, max 1 ms)
                  Not cacheable - 5 tasks took 2 ms (avg 0.40 ms, stddev 0.49 ms, min 0 ms, max 1 ms)
                    org.gradle.api.DefaultTask - 5 tasks took 2 ms (avg 0.40 ms, stddev 0.49 ms, min 0 ms, max 1 ms)
                NO_SOURCE - 6 tasks took 9 ms (avg 1.50 ms, stddev 0.76 ms, min 1 ms, max 3 ms)
                  Not cacheable - 2 tasks took 2 ms (avg 1.00 ms, stddev 0.00 ms, min 1 ms, max 1 ms)
                    org.gradle.language.jvm.tasks.ProcessResources - 2 tasks took 3 ms (avg 1.50 ms, stddev 0.50 ms, min 1 ms, max 2 ms)
                  Cacheable - 4 tasks took 8 ms (avg 2.00 ms, stddev 0.71 ms, min 1 ms, max 3 ms)
                    org.gradle.api.tasks.compile.GroovyCompile - 2 tasks took 5 ms (avg 2.50 ms, stddev 0.50 ms, min 2 ms, max 3 ms)
                    org.gradle.api.tasks.compile.JavaCompile - 1 task took 1 ms
                    org.gradle.api.tasks.testing.Test - 1 task took 2 ms
                  
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
            "FROM_CACHE","Cacheable","Jar","org.gradle.api.tasks.bundling",1,6,6.0,0.0,6,6
            "FROM_CACHE","Cacheable","JavaCompile (*)","org.gradle.api.tasks.compile",1,5,5.0,0.0,5,5
            "UP_TO_DATE","Not cacheable","DefaultTask","org.gradle.api",5,1,0.2,0.39999999999999997,0,1
            "NO_SOURCE","Cacheable","GroovyCompile","org.gradle.api.tasks.compile",2,0,0.0,0.0,0,0
            "NO_SOURCE","Cacheable","JavaCompile","org.gradle.api.tasks.compile",1,2,2.0,0.0,2,2
            "NO_SOURCE","Cacheable","Test","org.gradle.api.tasks.testing",1,1,1.0,0.0,1,1
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
