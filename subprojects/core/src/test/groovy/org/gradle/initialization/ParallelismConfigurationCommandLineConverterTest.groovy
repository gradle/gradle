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
import spock.lang.Specification
import spock.lang.Unroll

class ParallelismConfigurationCommandLineConverterTest extends Specification {
    final def converter = new ParallelismConfigurationCommandLineConverter()

    def "converts parallel executor"() {
        when:
        def result = convert("--parallel")

        then:
        result.parallelProjectExecutionEnabled == true
    }

    def "converts max workers"() {
        given:
        def maxWorkerCount = 5

        when:
        def result = convert("--max-workers", "$maxWorkerCount")

        then:
        result.maxWorkerCount == maxWorkerCount
    }

    def "converts empty arguments set max workers to number of processors"() {
        when:
        def result = convert()

        then:
        result.maxWorkerCount == Runtime.getRuntime().availableProcessors()
    }

    @Unroll
    def "converts invalid max workers (#value)"() {
        when:
        convert("--max-workers", value);

        then:
        thrown(CommandLineArgumentException)

        where:
        value << ["foo", "-1", "0"]
    }

    ParallelismConfiguration convert(String... args) {
        return converter.convert(Arrays.asList(args), new DefaultParallelismConfiguration())
    }
}
