/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.managed
import org.gradle.api.artifacts.Configuration
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ScalarTypesInManagedModelIntegrationTest extends AbstractIntegrationSpec {

    def "values of primitive types and boxed primitive types are widened as usual when using groovy"() {
        when:
        buildScript '''
            @Managed
            interface PrimitiveTypes {
                Long getLongPropertyFromInt()
                void setLongPropertyFromInt(Long value)

                Long getLongPropertyFromInteger()
                void setLongPropertyFromInteger(Long value)
            }

            class RulePlugin extends RuleSource {
                @Model
                void createPrimitiveTypes(PrimitiveTypes primitiveTypes) {
                    primitiveTypes.longPropertyFromInt = 123
                    primitiveTypes.longPropertyFromInteger = new Integer(321)
                }

                @Mutate
                void addEchoTask(ModelMap<Task> tasks, final PrimitiveTypes primitiveTypes) {
                    tasks.create("echo") {
                        it.doLast {
                            println "from int: $primitiveTypes.longPropertyFromInt"
                            println "from Integer: $primitiveTypes.longPropertyFromInteger"
                        }
                    }
                }
            }

            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains "from int: 123"
        output.contains "from Integer: 321"
    }

    def "mismatched types error in managed type are propagated to the user"() {
        when:
        buildScript '''
            @Managed
            interface PrimitiveTypes {
                Long getLongProperty()
                void setLongProperty(long value)
            }

            class RulePlugin extends RuleSource {
                @Model
                void createPrimitiveTypes(PrimitiveTypes primitiveTypes) {
                    primitiveTypes.longProperty = 123L
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "modelReport"

        and:
        failure.assertHasCause('Invalid managed model type PrimitiveTypes: setter method param must be of exactly the same type as the getter returns (expected: java.lang.Long, found: long) (invalid method: void PrimitiveTypes#setLongProperty(long))')
    }

    def "values of primitive types are boxed as usual when using java"() {
        when:
        file('buildSrc/src/main/java/Rules.java') << '''
            import org.gradle.api.*;
            import org.gradle.model.*;

            @Managed
            interface PrimitiveProperty {
                Long getLongProperty();
                void setLongProperty(Long value);
                Integer getIntegerProperty();
                void setIntegerProperty(Integer value);
                Boolean getBooleanProperty();
                void setBooleanProperty(Boolean value);
                Character getCharacterProperty();
                void setCharacterProperty(Character value);
            }

            class RulePlugin extends RuleSource {
                @Model
                void createPrimitiveProperty(PrimitiveProperty primitiveProperty) {
                    primitiveProperty.setLongProperty(123l);
                    primitiveProperty.setIntegerProperty(456);
                    primitiveProperty.setBooleanProperty(true);
                    primitiveProperty.setCharacterProperty('a');
                }

                @Mutate
                void addEchoTask(ModelMap<Task> tasks, final PrimitiveProperty primitiveProperty) {
                    tasks.create("echo", new Action<Task>() {
                        public void execute(Task task) {
                            task.doLast(new Action<Task>() {
                                public void execute(Task unused) {
                                    System.out.println(String.format("long: %d", primitiveProperty.getLongProperty()));
                                    System.out.println(String.format("integer: %d", primitiveProperty.getIntegerProperty()));
                                    System.out.println(String.format("boolean: %s", primitiveProperty.getBooleanProperty()));
                                    System.out.println(String.format("character: %s", primitiveProperty.getCharacterProperty()));
                                }
                            });
                        }
                    });
                }
            }
        '''

        buildScript '''
            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains "long: 123"
        output.contains "integer: 456"
        output.contains "boolean: true"
        output.contains "character: a"
    }

    def "can set/get properties of all supported unmanaged types using Groovy"() {
        when:
        buildScript '''
            @Managed
            interface AllSupportedUnmanagedTypes {
                Boolean getBooleanProperty()
                void setBooleanProperty(Boolean value)

                Integer getIntegerProperty()
                void setIntegerProperty(Integer value)

                Long getLongProperty()
                void setLongProperty(Long value)

                Double getDoubleProperty()
                void setDoubleProperty(Double value)

                BigInteger getBigIntegerProperty()
                void setBigIntegerProperty(BigInteger value)

                BigDecimal getBigDecimalProperty()
                void setBigDecimalProperty(BigDecimal value)

                String getStringProperty()
                void setStringProperty(String value)

                File getFile()
                void setFile(File file)

                boolean isFlag()
                void setFlag(boolean flag)

                boolean getOtherFlag()
                void setOtherFlag(boolean flag)

                boolean isThirdFlag()
                boolean getThirdFlag()
                void setThirdFlag(boolean flag)
            }

            class RulePlugin extends RuleSource {
                @Model
                void supportedUnmanagedTypes(AllSupportedUnmanagedTypes element) {
                    element.booleanProperty = Boolean.TRUE
                    element.integerProperty = Integer.valueOf(1)
                    element.longProperty = Long.valueOf(2L)
                    element.doubleProperty = Double.valueOf(3.3)
                    element.bigIntegerProperty = new BigInteger("4")
                    element.bigDecimalProperty = new BigDecimal("5.5")
                    element.stringProperty = "test"
                    element.file = new File('sample.txt')
                    element.flag = true
                    element.otherFlag = true
                    element.thirdFlag = true
                }

                @Mutate
                void addEchoTask(ModelMap<Task> tasks, AllSupportedUnmanagedTypes element) {
                    tasks.create("echo") {
                        it.doLast {
                            println "boolean: ${element.booleanProperty}"
                            println "integer: ${element.integerProperty}"
                            println "long: ${element.longProperty}"
                            println "double: ${element.doubleProperty}"
                            println "big integer: ${element.bigIntegerProperty}"
                            println "big decimal: ${element.bigDecimalProperty}"
                            println "string: ${element.stringProperty}"
                            println "file: ${element.file}"
                            println "flag: ${element.flag}"
                            println "otherFlag: ${element.otherFlag}"
                            println "thirdFlag: ${element.thirdFlag}"
                        }
                    }
                }
            }

            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains "boolean: true"
        output.contains "integer: 1"
        output.contains "long: 2"
        output.contains "double: 3.3"
        output.contains "big integer: 4"
        output.contains "big decimal: 5.5"
        output.contains "string: test"
        output.contains "file: sample.txt"
        output.contains "flag: true"
        output.contains "otherFlag: true"
        output.contains "thirdFlag: true"
    }

    def "can set/get properties of all supported unmanaged types using Java"() {
        given:
        file('buildSrc/src/main/java/Rules.java') << '''
            import org.gradle.api.*;
            import org.gradle.model.*;
            import java.math.BigInteger;
            import java.math.BigDecimal;
            import java.io.File;

            @Managed
            interface AllSupportedUnmanagedTypes {
                Boolean getBooleanProperty();
                void setBooleanProperty(Boolean value);

                Integer getIntegerProperty();
                void setIntegerProperty(Integer value);

                Long getLongProperty();
                void setLongProperty(Long value);

                Double getDoubleProperty();
                void setDoubleProperty(Double value);

                BigInteger getBigIntegerProperty();
                void setBigIntegerProperty(BigInteger value);

                BigDecimal getBigDecimalProperty();
                void setBigDecimalProperty(BigDecimal value);

                String getStringProperty();
                void setStringProperty(String value);

                File getFile();
                void setFile(File file);

                boolean isFlag();
                void setFlag(boolean flag);

                boolean getOtherFlag();
                void setOtherFlag(boolean flag);

                boolean isThirdFlag();
                boolean getThirdFlag();
                void setThirdFlag(boolean flag);

            };

            class RulePlugin extends RuleSource {
                @Model
                void supportedUnmanagedTypes(AllSupportedUnmanagedTypes element) {
                    element.setBooleanProperty(Boolean.TRUE);
                    element.setIntegerProperty(Integer.valueOf(1));
                    element.setLongProperty(Long.valueOf(2L));
                    element.setDoubleProperty(Double.valueOf(3.3d));
                    element.setBigIntegerProperty(new BigInteger("4"));
                    element.setBigDecimalProperty(new BigDecimal("5.5"));
                    element.setStringProperty("test");
                    element.setFile(new File("sample.txt"));
                    element.setFlag(true);
                    element.setOtherFlag(true);
                    element.setThirdFlag(true);
                }

                @Mutate
                void addEchoTask(ModelMap<Task> tasks, final AllSupportedUnmanagedTypes element) {
                    tasks.create("echo", new Action<Task>() {
                        public void execute(Task task) {
                            task.doLast(new Action<Task>() {
                                public void execute(Task unused) {
                                    System.out.println(String.format("%s: %s", "boolean", element.getBooleanProperty()));
                                    System.out.println(String.format("%s: %s", "integer", element.getIntegerProperty()));
                                    System.out.println(String.format("%s: %s", "long", element.getLongProperty()));
                                    System.out.println(String.format("%s: %s", "double", element.getDoubleProperty()));
                                    System.out.println(String.format("%s: %s", "big integer", element.getBigIntegerProperty()));
                                    System.out.println(String.format("%s: %s", "big decimal", element.getBigDecimalProperty()));
                                    System.out.println(String.format("%s: %s", "string", element.getStringProperty()));
                                    System.out.println(String.format("%s: %s", "file", element.getFile()));
                                    System.out.println(String.format("%s: %s", "flag", element.isFlag()));
                                    System.out.println(String.format("%s: %s", "otherFlag", element.getOtherFlag()));
                                    System.out.println(String.format("%s: %s %s", "thirdFlag", element.isThirdFlag(), element.getThirdFlag()));
                                }
                            });
                        }
                    });
                }
            }

        '''

        when:
        buildScript '''
            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains "boolean: true"
        output.contains "integer: 1"
        output.contains "long: 2"
        output.contains "double: 3.3"
        output.contains "big integer: 4"
        output.contains "big decimal: 5.5"
        output.contains "string: test"
        output.contains "file: sample.txt"
        output.contains "flag: true"
        output.contains "otherFlag: true"
        output.contains "thirdFlag: true true"
    }

    def "can specify managed models with file types"() {
        when:
        buildScript '''
            @Managed
            interface FileContainer {
                File getFile()
                void setFile(File file)
            }

            model {
                gradleFileContainer(FileContainer) {
                    file = file('sample.txt')
                }

                jdkFileContainer(FileContainer) {
                    file = new File('sample.txt')
                }
            }
        '''

        then:
        succeeds "model"
    }

    def "can read and write to managed property of scalar type when using Groovy"() {
        given:
        def i = 0
        def properties = [[byte, "123"],
                          [Byte, "123"],
                          [boolean, "false"],
                          [Boolean, "false"],
                          [boolean, "true"],
                          [Boolean, "true"],
                          [char, "'c'"],
                          [Character, "'c'"],
                          [float, "123.45f"],
                          [Float, "123.45f"],
                          [long, "123L"],
                          [Long, "123L"],
                          [short, "123"],
                          [Short, "123"],
                          [int, "123"],
                          [Integer, "123"],
                          [double, "123.456d"],
                          [Double, "123.456d"],
                          [String, '"Mogette"'],
                          [BigDecimal, '999G'],
                          [BigInteger, '777G'],
                          [Configuration.State, 'org.gradle.api.artifacts.Configuration.State.UNRESOLVED'],
                          [File, 'new File("foo")']].collect { propertyDef ->
            def (type, value) = propertyDef
            def propName = type.primitive ? "primitive${type.name.capitalize()}${i++}" : "boxed${type.simpleName}${i++}"
            [dsl       : """${type.canonicalName} get${propName.capitalize()}()
               void set${propName.capitalize()}(${type.canonicalName} value)
""",
             assignment: "p.$propName=$value",
             check     : "assert p.$propName == $value"]
        }

        buildScript """
            import org.gradle.api.artifacts.Configuration.State

            @Managed
            interface ManagedType {
                ${properties.dsl.join('\n')}
            }

            class PluginRules extends RuleSource {
                @Model
                void createModel(ManagedType p) {
                    ${properties.assignment.join('\n')}
                }

                @Mutate
                void addCheckTask(ModelMap<Task> tasks, ManagedType p) {
                    tasks.create("check") {
                        it.doLast {
                            ${properties.check.join('\n')}
                        }
                    }
                }
            }

            apply plugin: PluginRules

        """

        expect:
        succeeds 'check'

    }

    def "can read and write to managed property of scalar type when using Java"() {
        given:
        def i = 0
        def properties = [[byte, "(byte) 123"],
                          [Byte, "(byte) 123"],
                          [boolean, "false"],
                          [Boolean, "false"],
                          [boolean, "true"],
                          [Boolean, "true"],
                          [char, "'c'"],
                          [Character, "'c'"],
                          [float, "123.45f"],
                          [Float, "123.45f"],
                          [long, "123L"],
                          [Long, "123L"],
                          [short, "(short) 123"],
                          [Short, "(short) 123"],
                          [int, "123"],
                          [Integer, "123"],
                          [double, "123.456d"],
                          [Double, "123.456d"],
                          [String, '"Mogette"'],
                          [BigDecimal, 'new BigDecimal(999)'],
                          [BigInteger, 'new BigInteger("777")'],
                          [Configuration.State, 'org.gradle.api.artifacts.Configuration.State.UNRESOLVED'],
                          [File, 'new File("foo")']].collect { propertyDef ->
            def (type, value) = propertyDef
            def propName = type.primitive ? "primitive${type.name.capitalize()}${i++}" : "boxed${type.simpleName}${i++}"
            [dsl       : """${type.canonicalName} get${propName.capitalize()}();
               void set${propName.capitalize()}(${type.canonicalName} value);
""",
             assignment: "p.set${propName.capitalize()}($value);",
             check     : (type.primitive?"assert p.get${propName.capitalize()}() == $value":"assert p.get${propName.capitalize()}().equals($value)") + ":\"$propName validation failed\";"
            ]
        }

        file('buildSrc/src/main/java/Rules.java') << """
            import org.gradle.api.*;
            import org.gradle.model.*;
            import java.math.BigDecimal;
            import java.math.BigInteger;
            import java.io.File;
            import org.gradle.api.artifacts.Configuration.State;

            @Managed
            interface ManagedType {
                ${properties.dsl.join('\n')}
            }

            class RulePlugin extends RuleSource {
                @Model
                void createModel(ManagedType p) {
                    ${properties.assignment.join('\n')}
                }

                @Mutate
                void addCheckTask(ModelMap<Task> tasks, final ManagedType p) {
                    tasks.create("check", new Action<Task>() {
                        public void execute(Task task) {
                            task.doLast(new Action<Task>() {
                                public void execute(Task unused) {
                                    ${properties.check.join('\n')}
                                }
                            });
                        }
                    });
                }
            }
        """

        buildScript '''
            apply type: RulePlugin
        '''

        expect:
        succeeds 'check'

    }

    def "cannot mutate managed property of scalar type when view is immutable"() {
        given:
        def i = 0
        def datatable = [[byte, "123"],
                         [Byte, "123"],
                         [boolean, "false"],
                         [Boolean, "false"],
                         [boolean, "true"],
                         [Boolean, "true"],
                         [char, "'c'"],
                         [Character, "'c'"],
                         [float, "123.45f"],
                         [Float, "123.45f"],
                         [long, "123L"],
                         [Long, "123L"],
                         [short, "123"],
                         [Short, "123"],
                         [int, "123"],
                         [Integer, "123"],
                         [double, "123.456d"],
                         [Double, "123.456d"],
                         [String, '"Mogette"'],
                         [BigDecimal, '999G'],
                         [BigInteger, '777G'],
                         [Configuration.State, 'org.gradle.api.artifacts.Configuration.State.UNRESOLVED'],
                         [File, 'new File("foo")']]
        def properties = datatable.collect { propertyDef ->
            def (type, value) = propertyDef
            def propName = type.primitive ? "primitive${type.name.capitalize()}${i++}" : "boxed${type.simpleName}${i++}"
            [dsl       : """${type.canonicalName} get${propName.capitalize()}()
               void set${propName.capitalize()}(${type.canonicalName} value)
""",
             check: """
                    tasks.create("check${propName.capitalize()}") {
                        it.doLast {
                            p.$propName=$value
                        }
                    }"""]
        }

        buildScript """
            import org.gradle.api.artifacts.Configuration.State

            @Managed
            interface ManagedType {
                ${properties.dsl.join('\n')}
            }

            class PluginRules extends RuleSource {
                @Model
                void createModel(ManagedType p) {
                }

                @Mutate
                void addCheckTask(ModelMap<Task> tasks, ManagedType p) {
                    ${properties.check.join('\n')}
                }
            }

            apply plugin: PluginRules

        """
        i=0

        expect:
        datatable.each { propertyDef ->
            def (type, value) = propertyDef
            def propName = type.primitive ? "primitive${type.name.capitalize()}${i++}" : "boxed${type.simpleName}${i++}"
            fails "check${propName.capitalize()}"
            failure.assertHasCause(/Attempt to mutate closed view of model of type 'ManagedType' given to rule 'PluginRules#addCheckTask'/)
        }

    }

}
