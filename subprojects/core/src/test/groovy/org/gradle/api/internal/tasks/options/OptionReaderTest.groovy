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

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.tasks.TaskOptionsGenerator
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.options.OptionValues
import org.gradle.util.TestUtil
import spock.lang.Issue
import spock.lang.Specification

class OptionReaderTest extends Specification {

    OptionReader reader
    int builtInOptionCount

    def setup() {
        reader = new OptionReader()
        builtInOptionCount = TaskOptionsGenerator.BUILT_IN_OPTIONS.size();
    }

    def "can read options linked to setter methods of a task"() {
        when:
        List<InstanceOptionDescriptor> options = TaskOptionsGenerator.generate(new TestClassWithSetters(), reader).getAll()
        then:
        options[0].name == "aFlag"
        options[0].description == "simple flag"
        options[0].argumentType == Void.TYPE
        options[0].availableValues == [] as Set

        options[1].name == "no-aFlag"
        options[1].description == "Disables option --aFlag."
        options[1].argumentType == Void.TYPE
        options[1].availableValues == [] as Set

        options[2].name == "booleanValue"
        options[2].description == "boolean value"
        options[2].argumentType == Void.TYPE
        options[2].availableValues == [] as Set

        options[3].name == "no-booleanValue"
        options[3].description == "Disables option --booleanValue."
        options[3].argumentType == Void.TYPE
        options[3].availableValues == [] as Set

        options[4].name == "enumValue"
        options[4].description == "enum value"
        options[4].argumentType == String
        options[4].availableValues == ["ABC", "DEF"] as Set

        options[5].name == "integerValue"
        options[5].description == "integer value"
        options[5].argumentType == String
        options[5].availableValues == [] as Set

        options[6].name == "multiEnum"
        options[6].description == "a list of enums"
        options[6].argumentType == List
        options[6].availableValues == [] as Set

        options[7].name == "multiInteger"
        options[7].description == "a list of integers"
        options[7].argumentType == List
        options[7].availableValues == [] as Set

        options[8].name == "multiString"
        options[8].description == "a list of strings"
        options[8].argumentType == List
        options[8].availableValues == [] as Set

        options[9].name == "objectValue"
        options[9].description == "object value"
        options[9].argumentType == String
        options[9].availableValues == [] as Set

        options[10].name == "stringValue"
        options[10].description == "string value"
        options[10].argumentType == String
        options[10].availableValues == ["dynValue1", "dynValue2"] as Set
    }

    def "can read options linked to property getter methods of a task"() {
        when:
        List<InstanceOptionDescriptor> options = TaskOptionsGenerator.generate(new TestClassWithProperties(), reader).getAll()
        then:
        options[0].name == "booleanValue"
        options[0].description == "boolean value"
        options[0].argumentType == Void.TYPE
        options[0].availableValues == [] as Set

        options[1].name == "no-booleanValue"
        options[1].description == "Disables option --booleanValue."
        options[1].argumentType == Void.TYPE
        options[1].availableValues == [] as Set

        options[2].name == "directoryValue"
        options[2].description == "Directory value"
        options[2].argumentType == String

        options[3].name == "enumValue"
        options[3].description == "enum value"
        options[3].argumentType == String
        options[3].availableValues == ["ABC", "DEF"] as Set

        options[4].name == "integerValue"
        options[4].description == "integer value"
        options[4].argumentType == String
        options[4].availableValues == [] as Set

        options[5].name == "listEnumValue"
        options[5].description == "list enum value"
        options[5].argumentType == List

        options[6].name == "listIntegerValue"
        options[6].description == "list integer value"
        options[6].argumentType == List

        options[7].name == "listStringValue"
        options[7].description == "list string value"
        options[7].argumentType == List

        options[8].name == "objectValue"
        options[8].description == "object value"
        options[8].argumentType == String
        options[8].availableValues == [] as Set

        options[9].name == "regularFileValue"
        options[9].description == "RegularFile value"
        options[9].argumentType == String

        options[10].name == "setEnumValue"
        options[10].description == "set enum value"
        options[10].argumentType == List

        options[11].name == "setIntegerValue"
        options[11].description == "set integer value"
        options[11].argumentType == List

        options[12].name == "setStringValue"
        options[12].description == "set string value"
        options[12].argumentType == List

        options[13].name == "stringValue"
        options[13].description == "string value"
        options[13].argumentType == String
        options[13].availableValues == ["dynValue1", "dynValue2"] as Set
    }

