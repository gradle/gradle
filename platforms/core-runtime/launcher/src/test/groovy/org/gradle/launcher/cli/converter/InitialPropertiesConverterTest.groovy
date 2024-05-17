/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.cli.CommandLineParser
import spock.lang.Specification

class InitialPropertiesConverterTest extends Specification {
    def "collects -D command-line options"() {
        def converter = new InitialPropertiesConverter()
        def parser = new CommandLineParser()
        converter.configure(parser)
        def commandLine = parser.parse("-Done=12", "-Dtwo")
        def result = converter.convert(commandLine)

        expect:
        result.requestedSystemProperties.size() == 2
        result.requestedSystemProperties.get("one") == "12"
        result.requestedSystemProperties.get("two") == ""
    }
}
