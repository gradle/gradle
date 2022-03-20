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
package org.gradle.initialization

import org.gradle.cli.CommandLineArgumentException
import org.gradle.concurrent.ParallelismConfiguration
import org.gradle.internal.concurrent.DefaultParallelismConfiguration
import spock.lang.Specification

class ParallelismConfigurationCommandLineConverterTest extends Specification {
    final def converter = new ParallelismBuildOptions().commandLineConverter()

    def "converts parallel executor"() {
        when:
        def result = convert("--parallel")

        then:
        result.parallelProjectExecutionEnabled == true

        when:
        result = convert("--no-parallel")

        then:
        result.parallelProjectExecutionEnabled == false
    }

    def "converts max workers"() {
        given:
        def maxWorkerCount = 5

        when:
        def result = convert("--max-workers", "$maxWorkerCount")

        then:
        result.maxWorkerCount == maxWorkerCount
    }

    def "converts relative max workers"() {
        when:
        def result = convert("--max-workers", "${relativeMaxWourcerCount}C")

        then:
        result.maxWorkerCount >= 1
        result.maxWorkerCount == Math.max(1, (int) (Runtime.getRuntime().availableProcessors() * relativeMaxWourcerCount.toDouble()))

        where:
        relativeMaxWourcerCount << ["0.1", "0.5", "1.0", "2.0"]
    }

    def "converts empty arguments set max workers to number of processors"() {
        when:
        def result = convert()

        then:
        result.maxWorkerCount == Runtime.getRuntime().availableProcessors()
    }

    def "converts invalid max workers (#value)"() {
        when:
        convert("--max-workers", value);

        then:
        thrown(CommandLineArgumentException)

        where:
        value << ["foo", "0", "0C"]
    }

    def "throws exception for invalid max workers argument when converting"() {
        when:
        convert("--max-workers", "-1")

        then:
        Throwable t = thrown(CommandLineArgumentException)
        t.message == "No argument was provided for command-line option '--max-workers' with description: 'Configure the number of concurrent workers Gradle is allowed to use.'"
    }

    ParallelismConfiguration convert(String... args) {
        return converter.convert(Arrays.asList(args), new DefaultParallelismConfiguration())
    }
}
