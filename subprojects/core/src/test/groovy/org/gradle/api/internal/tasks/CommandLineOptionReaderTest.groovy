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
import org.gradle.api.Project
import org.gradle.cli.CommandLineArgumentException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification


class CommandLineOptionReaderTest extends Specification {

    CommandLineOptionReader reader
    Project project

    def setup() {
        reader = new CommandLineOptionReader()
        project = ProjectBuilder.builder().build();
    }

    def "can read commandlineoptions of a task"() {
        when:
        List<InstanceCommandLineOptionDescriptor> options = reader.getCommandLineOptions(Mock(TestTask1))
        then:
        options[0].option.description() == "simple flag"
        options[0].argumentType == Void.TYPE
        options[0].configurationMethod.name == "setActive"

        options[1].option.description() == "boolean value"
        options[1].argumentType == Void.TYPE
        options[1].configurationMethod.name == "setBooleanValue"

        options[2].option.description() == "enum value"
        options[2].argumentType == TestEnum
        options[2].configurationMethod.name == "setEnumValue"

        options[3].option.description() == "object value"
        options[3].argumentType == Object
        options[3].configurationMethod.name == "setObjectValue"

        options[4].option.description() == "string value"
        options[4].argumentType == String
        options[4].configurationMethod.name == "setStringValue"
    }

    def "fail when multiple methods define same option"() {
        when:
        reader.getCommandLineOptions(project.tasks.create("aTask", TestTask2))
        then:
        def e = thrown(CommandLineArgumentException)
        e.message == "Option 'stringValue' linked to multiple methods in class 'org.gradle.api.internal.tasks.CommandLineOptionReaderTest\$TestTask2_Decorated'."
    }

    def "ignores static methods"() {
        when:
        List<InstanceCommandLineOptionDescriptor> options = reader.getCommandLineOptions(Mock(TestTask3))
        then:
        options.isEmpty()
    }


    def "fail when parameter cannot be converted from the command-line"() {
        when:
        reader.getCommandLineOptions(project.tasks.create("aTask", TestTask5))
        then:
        def e = thrown(CommandLineArgumentException)
        e.message == "Option 'fileValue' cannot be casted to parameter type 'java.io.File' in class 'org.gradle.api.internal.tasks.CommandLineOptionReaderTest\$TestTask5_Decorated'."
    }


    def "fails when method has > 1 parameter"() {
        when:
        reader.getCommandLineOptions(project.tasks.create("aTask", TestTask4));
        then:
        def e = thrown(CommandLineArgumentException)
        e.message == "Option 'stringValue' cannot be linked to methods with multiple parameters in class 'org.gradle.api.internal.tasks.CommandLineOptionReaderTest\$TestTask4_Decorated#setStrings'."
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

        @CommandLineOption(options = "aFlag", description = "simple flag")
        public void setActive() {
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


