/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

import static org.hamcrest.CoreMatchers.containsString

class ManagedModelGroovyScalarConfigurationIntegrationTest extends AbstractIntegrationSpec {

    private static final String CLASSES = '''
        enum Thing {
            TOASTER,
            NOT_A_TOASTER
        }

        @Managed
        interface Props {
            Thing getTheThing()
            void setTheThing(Thing t)

            boolean isBool1()
            void setBool1(boolean b)

            boolean isBool2()
            void setBool2(boolean b)

            Boolean getTheBoolean()
            void setTheBoolean(Boolean b)

            byte getThebyte()
            void setThebyte(byte b)

            char getThechar()
            void setThechar(char c)

            Character getTheCharacter()
            void setTheCharacter(Character c)

            int getTheint()
            void setTheint(int i)

            Integer getTheInteger()
            void setTheInteger(Integer i)

            short getTheshort()
            void setTheshort(short s)

            String getTheString()
            void setTheString(String s)

            void setTheByte(Byte b)
            Byte getTheByte()

            Short getTheShort()
            void setTheShort(Short s)

            float getThefloat()
            void setThefloat(float f)

            Float getTheFloat()
            void setTheFloat(Float f)

            Long getTheLong()
            void setTheLong(Long l)

            long getThelong()
            void setThelong(long l)

            Double getTheDouble()
            void setTheDouble(Double d)

            double getThedouble()
            void setThedouble(double d)

            BigInteger getTheBigInteger()
            void setTheBigInteger(BigInteger b)

            BigDecimal getTheBigDecimal()
            void setTheBigDecimal(BigDecimal b)
        }

        class RulePlugin extends RuleSource {
            @Model
            void props(Props p) {}

            @Mutate
            void addTask(ModelMap<Task> tasks, Props p) {
                tasks.create('printResolvedValues') {
                    doLast {
                        println "prop theBigDecimal: $p.theBigDecimal :"
                        println "prop theBigInteger: $p.theBigInteger :"
                        println "prop bool1        : $p.bool1 :"
                        println "prop bool2        : $p.bool2 :"
                        println "prop theBoolean   : $p.theBoolean :"
                        println "prop thebyte      : $p.thebyte :"
                        println "prop theByte      : $p.theByte :"
                        println "prop thechar      : $p.thechar :"
                        println "prop theCharacter : $p.theCharacter :"
                        println "prop theDouble    : $p.theDouble :"
                        println "prop thedouble    : $p.thedouble :"
                        println "prop thefloat     : $p.thefloat :"
                        println "prop theFloat     : $p.theFloat :"
                        println "prop theint       : $p.theint :"
                        println "prop theInteger   : $p.theInteger :"
                        println "prop theLong      : $p.theLong :"
                        println "prop thelong      : $p.thelong :"
                        println "prop theshort     : $p.theshort :"
                        println "prop theShort     : $p.theShort :"
                        println "prop theString    : $p.theString :"
                        println "prop theThing     : $p.theThing :"
                    }
                }
            }
        }

        apply type: RulePlugin
        '''

    @Unroll
    void 'only CharSequence input values are supported - #varname'() {
        when:
        buildFile << CLASSES
        buildFile << """
            model {
                props {
                    $varname = new Object()
                }
            }
            """

        then:
        fails 'printResolvedValues'

        and:
        failure.assertHasLineNumber(111)
        failure.assertHasCause("Cannot set property: $varname for class: Props to value: java.lang.Object")
        failure.assertThatCause(containsString('''The following types/formats are supported:
  - A String or CharSequence
  - Any Number'''))

        where:
        // not including char, Character, and String since Groovy auto-coerces to String,
        // or boolean/Boolean since those are special cased for 'true'
        varname << ['theBigDecimal', 'theBigInteger', 'theDouble', 'thedouble', 'thefloat', 'theFloat', 'theint',
                    'theInteger', 'theLong', 'thelong', 'theshort', 'theShort', 'thebyte', 'theByte']
    }

