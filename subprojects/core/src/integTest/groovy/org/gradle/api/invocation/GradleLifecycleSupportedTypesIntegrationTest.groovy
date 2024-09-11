/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.invocation

import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.model.ObjectFactory
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.event.ListenerManager
import org.gradle.process.ExecOperations
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.slf4j.Logger

import javax.inject.Inject
import java.nio.charset.Charset
import java.util.logging.Level

class GradleLifecycleSupportedTypesIntegrationTest extends AbstractIntegrationSpec {

    def "lifecycle action can carry instances of #type"() {
        given:
        settingsFile << """
            import java.util.concurrent.*

            class SomeBean {
                ${type} value
            }

            enum SomeEnum {
                One, Two
            }

            def configureGradleLifecycle() {
                SomeBean bean = new SomeBean()
                ${type} value = ${reference}
                bean.value = ${reference}
                gradle.lifecycle.beforeProject {
                    println "this.value = " + value
                    println "bean.value = " + bean.value
                }
            }

            configureGradleLifecycle()
        """
        def expected = output instanceof Closure ? output.call(testDirectory) : output

        when:
        succeeds 'help'

        then:
        outputContains("this.value = ${expected}")
        outputContains("bean.value = ${expected}")

        where:
        type                                 | reference                                 | output
        String.name                          | "'value'"                                 | "value"
        String.name                          | "null"                                    | "null"
        Boolean.name                         | "true"                                    | "true"
        boolean.name                         | "true"                                    | "true"
        Character.name                       | "'a'"                                     | "a"
        char.name                            | "'a'"                                     | "a"
        Byte.name                            | "12"                                      | "12"
        byte.name                            | "12"                                      | "12"
        Short.name                           | "12"                                      | "12"
        short.name                           | "12"                                      | "12"
        Integer.name                         | "12"                                      | "12"
        int.name                             | "12"                                      | "12"
        Long.name                            | "12"                                      | "12"
        long.name                            | "12"                                      | "12"
        Float.name                           | "12.1"                                    | "12.1"
        float.name                           | "12.1"                                    | "12.1"
        Double.name                          | "12.1"                                    | "12.1"
        double.name                          | "12.1"                                    | "12.1"
        Class.name                           | "SomeBean"                                | "class SomeBean"
        URL.name                             | "new URL('https://gradle.org/')"          | "https://gradle.org/"
        URI.name                             | "URI.create('https://gradle.org/')"       | "https://gradle.org/"
        Level.name                           | "${Level.name}.INFO"                      | "INFO"
        Charset.name                         | "${Charset.name}.forName('UTF-8')"        | "UTF-8"
        "SomeEnum"                           | "SomeEnum.Two"                            | "Two"
        "SomeEnum[]"                         | "[SomeEnum.Two] as SomeEnum[]"            | "[Two]"
        "List<String>"                       | "['a', 'b', 'c']"                         | "[a, b, c]"
        "ArrayList<String>"                  | "['a', 'b', 'c'] as ArrayList"            | "[a, b, c]"
        "LinkedList<String>"                 | "['a', 'b', 'c'] as LinkedList"           | "[a, b, c]"
        "CopyOnWriteArrayList<String>"       | "['a', 'b', 'c'] as CopyOnWriteArrayList" | "[a, b, c]"
        "Set<String>"                        | "['a', 'b', 'c'] as Set"                  | "[a, b, c]"
        "HashSet<String>"                    | "['a', 'b', 'c'] as HashSet"              | "[a, b, c]"
        "LinkedHashSet<String>"              | "['a', 'b', 'c'] as LinkedHashSet"        | "[a, b, c]"
        "CopyOnWriteArraySet<String>"        | "['a', 'b', 'c'] as CopyOnWriteArraySet"  | "[a, b, c]"
        "TreeSet<String>"                    | "['a', 'b', 'c'] as TreeSet"              | "[a, b, c]"
        "EnumSet<SomeEnum>"                  | "EnumSet.of(SomeEnum.Two)"                | "[Two]"
        "Map<String, Integer>"               | "[a: 1, b: 2]"                            | "[a:1, b:2]"
        "HashMap<String, Integer>"           | "new HashMap([a: 1, b: 2])"               | "[a:1, b:2]"
        "LinkedHashMap<String, Integer>"     | "new LinkedHashMap([a: 1, b: 2])"         | "[a:1, b:2]"
        "TreeMap<String, Integer>"           | "new TreeMap([a: 1, b: 2])"               | "[a:1, b:2]"
        "TreeMap<String, Integer>"           | treeMapWithComparator()                   | "[b:2, a:1]"
        "ConcurrentHashMap<String, Integer>" | "new ConcurrentHashMap([a: 1, b: 2])"     | "[a:1, b:2]"
        "EnumMap<SomeEnum, String>"          | enumMapToString()                         | "[One:one, Two:two]"
        "ArrayDeque<String>"                 | "['a', 'b', 'c'] as ArrayDeque"           | "[a, b, c]"
        "byte[]"                             | "[Byte.MIN_VALUE, Byte.MAX_VALUE]"        | "[-128, 127]"
        "short[]"                            | "[Short.MIN_VALUE, Short.MAX_VALUE]"      | "[-32768, 32767]"
        "int[]"                              | integerArray()                            | "[-2147483648, 2147483647]"
        "long[]"                             | "[Long.MIN_VALUE, Long.MAX_VALUE]"        | "[-9223372036854775808, 9223372036854775807]"
        "float[]"                            | floatArray()                              | "[1.4E-45, NaN, 3.4028235E38]"
        "double[]"                           | doubleArray()                             | "[4.9E-324, NaN, 1.7976931348623157E308]"
        "boolean[]"                          | "[true, false]"                           | "[true, false]"
        "char[]"                             | "['a', 'b', 'c']"                         | "abc"
        "Directory"                          | "layout.rootDirectory.dir('foo')"         | { it.file("foo") }
        "RegularFile"                        | "layout.rootDirectory.file('bar.txt')"    | { it.file("bar.txt") }
    }

