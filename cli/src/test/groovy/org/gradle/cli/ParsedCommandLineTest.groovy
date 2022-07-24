/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.cli

import spock.lang.Specification

class ParsedCommandLineTest extends Specification {

    def "knows if contains an option"() {
        when:
        def line = new ParsedCommandLine([cmdOption("a"), cmdOption("b"), cmdOption("c")]);
        line.addOption("b", cmdOption("b"))

        then:
        !line.hasOption("a")
        line.hasOption("b")

        !line.hasAnyOption(["a", "c"])
        line.hasAnyOption(["b", "c"])
    }

    private CommandLineOption cmdOption(String opt) {
        new CommandLineOption([opt])
    }

    def "keeps track of removed options"() {
        when:
        def line = new ParsedCommandLine([cmdOption("a"), cmdOption("b"), cmdOption("c")]);
        line.addOption("a", cmdOption("a"))
        line.addOption("b", cmdOption("b"))
        // allowOneOf(a, b)
        line.removeOption(cmdOption("a"))

        then:
        !line.hasOption("a")
        line.hasOption("b")

        line.hadOptionRemoved("a")

        !line.hasAnyOption(["a", "c"])
        line.hasAnyOption(["b", "c"])
    }
}