    void 'reports supported input types for enum property'() {
        when:
        buildFile << CLASSES
        buildFile << """
            model {
                props {
                    theThing = $value
                }
            }
            """

        then:
        fails 'printResolvedValues'

        and:
        failure.assertHasLineNumber(111)
        failure.assertHasCause("Cannot set property: theThing for class: Props to value: $value.")
        failure.assertHasCause("""Cannot convert the provided notation to an object of type Thing: $value.
The following types/formats are supported:
  - One of the following values: 'TOASTER', 'NOT_A_TOASTER'""")

        where:
        value << ["12", "false"]
    }

    @Unroll
    void 'number types require stringified numeric inputs - #varname'() {
        when:
        buildFile << CLASSES
        buildFile << """
            model {
                props {
                    $varname = '42foo'
                }
            }
            """

        then:
        fails 'printResolvedValues'

        and:
        failure.assertHasLineNumber(111)
        failure.assertHasCause("Cannot set property: ${varname} for class: Props to value: 42foo.")
        failure.assertHasCause("Cannot convert value '42foo' to type $type.simpleName")

        where:
        varname         | type
        'theBigDecimal' | BigDecimal
        'theBigInteger' | BigInteger
        'theDouble'     | Double
        'thedouble'     | double
        'thefloat'      | float
        'theFloat'      | Float
        'theint'        | int
        'theInteger'    | Integer
        'theLong'       | Long
        'thelong'       | long
        'theshort'      | short
        'theShort'      | Short
        'thebyte'       | byte
        'theByte'       | Byte
    }

    @Unroll
    void 'primitive types cannot accept null values'() {
        when:
        buildFile << CLASSES
        buildFile << """
            model {
                props {
                    $varname = null
                }
            }
            """

        then:
        fails 'printResolvedValues'

        and:
        failure.assertHasLineNumber(111)
        failure.assertHasCause("Cannot set property: $varname for class: Props to value: null.")
        failure.assertHasCause("""Cannot convert a null value to an object of type $type.
The following types/formats are supported:
  - A String or CharSequence""")

                where:
        varname     | type
        'bool1'     | boolean
        'thedouble' | double
        'thefloat'  | float
        'theint'    | int
        'thelong'   | long
        'theshort'  | short
        'thebyte'   | byte
        'thechar'   | char
    }

    @Unroll
    void 'non-primitive types can accept null values'() {
        when:
        buildFile << CLASSES
        buildFile << '''
            model {
                props {
                    theBigDecimal = null
                    theBigInteger = null
                    theBoolean = null
                    theDouble = null
                    theFloat = null
                    theInteger = null
                    theLong = null
                    theShort = null
                    theByte = null
                    theCharacter = null
                    theString = null
                    theThing = null
                }
            }
        '''

        then:
        succeeds 'printResolvedValues'

        and:
        output.contains 'prop theBigDecimal: null'
        output.contains 'prop theBigInteger: null'
        output.contains 'prop theBoolean   : null'
        output.contains 'prop theDouble    : null'
        output.contains 'prop theFloat     : null'
        output.contains 'prop theInteger   : null'
        output.contains 'prop theLong      : null'
        output.contains 'prop theShort     : null'
        output.contains 'prop theByte      : null'
        output.contains 'prop theCharacter : null'
        output.contains 'prop theString    : null'
        output.contains 'prop theThing     : null'
    }

    void 'enum types require valid enum constants'() {
        when:
        buildFile << CLASSES
        buildFile << """
            model {
                props {
                    theThing = 'IS_NOT_A_TOASTER'
                }
            }
            """

        then:
        fails 'printResolvedValues'

        and:
        failure.assertHasLineNumber(111)
        failure.assertHasCause("Cannot set property: theThing for class: Props to value: IS_NOT_A_TOASTER.")
        failure.assertHasCause("Cannot convert string value 'IS_NOT_A_TOASTER' to an enum value of type 'Thing'")
    }

