/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.options.OptionValues
import spock.lang.Issue
import spock.lang.Specification

class OptionReaderTest extends Specification {

    OptionReader reader
    Project project
    int builtInOptionCount

    def setup() {
        reader = new OptionReader()
        builtInOptionCount = OptionReader.BUILT_IN_OPTIONS.size();
    }

    def "can read options linked to setter methods of a task"() {
        when:
        List<InstanceOptionDescriptor> options = reader.getOptions(new TestClassWithSetters())
        then:
        options[0].name == "aFlag"
        options[0].description == "simple flag"
        options[0].argumentType == Void.TYPE
        options[0].availableValues == [] as Set

        options[1].name == "booleanValue"
        options[1].description == "boolean value"
        options[1].argumentType == Void.TYPE
        options[1].availableValues == [] as Set

        options[2].name == "enumValue"
        options[2].description == "enum value"
        options[2].argumentType == String
        options[2].availableValues == ["ABC", "DEF"] as Set

        options[3].name == "multiString"
        options[3].description == "a list of strings"
        options[3].argumentType == List
        options[3].availableValues == [] as Set

        options[4].name == "objectValue"
        options[4].description == "object value"
        options[4].argumentType == String
        options[4].availableValues == [] as Set

        options[5].name == "stringValue"
        options[5].description == "string value"
        options[5].argumentType == String
        options[5].availableValues == ["dynValue1", "dynValue2"] as Set
    }

    def "can read options linked to property getter methods of a task"() {
        when:
        List<InstanceOptionDescriptor> options = reader.getOptions(new TestClassWithProperties())
        then:
        options[0].name == "booleanValue"
        options[0].description == "boolean value"
        options[0].argumentType == Void.TYPE
        options[0].availableValues == [] as Set

        options[1].name == "enumValue"
        options[1].description == "enum value"
        options[1].argumentType == String
        options[1].availableValues == ["ABC", "DEF"] as Set

        options[2].name == "objectValue"
        options[2].description == "object value"
        options[2].argumentType == String
        options[2].availableValues == [] as Set

        options[3].name == "stringValue"
        options[3].description == "string value"
        options[3].argumentType == String
        options[3].availableValues == ["dynValue1", "dynValue2"] as Set
    }

    def "built-in options appear last"() {
        when:
        List<InstanceOptionDescriptor> options = reader.getOptions(new TestClassWithProperties())
        int ownOptions = 4
        then:
        options.size() == ownOptions + OptionReader.BUILT_IN_OPTIONS.size()
        OptionReader.BUILT_IN_OPTIONS.eachWithIndex{ BuiltInOptionElement entry, int i ->
            assert options[ownOptions + i].name == entry.optionName
            assert options[ownOptions + i].description == entry.description
            assert options[ownOptions + i].argumentType == Void.TYPE
            assert options[ownOptions + i].availableValues == [] as Set
        }
    }

    def "task own options shadow built-in options"() {
        when:
        List<InstanceOptionDescriptor> options = reader.getOptions(new TestClassWithOptionNameClashing())
        int ownOptions = 2
        List<String> clashingOptions = ["rerun"]
        then:
        options.size() == ownOptions + OptionReader.BUILT_IN_OPTIONS.size() - clashingOptions.size()
        options[0].name == "rerun"
        options[0].description == "custom clashing option"
        options[1].name == "unique"
        options[1].description == "custom unique option"
    }

    def "fail when multiple methods define same option"() {
        when:
        reader.getOptions(new TestClass2())
        then:
        def e = thrown(OptionValidationException)
        e.message == "@Option 'stringValue' linked to multiple elements in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestClass2'."
    }

    def "fail when multiple methods from different types define same option"() {
        when:
        reader.getOptions(new WithDuplicateOptionInAnotherInterface())
        then:
        def e = thrown(OptionValidationException)
        e.message == "@Option 'stringValue' linked to multiple elements in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$WithDuplicateOptionInAnotherInterface'."
    }

    def "fails on static methods"() {
        when:
        reader.getOptions(new TestClass31())
        then:
        def e = thrown(OptionValidationException)
        e.message == "@Option on static method 'setStaticString' not supported in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestClass31'."
    }

    def "fails on static fields"() {
        when:
        reader.getOptions(new TestClass32())
        then:
        def e = thrown(OptionValidationException)
        e.message == "@Option on static field 'staticField' not supported in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestClass32'."
    }