    def "lifecycle action can carry service of type #type"() {
        settingsFile << """
            class SomeBean {
                ${type} value
            }

            def configureGradleLifecycle() {
                SomeBean bean = new SomeBean()
                ${type} value
                value = ${reference}
                bean.value = ${reference}
                gradle.lifecycle.beforeProject {
                    value.${invocation}
                    bean.value.${invocation}
                }
            }

            configureGradleLifecycle()
        """

        when:
        succeeds 'help'

        then:
        noExceptionThrown()

        where:
        type                             | reference                                           | invocation
        Logger.name                      | "logger"                                            | "info('hi')"
        ObjectFactory.name               | "services.get(${ObjectFactory.name})"               | "newInstance(SomeBean)"
        ToolingModelBuilderRegistry.name | "services.get(${ToolingModelBuilderRegistry.name})" | "toString()"
        FileSystemOperations.name        | "services.get(${FileSystemOperations.name})"        | "toString()"
        ArchiveOperations.name           | "services.get(${ArchiveOperations.name})"           | "toString()"
        ExecOperations.name              | "services.get(${ExecOperations.name})"              | "toString()"
        ListenerManager.name             | "services.get(${ListenerManager.name})"             | "toString()"
    }

    def "lifecycle action can carry provider of type #type"() {
        settingsFile << """
            import ${Inject.name}

            class SomeBean {
                ${type} value
            }

            def configureGradleLifecycle() {
                def objects = services.get(${ObjectFactory.name})
                SomeBean bean = objects.newInstance(SomeBean)

                ${type} value
                value = ${reference}
                bean.value = ${reference}

                gradle.lifecycle.beforeProject {
                    println "this.value = " + value.getOrNull()
                    println "bean.value = " + bean.value.getOrNull()
                }
            }

            configureGradleLifecycle()
        """

        when:
        succeeds 'help'

        then:
        outputContains("this.value = ${output}")
        outputContains("bean.value = ${output}")

        where:
        type               | reference                                 | output
        "Provider<String>" | "providers.provider { 'value' }"          | "value"
        "Provider<String>" | "providers.provider { null }"             | "null"
        "Provider<String>" | "objects.property(String).value('value')" | "value"
        "Provider<String>" | "objects.property(String)"                | "null"
    }

    def "lifecycle action can carry property of type #type"() {
        settingsFile << """
            import ${Inject.name}

            class SomeBean {
                @Internal
                final ${type} value

                @Inject
                SomeBean(ObjectFactory objects) {
                    value = ${factory}
                }
            }

            def configureGradleLifecycle() {
                def objects = services.get(${ObjectFactory.name})

                SomeBean bean = objects.newInstance(SomeBean)
                ${type} value = ${factory}
                value.set(${reference})
                bean.value = ${reference}

                gradle.lifecycle.beforeProject {
                    println "this.value = " + value.getOrNull()
                    println "bean.value = " + bean.value.getOrNull()
                }
            }

            configureGradleLifecycle()
        """

        when:
        succeeds 'help'

        then:
        def expected = output instanceof File ? file(output.path) : output
        outputContains("this.value = ${expected}")
        outputContains("bean.value = ${expected}")

        where:
        type                          | factory                               | reference            | output
        "Property<String>"            | "objects.property(String)"            | "'value'"            | "value"
        "Property<String>"            | "objects.property(String)"            | "(String) null"      | "null"
        "DirectoryProperty"           | "objects.directoryProperty()"         | "file('abc')"        | new File('abc')
        "DirectoryProperty"           | "objects.directoryProperty()"         | "(File) null"        | "null"
        "RegularFileProperty"         | "objects.fileProperty()"              | "file('abc')"        | new File('abc')
        "RegularFileProperty"         | "objects.fileProperty()"              | "(File) null"        | "null"
        "ListProperty<String>"        | "objects.listProperty(String)"        | "[]"                 | "[]"
        "ListProperty<String>"        | "objects.listProperty(String)"        | "['abc']"            | ['abc']
        "ListProperty<String>"        | "objects.listProperty(String)"        | "(Iterable) null"    | "null"
        "SetProperty<String>"         | "objects.setProperty(String)"         | "[]"                 | "[]"
        "SetProperty<String>"         | "objects.setProperty(String)"         | "['abc']"            | ['abc']
        "SetProperty<String>"         | "objects.setProperty(String)"         | "(Iterable) null"    | "null"
        "MapProperty<String, String>" | "objects.mapProperty(String, String)" | "[:]"                | [:]
        "MapProperty<String, String>" | "objects.mapProperty(String, String)" | "['abc': 'def']"     | ['abc': 'def']
        "MapProperty<String, String>" | "objects.mapProperty(String, String)" | "(Map) null"         | "null"
        "Property<$Level.name>"       | "objects.property($Level.name)"       | "${Level.name}.INFO" | "INFO"
    }

    private String integerArray() {
        "[Integer.MIN_VALUE, Integer.MAX_VALUE]"
    }

    private String floatArray() {
        "[Float.MIN_VALUE, Float.NaN, Float.MAX_VALUE]"
    }

    private String doubleArray() {
        "[Double.MIN_VALUE, Double.NaN, Double.MAX_VALUE]"
    }

    private String enumMapToString() {
        "new EnumMap([(SomeEnum.One): 'one', (SomeEnum.Two): 'two'])"
    }

    private String treeMapWithComparator() {
        "new TreeMap({ x, y -> y.compareTo(x) }).tap { putAll([a: 1, b: 2]) }"
    }
}
