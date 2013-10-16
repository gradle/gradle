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
        List<CommandLineOptionReader.CommandLineOptionDescriptor> options = new CommandLineOptionReader().getCommandLineOptions(TestTask)
        then:

        options[0].option.description() == "boolean value"
        options[0].availableValuesType  == Boolean.TYPE
        options[0].annotatedMethod.name == "setBooleanValue"

        options[1].option.description() == "enum value"
        options[1].availableValuesType  == TestEnum
        options[1].annotatedMethod.name == "setEnumValue"

        options[2].option.description() == "string value"
        options[2].availableValuesType  == String
        options[2].annotatedMethod.name == "setStringValue"
    }


    public static class TestTask extends DefaultTask {
        @CommandLineOption(options = "stringValue", description = "string value")
        public void setStringValue(String value) {
        }

        @CommandLineOption(options = "booleanValue", description = "boolean value")
        public void setBooleanValue(boolean value) {
        }

        @CommandLineOption(options = "enumValue", description = "enum value")
        public void setEnumValue(TestEnum value) {
        }

    }

    enum TestEnum {
        ABC, DEF
    }
}