    def "fail when parameter cannot be converted from the command-line"() {
        when:
        reader.getOptions(new TestClass5())
        then:
        def e = thrown(OptionValidationException)
        e.message == "Option 'fileValue' cannot be cast to type 'java.io.File' in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestClass5'."
    }

    def "fails when method has > 1 parameter"() {
        when:
        reader.getOptions(new TestClass4());
        then:
        def e = thrown(OptionValidationException)
        e.message == "Option 'stringValue' on method cannot take multiple parameters in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestClass4#setStrings'."
    }

    def "handles field options"() {
        when:
        List<InstanceOptionDescriptor> options = reader.getOptions(new TestClassWithFields())
        then:
        options[0].name == "customOptionName"
        options[0].description == "custom description"
        options[0].argumentType == String

        options[1].name == "field2"
        options[1].description == "Descr Field2"
        options[1].argumentType == String
        options[1].availableValues == ["dynValue1", "dynValue2"] as Set

        options[2].name == "field3"
        options[2].description == "Descr Field3"
        options[2].argumentType == String
        options[2].availableValues as Set == ["ABC", "DEF"] as Set

        options[3].name == "field4"
        options[3].description == "Descr Field4"
        options[3].argumentType == Void.TYPE
        options[3].availableValues.isEmpty()

        options[4].name == "field5"
        options[4].description == "Descr Field5"
        options[4].argumentType == List
        options[4].availableValues.isEmpty()
    }

    def "handles property field options"() {
        when:
        List<InstanceOptionDescriptor> options = reader.getOptions(new TestClassWithPropertyField())
        then:
        options[0].name == "customOptionName"
        options[0].description == "custom description"
        options[0].argumentType == String

        options[1].name == "field2"
        options[1].description == "Descr Field2"
        options[1].argumentType == String
        options[1].availableValues == ["dynValue1", "dynValue2"] as Set

        options[2].name == "field3"
        options[2].description == "Descr Field3"
        options[2].argumentType == String
        options[2].availableValues as Set == ["ABC", "DEF"] as Set

        options[3].name == "field4"
        options[3].description == "Descr Field4"
        options[3].argumentType == Void.TYPE
        options[3].availableValues.isEmpty()
    }


    def "throws decent error when description not set"() {
        when:
        reader.getOptions(new TestClass7());
        then:
        def e = thrown(OptionValidationException)
        e.message == "No description set on option 'aValue' at for class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestClass7'."

        when:
        reader.getOptions(new TestClass8());
        then:
        e = thrown(OptionValidationException)
        e.message == "No description set on option 'field' at for class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestClass8'."
    }

    def "throws decent error when method annotated without option name set"() {
        when:
        reader.getOptions(new TestClass9());
        then:
        def e = thrown(OptionValidationException)
        e.message == "No option name set on 'setStrings' in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestClass9'."
    }

    def "throws decent error when private field is annotated as option and no setter declared"() {
        when:
        reader.getOptions(new TestClass10())
        then:
        def e = thrown(OptionValidationException)
        e.message == "No setter for Option annotated field 'field' in class 'class org.gradle.api.internal.tasks.options.OptionReaderTest\$TestClass10'."
    }

    def "throws decent error for invalid OptionValues annotated methods"() {
        when:
        reader.getOptions(new WithInvalidSomeOptionMethod());
        then:
        def e = thrown(OptionValidationException)
        e.message == "@OptionValues annotation not supported on method 'getValues' in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$WithInvalidSomeOptionMethod'. Supported method must be non-static, return a Collection<String> and take no parameters.";

        when:
        reader.getOptions(new TestClass8());
        then:
        e = thrown(OptionValidationException)
        e.message == "No description set on option 'field' at for class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestClass8'."
    }

    @Issue("https://github.com/gradle/gradle/issues/18496")
    def "handles abstract classes with interfaces"() {
        when:
        List<OptionDescriptor> options = reader.getOptions(new AbstractTestClassWithInterface() {
            @Override
            Property<String> getStringValue() {
                throw new UnsupportedOperationException()
            }
        })
        then:
        options.size() == 2 + builtInOptionCount

        options[0].name == "objectValue"
        options[0].description == "object value"
        options[0].argumentType == String
        options[0].availableValues == [] as Set<String>

        options[1].name == "stringValue"
        options[1].description == "string value"
        options[1].argumentType == String
        options[1].availableValues == [] as Set<String>
    }