    @Unroll
    void 'boolean types are only true for the literal string "true"'() {
        when:
        buildFile << CLASSES
        buildFile << """
            model {
                props {
                    bool1 = '$value'
                }
            }
            """

        then:
        succeeds 'printResolvedValues'

        and:
        output.contains "prop bool1        : $actual"

        where:
        value   | actual
        'true'  | true
        'TRUE'  | false
        'false' | false
    }

    void 'can convert CharSequence to any scalar type'() {
        when:
        buildFile << CLASSES
        buildFile << '''
            model {
                props {
                    theBigDecimal = "${new BigDecimal('123.4').power(10)}"
                    theBigInteger = "${(Long.MAX_VALUE as BigInteger) * 10}"
                    bool1 = "${1 > 2}"
                    bool2 = "${2 > 1}"
                    theBoolean = "${3 > 2}"
                    theDouble = "${Math.PI - 1}"
                    thedouble = "${Math.E - 1}"
                    thefloat = "${Math.PI - 2}"
                    theFloat = "${Math.E - 2}"
                    theint = "${1 + 2}"
                    theInteger = "${3 + 5}"
                    theLong = "${(long)Integer.MAX_VALUE * 2}"
                    thelong = "${(long)Integer.MAX_VALUE * 3}"
                    theshort = "${8 + 13}"
                    theShort = "${21 + 34}"
                    thebyte = "55"
                    theByte = "89"
                    thechar = "${'managed'[3]}"
                    theCharacter = "${'managed'[4]}"
                    theString = "${'bar/fooooo' - 'ooo'}"
                    theThing = "${Thing.valueOf('NOT_A_TOASTER')}"
                }
            }
        '''

        then:
        succeeds 'printResolvedValues'

        and:
        output.contains 'prop theBigDecimal: 818750535356720922824.4052427776'
        output.contains 'prop theBigInteger: 92233720368547758070'
        output.contains 'prop bool1        : false'
        output.contains 'prop bool2        : true'
        output.contains 'prop theBoolean   : true'
        output.contains 'prop theDouble    : 2.141592653589793'
        output.contains 'prop thedouble    : 1.718281828459045'
        output.contains 'prop thefloat     : 1.1415926'
        output.contains 'prop theFloat     : 0.7182818'
        output.contains 'prop theint       : 3'
        output.contains 'prop theInteger   : 8'
        output.contains 'prop theLong      : 4294967294'
        output.contains 'prop thelong      : 6442450941'
        output.contains 'prop theshort     : 21'
        output.contains 'prop theShort     : 55'
        output.contains 'prop thebyte      : 55'
        output.contains 'prop theByte      : 89'
        output.contains 'prop thechar      : a'
        output.contains 'prop theCharacter : g'
        output.contains 'prop theString    : bar/foo'
        output.contains 'prop theThing     : NOT_A_TOASTER'
    }

    void 'scalar conversion works from a Groovy RuleSource'() {
        when:
        buildFile << CLASSES
        buildFile << '''
            class ConvertRules extends RuleSource {
                @Mutate
                void change(Props p) {
                    p.theBoolean = 'true'
                    p.thelong = '123'
                    p.theString = p.thelong
                    p.theThing = null
                }
            }
            apply plugin: ConvertRules
        '''

        then:
        succeeds 'printResolvedValues'

        and:
        output.contains 'prop theBoolean   : true'
        output.contains 'prop thelong      : 123'
        output.contains 'prop theString    : 123'
        output.contains 'prop theThing     : null'
    }