    def "built-in options appear last"() {
        when:
        List<OptionDescriptor> options = TaskOptionsGenerator.generate(new TestClassWithProperties(), reader).getAll()
        int ownOptions = 14
        then:
        options.forEach {it -> System.out.println(it.name + " " + it.description)}

        TaskOptionsGenerator.BUILT_IN_OPTIONS.eachWithIndex { BuiltInOptionElement optionElement, int i ->
            assert options[ownOptions + i].name == optionElement.optionName
            assert options[ownOptions + i].description == optionElement.description
            assert options[ownOptions + i].argumentType == Void.TYPE
            assert options[ownOptions + i].availableValues == [] as Set
        }
    }

    def "task own options shadow built-in options"() {
        when:
        List<InstanceOptionDescriptor> options = TaskOptionsGenerator.generate(new TestClassWithOptionNameClashing(), reader).getAll()
        int ownOptions = 2
        List<String> clashingOptions = ["rerun"]
        then:
        options.size() == ownOptions + builtInOptionCount - clashingOptions.size()
        options[0].name == "rerun"
        options[0].description == "custom clashing option"
        options[1].name == "unique"
        options[1].description == "custom unique option"
    }

    def "task own options shadow generated opposite options"() {
        when:
        List<InstanceOptionDescriptor> options = TaskOptionsGenerator.generate(new TestClassWithOppositeOptionNameClashing(), reader).getAll()
        int ownOptions = 2
        then:
        options.size() == ownOptions + builtInOptionCount
        options[0].name == "my-option"
        options[0].description == "Option to trigger creation of opposite option"
        options[1].name == "no-my-option"
        options[1].description == "Option clashing with opposite option"
        options[2].name == "rerun"
        options[2].description == "Causes the task to be re-run even if up-to-date."
    }

    def "task only opposite option"() {
        when:
        List<InstanceOptionDescriptor> options = TaskOptionsGenerator.generate(new TestClassWithOnlyOppositeOption(), reader).getAll()
        int ownOptions = 4
        then:
        options.size() == ownOptions + builtInOptionCount
        options[0].name == "my-option1"
        options[0].description == "Opposite option of --no-my-option1."
        options[1].name == "no-my-option1"
        options[1].description == "Opposite option Boolean"
        options[2].name == "my-option2"
        options[2].description == "Opposite option of --no-my-option2."
        options[3].name == "no-my-option2"
        options[3].description == "Opposite option Property<Boolean>"
        options[4].name == "rerun"
        options[4].description == "Causes the task to be re-run even if up-to-date."
    }

    def "fail when multiple methods define same option"() {
        when:
        TaskOptionsGenerator.generate(new TestClass2(), reader).getAll()
        then:
        def e = thrown(OptionValidationException)
        e.message == "@Option 'stringValue' linked to multiple elements in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestClass2'."
    }

    def "fail when multiple methods from different types define same option"() {
        when:
        TaskOptionsGenerator.generate(new WithDuplicateOptionInAnotherInterface(), reader).getAll()
        then:
        def e = thrown(OptionValidationException)
        e.message == "@Option 'stringValue' linked to multiple elements in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$WithDuplicateOptionInAnotherInterface'."
    }

    def "fails on static methods"() {
        when:
        TaskOptionsGenerator.generate(new TestClass31(), reader).getAll()
        then:
        def e = thrown(OptionValidationException)
        e.message == "@Option on static method 'setStaticString' not supported in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestClass31'."
    }

    def "fails on static fields"() {
        when:
        TaskOptionsGenerator.generate(new TestClass32(), reader).getAll()
        then:
        def e = thrown(OptionValidationException)
        e.message == "@Option on static field 'staticField' not supported in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestClass32'."
    }

    def "fail when parameter cannot be converted from the command-line"() {
        when:
        TaskOptionsGenerator.generate(new TestClass5(), reader).getAll()
        then:
        def e = thrown(OptionValidationException)
        e.message == "Option 'fileValue' cannot be cast to type 'java.io.File' in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestClass5'."
    }

    def "fails when method has > 1 parameter"() {
        when:
        TaskOptionsGenerator.generate(new TestClass4(), reader).getAll()
        then:
        def e = thrown(OptionValidationException)
        e.message == "Option 'stringValue' on method cannot take multiple parameters in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestClass4#setStrings'."
    }

