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

package org.gradle.execution.commandline

import org.gradle.execution.TaskSelector
import spock.lang.Specification

import static java.util.Collections.emptyList

/**
 * by Szczepan Faber, created at: 10/8/12
 */
class CommandLineTaskParserSpec extends Specification {

    CommandLineTaskParser parser = new CommandLineTaskParser()
    TaskSelector selector = Mock()

    def "deals with empty input"() {
        expect:
        parser.parseTasks(emptyList(), selector).empty
    }

    //more tests after the next refactoring step.
}