    void 'can convert CharSequence to File'() {
        when:
        buildFile << '''
            @Managed
            interface Props {
                File getTheFile1()
                void setTheFile1(File f)

                File getTheFile2()
                void setTheFile2(File f)

                File getTheFile3()
                void setTheFile3(File f)

                File getTheFile4()
                void setTheFile4(File f)
            }

            class RulePlugin extends RuleSource {
                @Model
                void props(Props p) {}

                @Mutate
                void addTask(ModelMap<Task> tasks, Props p) {
                    tasks.create('printResolvedValues') {
                        doLast {
                            String projectDirPath = project.projectDir.absolutePath
                            String relative
                            String relativeExpected

                            assert p.theFile1
                            relative = p.theFile1.absolutePath - projectDirPath
                            relativeExpected = project.file('foo.txt').absolutePath - projectDirPath
                            println "1: ${relative == relativeExpected}"
                            assert relative == relativeExpected

                            assert p.theFile2
                            relative = p.theFile2.absolutePath - projectDirPath
                            relativeExpected = project.file('path/to/Thing.java').absolutePath - projectDirPath
                            println "2: ${relative == relativeExpected}"
                            assert relative == relativeExpected

                            assert p.theFile3
                            relative = p.theFile3.absolutePath - projectDirPath
                            relativeExpected = project.file('/path/to/Thing.groovy').absolutePath - projectDirPath
                            println "3: ${relative == relativeExpected}"
                            assert relative == relativeExpected

                            assert p.theFile4
                            relative = p.theFile4.absolutePath - projectDirPath
                            relativeExpected = project.file('file:/foo/bar/baz.sh').absolutePath - projectDirPath
                            println "4: ${relative == relativeExpected}"
                            assert relative == relativeExpected
                        }
                    }
                }
            }

            apply type: RulePlugin

            model {
                props {
                    theFile1 = 'foo.txt'
                    theFile2 = 'path/to/Thing.java'
                    theFile3 = "${'/' + 'path.to.Thing'.replace('.', '/') + '.groovy'}"
                    theFile4 = 'file:/foo/bar/baz.sh'
                }
            }
        '''

        then:
        succeeds 'printResolvedValues'

        and:
        output.contains '1: true'
        output.contains '2: true'
        output.contains '3: true'
        output.contains '4: true'
    }

    void 'CharSequence to File error cases'() {
        given:
        String model = '''
            @Managed
            interface Props {
                File getTheFile()
                void setTheFile(File f)
            }

            class RulePlugin extends RuleSource {
                @Model
                void props(Props p) {}
            }

            apply type: RulePlugin
        '''

        when:
        buildFile.text = model + '''
            model {
                props {
                    theFile = 'http://gradle.org'
                }
            }
        '''

        then:
        fails 'model'

        and:
        failure.assertThatCause(containsString("Cannot convert URL 'http://gradle.org' to a file."))

        when:
        buildFile.text = model + '''
            model {
                props {
                    theFile = new Object()
                }
            }
        '''

        then:
        fails 'model'

        and:
        failure.assertHasCause('Cannot convert the provided notation to an object of type File: ')
        failure.assertThatCause(containsString('''The following types/formats are supported:
  - A String or CharSequence
  - A File'''))
    }

    void 'can convert CharSequence to File for multi-project build'() {

        given:
        String model = '''
            model {
                props {
                    theFile = 'path/to/Thing.java'
                }
            }
        '''

        when:
        settingsFile << "include 'p1', 'p2'"

        buildFile << '''
            @Managed
            interface Props {
                File getTheFile()
                void setTheFile(File f)
            }

            class RulePlugin extends RuleSource {
                @Model
                void props(Props p) {}

                @Mutate
                void addTask(ModelMap<Task> tasks, Props p) {
                    tasks.create('printResolvedValues') {
                        doLast {
                            String projectDirPath = project.projectDir.absolutePath

                            assert p.theFile
                            String relative = p.theFile.absolutePath - projectDirPath
                            String relativeExpected = project.file('path/to/Thing.java').absolutePath - projectDirPath
                            println "$project.name file: $relative ${relative == relativeExpected}"
                            assert relative == relativeExpected
                        }
                    }
                }
            }

            subprojects {
                apply type: RulePlugin
            }
        '''

        file('p1/build.gradle') << model
        file('p2/build.gradle') << model

        then:
        succeeds ':p1:printResolvedValues', ':p2:printResolvedValues'

        and:
        output.contains 'p1 file: /path/to/Thing.java true'.replace('/' as char, File.separatorChar)
        output.contains 'p2 file: /path/to/Thing.java true'.replace('/' as char, File.separatorChar)
    }
}