    @Issue("https://github.com/gradle/gradle/issues/18496")
    def "handles abstract classes with interfaces with same method but different option names"() {
        when:
        List<OptionDescriptor> options = reader.getOptions(new AbstractTestClassWithTwoInterfacesWithSameMethod() {
            @Override
            Property<String> getStringValue() {
                throw new UnsupportedOperationException()
            }
        })
        then:
        options.size() == 3 + builtInOptionCount

        options[0].name == "objectValue"
        options[0].description == "object value"
        options[0].argumentType == String
        options[0].availableValues == [] as Set<String>

        options[1].name == "stringValue"
        options[1].description == "string value"
        options[1].argumentType == String
        options[1].availableValues == [] as Set<String>

        options[2].name == "stringValue1"
        options[2].description == "string value 1"
        options[2].argumentType == String
        options[2].availableValues == [] as Set<String>
    }

    def "class that defines option when parent class and interface do has uses the sub-class option"() {
        when:
        List<OptionDescriptor> options = reader.getOptions(new OverrideCheckSubClassDefinesOption())
        then:
        options.size() == 1 + builtInOptionCount

        options[0].name == "base"
        options[0].description == "from sub class"
    }

    def "class that has an option defined in parent class and interface uses the parent class option"() {
        when:
        List<OptionDescriptor> options = reader.getOptions(new OverrideCheckSubClassSaysNothing())
        then:
        options.size() == 1 + builtInOptionCount

        options[0].name == "base"
        options[0].description == "from base class"
    }

    def "class that defines option when parent class which impls interface do has uses the sub-class option"() {
        when:
        List<OptionDescriptor> options = reader.getOptions(new OverrideCheckSubClassImplInterfaceDefinesOption())
        then:
        options.size() == 1 + builtInOptionCount

        options[0].name == "base"
        options[0].description == "from sub class"
    }

    def "class that has an option defined in parent class which impls interface uses the parent class option"() {
        when:
        List<OptionDescriptor> options = reader.getOptions(new OverrideCheckSubClassImplInterfaceSaysNothing())
        then:
        options.size() == 1 + builtInOptionCount

        options[0].name == "base"
        options[0].description == "from base class"
    }

    public interface TestInterface {
        @Option(option = "stringValue", description = "string value")
        public Property<String> getStringValue();
    }

    public interface TestInterfaceWithSameFunctionButDifferentOptionName {
        @Option(option = "stringValue1", description = "string value 1")
        public Property<String> getStringValue();
    }

    public static abstract class AbstractTestClassWithInterface implements TestInterface {
        @Option(option = "objectValue", description = "object value")
        public void setObjectValue(Object value) {
        }
    }

    public static abstract class AbstractTestClassWithTwoInterfacesWithSameMethod implements TestInterface, TestInterfaceWithSameFunctionButDifferentOptionName {
        @Option(option = "objectValue", description = "object value")
        public void setObjectValue(Object value) {
        }
    }

    // These demonstrate override behavior

    static class OverrideCheckBaseClass {
        @Option(option = "base", description = "from base class")
        public void setBase(String value) {
        }
    }

    interface OverrideCheckBaseInterface {
        @Option(option = "base", description = "from base interface")
        public void setBase(String value);
    }

    static class OverrideCheckSubClassDefinesOption extends OverrideCheckBaseClass implements OverrideCheckBaseInterface {
        @Option(option = "base", description = "from sub class")
        public void setBase(String value) {
        }
    }

    static class OverrideCheckSubClassSaysNothing extends OverrideCheckBaseClass implements OverrideCheckBaseInterface {
    }

    static class OverrideCheckBaseClassImplInterface implements OverrideCheckBaseInterface {
        @Option(option = "base", description = "from base class")
        public void setBase(String value) {
        }
    }

    static class OverrideCheckSubClassImplInterfaceDefinesOption extends OverrideCheckBaseClassImplInterface {
        @Option(option = "base", description = "from sub class")
        public void setBase(String value) {
        }
    }

    static class OverrideCheckSubClassImplInterfaceSaysNothing extends OverrideCheckBaseClassImplInterface {
    }

    public static class TestClassWithSetters {
        @Option(option = "stringValue", description = "string value")
        public void setStringValue(String value) {
        }

        @Option(option = "objectValue", description = "object value")
        public void setObjectValue(Object value) {
        }

        @Option(option = "booleanValue", description = "boolean value")
        public void setBooleanValue(boolean value) {
        }

        @Option(option = "enumValue", description = "enum value")
        public void setEnumValue(TestEnum value) {
        }

        @Option(option = "aFlag", description = "simple flag")
        public void setActive() {
        }