    def "handles field options"() {
        when:
        List<InstanceOptionDescriptor> options = TaskOptionsGenerator.generate(new TestClassWithFields(), reader).getAll()
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

        options[4].name == "no-field4"
        options[4].description == "Disables option --field4."
        options[4].argumentType == Void.TYPE
        options[4].availableValues.isEmpty()

        options[5].name == "field5"
        options[5].description == "Descr Field5"
        options[5].argumentType == String
        options[5].availableValues.isEmpty()

        options[6].name == "field6"
        options[6].description == "Descr Field6"
        options[6].argumentType == List
        options[6].availableValues.isEmpty()

        options[7].name == "field7"
        options[7].description == "Descr Field7"
        options[7].argumentType == List
        options[7].availableValues.isEmpty()

        options[8].name == "field8"
        options[8].description == "Descr Field8"
        options[8].argumentType == List
        options[8].availableValues.isEmpty()

        options[9].name == "field9"
        options[9].description == "Descr Field9"
        options[9].argumentType == String
        options[9].availableValues == ["dynValue3", "dynValue4"] as Set
    }

    def "handles property field options"() {
        when:
        List<InstanceOptionDescriptor> options = TaskOptionsGenerator.generate(new TestClassWithPropertyField(), reader).getAll()

        then:
        options[0].name == "booleanValue"
        options[0].description == "Descr booleanValue"
        options[0].argumentType == Void.TYPE
        options[0].availableValues.isEmpty()

        options[1].name == "no-booleanValue"
        options[1].description == "Disables option --booleanValue."
        options[1].argumentType == Void.TYPE
        options[1].availableValues.isEmpty()

        options[2].name == "customOptionName"
        options[2].description == "custom description"
        options[2].argumentType == String

        options[3].name == "directoryValue"
        options[3].description == "Descr directoryValue"
        options[3].argumentType == String

        options[4].name == "enumListValue"
        options[4].description == "Descr enumListValue"
        options[4].argumentType == List

        options[5].name == "enumSetValue"
        options[5].description == "Descr enumSetValue"
        options[5].argumentType == List

        options[6].name == "enumValue"
        options[6].description == "Descr enumValue"
        options[6].argumentType == String
        options[6].availableValues as Set == ["ABC", "DEF"] as Set

        options[7].name == "integerListValue"
        options[7].description == "Descr integerListValue"
        options[7].argumentType == List

        options[8].name == "integerSetValue"
        options[8].description == "Descr integerSetValue"
        options[8].argumentType == List

        options[9].name == "integerValue"
        options[9].description == "Descr integerValue"
        options[9].argumentType == String

        options[10].name == "regularFileValue"
        options[10].description == "Descr regularFileValue"
        options[10].argumentType == String

        options[11].name == "stringListValue"
        options[11].description == "Descr stringListValue"
        options[11].argumentType == List

        options[12].name == "stringSetValue"
        options[12].description == "Descr stringSetValue"
        options[12].argumentType == List

        options[13].name == "stringValue"
        options[13].description == "Descr stringValue"
        options[13].argumentType == String
        options[13].availableValues == ["dynValue1", "dynValue2"] as Set
    }

    def "throws decent error when description not set"() {
        when:
        TaskOptionsGenerator.generate(new TestClass7(), reader).getAll()
        then:
        def e = thrown(OptionValidationException)
        e.message == "No description set on option 'aValue' at for class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestClass7'."

        when:
        TaskOptionsGenerator.generate(new TestClass8(), reader).getAll()
        then:
        e = thrown(OptionValidationException)
        e.message == "No description set on option 'field' at for class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestClass8'."
    }

    def "throws decent error when method annotated without option name set"() {
        when:
        TaskOptionsGenerator.generate(new TestClass9(), reader).getAll()
        then:
        def e = thrown(OptionValidationException)
        e.message == "No option name set on 'setStrings' in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestClass9'."
    }

    def "throws decent error when private field is annotated as option and no setter declared"() {
        when:
        TaskOptionsGenerator.generate(new TestClass10(), reader).getAll()
        then:
        def e = thrown(OptionValidationException)
        e.message == "No setter for Option annotated field 'field' in class 'class org.gradle.api.internal.tasks.options.OptionReaderTest\$TestClass10'."
    }

