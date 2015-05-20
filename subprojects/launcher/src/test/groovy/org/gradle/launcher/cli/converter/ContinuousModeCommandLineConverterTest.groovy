/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.cli.converter

import org.gradle.api.JavaVersion
import org.gradle.cli.CommandLineArgumentException
import org.gradle.cli.CommandLineParser
import org.gradle.launcher.continuous.ContinuousModeParameters
import spock.lang.Specification
import spock.lang.Unroll

class ContinuousModeCommandLineConverterTest extends Specification {

    @Unroll
    def "converts watch options - #options"() {
        when:
        def converted = convert(options, JavaVersion.VERSION_1_7)

        then:
        converted.enabled == enabled

        where:
        options     | enabled
        []          | false
        ['--continuous'] | true
    }

    def "fails on Java 6 with reasonable message"() {
        when:
        convert(["--continuous"], JavaVersion.VERSION_1_6)
        then:
        def e = thrown(CommandLineArgumentException)
        e.message == "Continuous mode (--continuous) is not supported on versions of Java older than 1.7."
    }

    private ContinuousModeParameters convert(Iterable args, JavaVersion javaVersion) {
        CommandLineParser parser = new CommandLineParser()
        def converter = new ContinuousModeCommandLineConverter(javaVersion)
        converter.configure(parser)
        converter.convert(args, new ContinuousModeParameters())
    }
}