        @Option(option = "multiString", description = "a list of strings")
        public void setStringListValue(List<String> values) {
        }

        @OptionValues("stringValue")
        public Collection<CustomClass> getAvailableValues() {
            return Arrays.asList(new CustomClass(value: "dynValue1"), new CustomClass(value: "dynValue2"))
        }
    }

    public static class TestClassWithProperties {
        @Option(option = "stringValue", description = "string value")
        public Property<String> getStringValue() {
            throw new UnsupportedOperationException()
        }

        @Option(option = "objectValue", description = "object value")
        public Property<Object> getObjectValue() {
            throw new UnsupportedOperationException()
        }

        @Option(option = "booleanValue", description = "boolean value")
        public Property<Boolean> getBooleanValue() {
            throw new UnsupportedOperationException()
        }

        @Option(option = "enumValue", description = "enum value")
        public Property<TestEnum> getEnumValue() {
            throw new UnsupportedOperationException()
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

    public static class TestClass2 {
        @Option(option = "stringValue", description = "string value")
        public void setStringValue(String value) {
        }

        @Option(option = "stringValue", description = "string value")
        public void setStringValue2(String value) {
        }
    }

    public static class TestClass31 {
        @Option(option = "staticString", description = "string value")
        public static void setStaticString(String value) {
        }
    }

    public static class TestClass32 {
        @Option(description = "staticOption")
        static String staticField
    }

    public static class TestClass4 {
        @Option(option = 'stringValue', description = "string value")
        public void setStrings(String value1, String value2) {
        }
    }

    public static class TestClass5 {
        @Option(option = 'fileValue', description = "file value")
        public void setStrings(File file) {
        }
    }

    public static class TestClassWithOptionNameClashing {
        @Option(option = 'unique', description = "custom unique option")
        String field1
        @Option(option = 'rerun', description = "custom clashing option")
        String field2
    }

    public static class TestClassWithFields {
        @Option(option = 'customOptionName', description = "custom description")
        String field1

        @Option(description = "Descr Field2")
        String field2

        @Option(description = "Descr Field3")
        TestEnum field3

        @Option(description = "Descr Field4")
        boolean field4

        @Option(description = "Descr Field5")
        List<String> field5

        @OptionValues("field2")
        List<String> getField2Options() {
            return Arrays.asList("dynValue1", "dynValue2")
        }
    }

    public static class TestClassWithPropertyField {
        @Option(option = 'customOptionName', description = "custom description")
        final Property<String> field1

        @Option(description = "Descr Field2")
        final Property<String> field2

        @Option(description = "Descr Field3")
        final Property<TestEnum> field3

        @Option(description = "Descr Field4")
        final Property<Boolean> field4

        @OptionValues("field2")
        List<String> getField2Options() {
            return Arrays.asList("dynValue1", "dynValue2")
        }
    }

    public static class TestClass7 {
        @Option(option = 'aValue')
        public void setStrings(String value) {
        }
    }

    public static class TestClass8 {
        @Option
        String field
    }

    public static class TestClass9 {
        @Option(description = "some description")
        public void setStrings(String value) {
        }
    }

    public static class TestClass10 {
        @Option(description = "some description")
        private String field
    }

    public static class WithInvalidSomeOptionMethod {
        @OptionValues("someOption")
        List<String> getValues(String someParam) { return Arrays.asList("something") }
    }

    public static class WithDuplicateSomeOptions {
        @OptionValues("someOption")
        List<String> getValues() { return Arrays.asList("something") }

        @OptionValues("someOption")
        List<String> getValues2() { return Arrays.asList("somethingElse") }
    }

    static class WithDuplicateOptionInAnotherInterface implements TestInterface {
        @Option(option = "stringValue", description = "string value")
        List<String> getValues() { return Arrays.asList("something") }

        @Override
        Property<String> getStringValue() {
            return null
        }
    }

    public static class WithAnnotatedStaticMethod {
        @OptionValues("someOption")
        static List<String> getValues(String someParam) { return Arrays.asList("something") }
    }

    public class SomeOptionValues {
        @OptionValues("someOption")
        List<String> getValues() { return Arrays.asList("something") }
    }

    public static class WithCustomOrder {

        @Option(option = "option0", description = "desc")
        public void setOption0(String value) {
        }

        @Option(option = "option1", description = "desc")
        public void setOption1(String value) {
        }
    }

    enum TestEnum {
        ABC, DEF
    }
}