    def "throws decent error for invalid OptionValues annotated methods"() {
        when:
        TaskOptionsGenerator.generate(new WithInvalidSomeOptionMethod(), reader).getAll()
        then:
        def e = thrown(OptionValidationException)
        e.message == "@OptionValues annotation not supported on method 'getValues' in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$WithInvalidSomeOptionMethod'. Supported method must be non-static, return a Collection<String> or Provider<Collection<String>> and take no parameters."

        when:
        TaskOptionsGenerator.generate(new WithInvalidPropertyTypeSomeOptionMethod(), reader).getAll()
        then:
        e = thrown(OptionValidationException)
        e.message == "@OptionValues annotation not supported on method 'getValues' in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$WithInvalidPropertyTypeSomeOptionMethod'. Supported method must be non-static, return a Collection<String> or Provider<Collection<String>> and take no parameters."

        when:
        TaskOptionsGenerator.generate(new WithInvalidProviderCollectionTypeSomeOptionMethod(), reader).getAll()
        then:
        e = thrown(OptionValidationException)
        e.message == "@OptionValues annotation not supported on method 'getValues' in class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$WithInvalidProviderCollectionTypeSomeOptionMethod'. Supported method must be non-static, return a Collection<String> or Provider<Collection<String>> and take no parameters."

        when:
        TaskOptionsGenerator.generate(new TestClass8(), reader).getAll()
        then:
        e = thrown(OptionValidationException)
        e.message == "No description set on option 'field' at for class 'org.gradle.api.internal.tasks.options.OptionReaderTest\$TestClass8'."
    }

    @Issue("https://github.com/gradle/gradle/issues/18496")
    def "handles abstract classes with interfaces"() {
        when:
        List<OptionDescriptor> options = TaskOptionsGenerator.generate((new AbstractTestClassWithInterface() {
            @Override
            Property<String> getStringValue() {
                throw new UnsupportedOperationException()
            }
        }), reader).getAll()
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
        List<OptionDescriptor> options = TaskOptionsGenerator.generate((new AbstractTestClassWithTwoInterfacesWithSameMethod() {
            @Override
            Property<String> getStringValue() {
                throw new UnsupportedOperationException()
            }
        }), reader).getAll()
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
        List<OptionDescriptor> options = TaskOptionsGenerator.generate(new OverrideCheckSubClassDefinesOption(), reader).getAll()
        then:
        options.size() == 1 + builtInOptionCount

        options[0].name == "base"
        options[0].description == "from sub class"
    }

    def "class that has an option defined in parent class and interface uses the parent class option"() {
        when:
        List<OptionDescriptor> options = TaskOptionsGenerator.generate(new OverrideCheckSubClassSaysNothing(), reader).getAll()
        then:
        options.size() == 1 + builtInOptionCount

        options[0].name == "base"
        options[0].description == "from base class"
    }

    def "class that defines option when parent class which impls interface do has uses the sub-class option"() {
        when:
        List<OptionDescriptor> options = TaskOptionsGenerator.generate(new OverrideCheckSubClassImplInterfaceDefinesOption(), reader).getAll()
        then:
        options.size() == 1 + builtInOptionCount

        options[0].name == "base"
        options[0].description == "from sub class"
    }

    def "class that has an option defined in parent class which impls interface uses the parent class option"() {
        when:
        List<OptionDescriptor> options = TaskOptionsGenerator.generate(new OverrideCheckSubClassImplInterfaceSaysNothing(), reader).getAll()
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

        @Option(option = "integerValue", description = "integer value")
        public void setIntegerValue(Integer value) {
        }

        @Option(option = "aFlag", description = "simple flag")
        public void setActive() {
        }

        @Option(option = "multiString", description = "a list of strings")
        public void setStringListValue(List<String> values) {
        }

        @Option(option = "multiEnum", description = "a list of enums")
        public void setEnumListValue(List<TestEnum> values) {
        }

        @Option(option = "multiInteger", description = "a list of integers")
        public void setIntegerListValue(List<Integer> values) {
        }

        @OptionValues("stringValue")
        public Collection<CustomClass> getAvailableValues() {
            return Arrays.asList(new CustomClass(value: "dynValue1"), new CustomClass(value: "dynValue2"))
        }
    }

    public static class TestClassWithProperties {
        @Option(option = "booleanValue", description = "boolean value")
        public Property<Boolean> getBooleanValue() {
            throw new UnsupportedOperationException()
        }

        @Option(option = "directoryValue", description = "Directory value")
        public DirectoryProperty getDirectoryValue() {
            throw new UnsupportedOperationException()
        }

