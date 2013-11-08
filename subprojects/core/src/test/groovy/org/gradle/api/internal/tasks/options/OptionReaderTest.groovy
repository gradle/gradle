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

package org.gradle.api.internal.tasks.options

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class OptionReaderTest extends Specification {

    OptionReader reader
    Project project

    def setup() {
        reader = new OptionReader()
        project = ProjectBuilder.builder().build();
    }

    def "can read options linked to setter methods of a task"() {
        when:
        List<InstanceOptionDescriptor> options = reader.getOptions(project.tasks.create("aTask", TestTask1))
        then:
        options[0].name == "aFlag"
        options[0].description == "simple flag"
        options[0].argumentType == Void.TYPE
        options[0].optionElement.name == "setActive"
        options[0].availableValues == []

        options[1].name == "booleanValue"
        options[1].description == "boolean value"
        options[1].argumentType == Void.TYPE
        options[1].optionElement.name == "setBooleanValue"
        options[1].availableValues == []

        options[2].name == "enumValue"
        options[2].description == "enum value"
        options[2].argumentType == TestEnum
        options[2].optionElement.name == "setEnumValue"
        options[2].availableValues == ["ABC", "DEF"]

        options[3].name == "objectValue"
        options[3].description == "object value"
        options[3].argumentType == Object
        options[3].optionElement.name == "setObjectValue"
        options[3].availableValues == []

        options[4].name == "stringValue"
        options[4].description == "string value"
        options[4].argumentType == String
        options[4].optionElement.name == "setStringValue"
        options[4].availableValues == ["dynValue1", "dynValue2"]

    }

    def "fail when multiple methods define same option"() {
        when:
        reader.getOptions(project.tasks.create("aTask", TestTask2))
        then:
        def e = thrown(OptionValidationException)
        e.message == "Option 'stringValue' linked to multiple elements in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestTask2_Decorated'."
    }

    def "ignores static methods and fields"() {
        when:
        List<InstanceOptionDescriptor> options = reader.getOptions(Mock(TestTask3))
        then:
        options.isEmpty()
    }


    def "fail when parameter cannot be converted from the command-line"() {
        when:
        reader.getOptions(project.tasks.create("aTask", TestTask5))
        then:
        def e = thrown(OptionValidationException)
        e.message == "Option 'fileValue' cannot be casted to parameter type 'java.io.File' in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestTask5'."
    }

    def "fails when method has > 1 parameter"() {
        when:
        reader.getOptions(project.tasks.create("aTask", TestTask4));
        then:
        def e = thrown(OptionValidationException)
        e.message == "Option 'stringValue' cannot be linked to methods with multiple parameters in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestTask4#setStrings'."
    }

    def "handles field options"() {
        when:
        List<InstanceOptionDescriptor> options = reader.getOptions(project.tasks.create("aTask", TestTask6))
        then:
        options[0].name == "customOptionName"
        options[0].description == "custom description"
        options[0].argumentType == String

        options[1].name == "field2"
        options[1].description == "Descr Field2"
        options[1].argumentType == String
        options[1].availableValues == ["dynValue1", "dynValue2"]

        options[2].name == "field3"
        options[2].description == "Descr Field3"
        options[2].argumentType == TestEnum
        options[2].availableValues as Set == ["ABC", "DEF"] as Set

        options[3].name == "field4"
        options[3].description == "Descr Field4"
        options[3].argumentType == Void.TYPE
        options[3].availableValues.isEmpty()
    }


    def "throws decent error when description not set"() {
        when:
        reader.getOptions(project.tasks.create("aTask", TestTask7));
        then:
        def e = thrown(OptionValidationException)
        e.message == "No description set on option 'aValue' at for class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestTask7'."

        when:
        reader.getOptions(project.tasks.create("bTask", TestTask8));
        then:
        e = thrown(OptionValidationException)
        e.message == "No description set on option 'field' at for class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestTask8'."
    }

    def "throws decent error when method annotated without option set"() {
        when:
        reader.getOptions(project.tasks.create("aTask", TestTask9));
        then:
        def e = thrown(OptionValidationException)
        e.message == "No option name set on 'setStrings' in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestTask9'."
    }


    public static class TestTask1 extends DefaultTask {
        @Option(options = "stringValue", description = "string value")
        public void setStringValue(String value) {
        }

        @Option(options = "objectValue", description = "object value")
        public void setObjectValue(Object value) {
        }

        @Option(options = "booleanValue", description = "boolean value")
        public void setBooleanValue(boolean value) {
        }

        @Option(options = "enumValue", description = "enum value")
        public void setEnumValue(TestEnum value) {
        }

        @Option(options = "aFlag", description = "simple flag")
        public void setActive() {
        }

        @OptionValues("stringValue")
        public Collection<CustomClass> getAvailableValues() {
            return Arrays.asList(new CustomClass(value: "dynValue1"), new CustomClass(value: "dynValue2"))
        }
    }

    public static class CustomClass {
        String value

        public String toString() {
            value
        }
    }

    public static class TestTask2 extends DefaultTask {
        @Option(options = "stringValue", description = "string value")
        public void setStringValue(String value) {
        }

        @Option(options = "stringValue", description = "string value")
        public void setStringValue2(String value) {
        }
    }

    public static class TestTask3 extends DefaultTask {
        @Option(options = "staticString", description = "string value")
        public static void setStaticString(String value) {
        }
        @Option(description = "staticOption")
        static String staticField
    }

    public static class TestTask4 extends DefaultTask {
        @Option(options = 'stringValue', description = "string value")
        public void setStrings(String value1, String value2) {
        }
    }

    public static class TestTask5 extends DefaultTask {
        @Option(options = 'fileValue', description = "file value")
        public void setStrings(File file) {
        }
    }

    public static class TestTask6 extends DefaultTask {
        @Option(options = 'customOptionName', description = "custom description")
        String field1

        @Option(description = "Descr Field2")
        String field2

        @Option(description = "Descr Field3")
        TestEnum field3

        @Option(description = "Descr Field4")
        boolean field4

        @OptionValues("field2")
        List<String> getField2Options() {
            return Arrays.asList("dynValue1", "dynValue2")
        }
    }

    public static class TestTask7 extends DefaultTask {
        @Option(options = 'aValue')
        public void setStrings(String value) {
        }
    }

    public static class TestTask8 extends DefaultTask {
        @Option
        String field
    }

    public static class TestTask9 extends DefaultTask {
        @Option
        public void setStrings(String value) {
        }
    }


    enum TestEnum {
        ABC, DEF
    }
}


