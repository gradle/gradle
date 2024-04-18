/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.integtests.fixtures.logging

import org.gradle.exemplar.executor.ExecutionMetadata
import org.gradle.exemplar.test.normalizer.OutputNormalizer
import spock.lang.Specification

class ZincScalaCompilerOutputNormalizerTest extends Specification {
    OutputNormalizer outputNormalizer = new ZincScalaCompilerOutputNormalizer()
    ExecutionMetadata executionMetadata = new ExecutionMetadata(new File('building-scala-applications'), [:])

    String expected = """
> Task :app:compileJava NO-SOURCE
> Task :app:compileScala
> Task :app:processResources NO-SOURCE
> Task :app:classes
> Task :app:jar
> Task :app:startScripts
> Task :app:distTar
> Task :app:distZip
> Task :app:assemble
> Task :app:compileTestJava NO-SOURCE
> Task :app:compileTestScala
> Task :app:processTestResources NO-SOURCE
> Task :app:testClasses
> Task :app:test
> Task :app:check
> Task :app:build

BUILD SUCCESSFUL in 0s
7 actionable tasks: 7 executed
""".trim()

    def "successfully normalizes Scala compiler output"() {
        given:
        String input = """
> Task :app:compileJava NO-SOURCE
> Task :app:compileScala
Scala Compiler interface compilation took 1 hrs 20 mins 41.884 secs

> Task :app:processResources NO-SOURCE
> Task :app:classes
> Task :app:jar
> Task :app:startScripts
> Task :app:distTar
> Task :app:distZip
> Task :app:assemble
> Task :app:compileTestJava NO-SOURCE
> Task :app:compileTestScala
> Task :app:processTestResources NO-SOURCE
> Task :app:testClasses
> Task :app:test
> Task :app:check
> Task :app:build

BUILD SUCCESSFUL in 0s
7 actionable tasks: 7 executed
""".trim()

        when:
        String normalized = outputNormalizer.normalize(input, executionMetadata)

        then:
        normalized == expected
    }

    def "successfully normalizes Scala compiler warning"() {
        given:
        String input = """
> Task :app:compileJava NO-SOURCE
> Task :app:compileScala
[Warn] : -target is deprecated: Use -release instead to compile against the correct platform API.
one warning found

> Task :app:processResources NO-SOURCE
> Task :app:classes
> Task :app:jar
> Task :app:startScripts
> Task :app:distTar
> Task :app:distZip
> Task :app:assemble
> Task :app:compileTestJava NO-SOURCE
> Task :app:compileTestScala
[Warn] : -target is deprecated: Use -release instead to compile against the correct platform API.
one warning found

> Task :app:processTestResources NO-SOURCE
> Task :app:testClasses
> Task :app:test
> Task :app:check
> Task :app:build

BUILD SUCCESSFUL in 0s
7 actionable tasks: 7 executed
""".trim()

        when:
        String normalized = outputNormalizer.normalize(input, executionMetadata)

        then:
        normalized == expected
    }
}