        @Option(option = "enumValue", description = "enum value")
        public Property<TestEnum> getEnumValue() {
            throw new UnsupportedOperationException()
        }

        @Option(option = "integerValue", description = "integer value")
        public Property<Integer> getIntegerValue() {
            throw new UnsupportedOperationException()
        }

        @Option(option = "listEnumValue", description = "list enum value")
        public ListProperty<TestEnum> getListEnumValue() {
            throw new UnsupportedOperationException()
        }

        @Option(option = "listIntegerValue", description = "list integer value")
        public ListProperty<Integer> getListIntegerValue() {
            throw new UnsupportedOperationException()
        }

        @Option(option = "listStringValue", description = "list string value")
        public ListProperty<String> getListStringValue() {
            throw new UnsupportedOperationException()
        }

        @Option(option = "regularFileValue", description = "RegularFile value")
        public RegularFileProperty getRegularFileValue() {
            throw new UnsupportedOperationException()
        }

        @Option(option = "setEnumValue", description = "set enum value")
        public SetProperty<TestEnum> getSetEnumValue() {
            throw new UnsupportedOperationException()
        }

        @Option(option = "setIntegerValue", description = "set integer value")
        public SetProperty<Integer> getSetIntegerValue() {
            throw new UnsupportedOperationException()
        }

        @Option(option = "setStringValue", description = "set string value")
        public SetProperty<String> getSetStringValue() {
            throw new UnsupportedOperationException()
        }

        @Option(option = "stringValue", description = "string value")
        public Property<String> getStringValue() {
            throw new UnsupportedOperationException()
        }

        @Option(option = "objectValue", description = "object value")
        public Property<Object> getObjectValue() {
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

    public static class TestClassWithOppositeOptionNameClashing {
        @Option(option = 'my-option', description = "Option to trigger creation of opposite option")
        Boolean field1
        @Option(option = 'no-my-option', description = "Option clashing with opposite option")
        Boolean field2
    }

    public static class TestClassWithOnlyOppositeOption {
        @Option(option = 'no-my-option1', description = "Opposite option Boolean")
        Boolean field1

        @Option(option = 'no-my-option2', description = "Opposite option Property<Boolean>")
        Property<Boolean> field2
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
        Integer field5

        @Option(description = "Descr Field6")
        List<TestEnum> field6

        @Option(description = "Descr Field7")
        List<Integer> field7

        @Option(description = "Descr Field8")
        List<String> field8

        @Option(description = "Descr Field9")
        String field9

        @OptionValues("field2")
        List<String> getField2Options() {
            return Arrays.asList("dynValue1", "dynValue2")
        }

        @OptionValues("field9")
        Provider<List<String>> getField9Options() {
            return TestUtil.providerFactory().provider { Arrays.asList("dynValue3", "dynValue4") }
        }
    }

    public static class TestClassWithPropertyField {
        @Option(description = "Descr booleanValue")
        final Property<Boolean> booleanValue

        @Option(option = 'customOptionName', description = "custom description")
        final Property<String> customStringValue

        @Option(description = "Descr directoryValue")
        final DirectoryProperty directoryValue

        @Option(description = "Descr enumListValue")
        final ListProperty<TestEnum> enumListValue

        @Option(description = "Descr enumSetValue")
        final SetProperty<TestEnum> enumSetValue

        @Option(description = "Descr enumValue")
        final Property<TestEnum> enumValue

        @Option(description = "Descr integerValue")
        final Property<Integer> integerValue

        @Option(description = "Descr integerListValue")
        final ListProperty<Integer> integerListValue

        @Option(description = "Descr integerSetValue")
        final SetProperty<Integer> integerSetValue

        @Option(description = "Descr regularFileValue")
        final RegularFileProperty regularFileValue

        @Option(description = "Descr stringListValue")
        final ListProperty<String> stringListValue

        @Option(description = "Descr stringValue")
        final Property<String> stringValue

        @Option(description = "Descr stringSetValue")
        final SetProperty<String> stringSetValue

        @OptionValues("stringValue")
        List<String> getStringValueOptions() {
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

    public static class WithInvalidPropertyTypeSomeOptionMethod {
        @OptionValues("someOption")
        Property<List<String>> getValues() { null }
    }

    public static class WithInvalidProviderCollectionTypeSomeOptionMethod {
        @OptionValues("someOption")
        Property<String> getValues() { null }
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


