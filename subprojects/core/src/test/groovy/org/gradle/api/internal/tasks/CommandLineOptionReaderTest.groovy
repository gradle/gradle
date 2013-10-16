/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks

import org.gradle.api.DefaultTask
import spock.lang.Specification


class CommandLineOptionReaderTest extends Specification {

    def "can read commandlineoptions of a task"() {
        when:
        Set<CommandLineOption> options = new CommandLineOptionReader().getCommandLineOptions(TestTask)
        then:
        options.size() == 1
        def option = options.iterator().next()
        option.description() == "sets a value"
        option.options().size() == 1
        option.options()[0] == "aValue"
    }


    public static class TestTask extends DefaultTask {
        String value;

        @CommandLineOption(options = "aValue", description = "sets a value")
        public void setValue(String value) {
            this.value = value
        }
    }
}


