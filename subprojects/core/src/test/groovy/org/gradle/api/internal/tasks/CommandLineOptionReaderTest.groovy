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
import org.gradle.cli.CommandLineArgumentException
import spock.lang.Specification


class CommandLineOptionReaderTest extends Specification {

    CommandLineOptionReader reader

    def setup() {
        reader = new CommandLineOptionReader()
    }

    def "can read commandlineoptions of a task"() {
        when:
        List<CommandLineOptionReader.CommandLineOptionDescriptor> options = reader.getCommandLineOptions(TestTask1)
        then:

        options[0].option.description() == "boolean value"
        options[0].availableValuesType == Boolean.TYPE
        options[0].annotatedMethod.name == "setBooleanValue"

        options[1].option.description() == "enum value"
        options[1].availableValuesType == TestEnum
        options[1].annotatedMethod.name == "setEnumValue"

        options[2].option.description() == "object value"
        options[2].availableValuesType == Object
        options[2].annotatedMethod.name == "setObjectValue"

        options[3].option.description() == "string value"
        options[3].availableValuesType == String
        options[3].annotatedMethod.name == "setStringValue"
    }

    def "fail when multiple methods define same option"() {
        when:
        reader.getCommandLineOptions(TestTask2)
        then:
        def e = thrown(CommandLineArgumentException)
        e.message == "Option 'stringValue' linked to multiple methods in class 'org.gradle.api.internal.tasks.CommandLineOptionReaderTest\$TestTask2'."
    }

    def "ignores static methods"() {
        when:
        List<CommandLineOptionReader.CommandLineOptionDescriptor> options = reader.getCommandLineOptions(TestTask3)
        then:
        options.isEmpty()
    }


    def "fail when parameter cannot be converted from the command-line"() {
        when:
        reader.getCommandLineOptions(TestTask5)
        then:
        def e = thrown(CommandLineArgumentException)
        e.message == "Option 'fileValue' cannot be casted to parameter type 'java.io.File' in class 'org.gradle.api.internal.tasks.CommandLineOptionReaderTest\$TestTask5'."
    }


    def "fails when method has > 1 parameter"() {
        when:
        reader.getCommandLineOptions(TestTask4)
        then:
        def e = thrown(CommandLineArgumentException)
        e.message == "Option 'stringValue' cannot be linked to methods with multiple parameters in class 'org.gradle.api.internal.tasks.CommandLineOptionReaderTest\$TestTask4#setStrings'."
    }

    public static class TestTask1 extends DefaultTask {
        @CommandLineOption(options = "stringValue", description = "string value")
        public void setStringValue(String value) {
        }

        @CommandLineOption(options = "objectValue", description = "object value")
        public void setObjectValue(Object value) {
        }

        @CommandLineOption(options = "booleanValue", description = "boolean value")
        public void setBooleanValue(boolean value) {
        }

        @CommandLineOption(options = "enumValue", description = "enum value")
        public void setEnumValue(TestEnum value) {
        }
    }

    public static class TestTask2 extends DefaultTask {
        @CommandLineOption(options = "stringValue", description = "string value")
        public void setStringValue(String value) {
        }

        @CommandLineOption(options = "stringValue", description = "string value")
        public void setStringValue2(String value) {
        }
    }

    public static class TestTask3 extends DefaultTask {
        @CommandLineOption(options = "staticString", description = "string value")
        public static void setStaticString(String value) {
        }
    }

    public static class TestTask4 extends DefaultTask {
        @CommandLineOption(options = 'stringValue', description = "string value")
        public void setStrings(String value1, String value2) {
        }
    }

    public static class TestTask5 extends DefaultTask {
        @CommandLineOption(options = 'fileValue', description = "file value")
        public void setStrings(File file) {
        }
    }

    enum TestEnum {
        ABC, DEF
    }
}


