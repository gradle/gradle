/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.event.ListenerManager
import org.gradle.process.ExecOperations
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.workers.WorkerExecutor
import org.slf4j.Logger
import spock.lang.Issue

import javax.inject.Inject
import java.util.logging.Level

class ConfigurationCacheSupportedTypesIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "restores task fields whose value is instance of #type"() {
        buildFile << """
            import java.util.concurrent.*

            class SomeBean {
                ${type} value
            }

            enum SomeEnum {
                One, Two
            }

            class SomeTask extends DefaultTask {
                private final SomeBean bean = new SomeBean()
                private final ${type} value

                SomeTask() {
                    value = ${reference}
                    bean.value = ${reference}
                }

                @TaskAction
                void run() {
                    println "this.value = " + value
                    println "bean.value = " + bean.value
                }
            }

            task ok(type: SomeTask)
        """

        when:
        configurationCacheRun "ok"
        configurationCacheRun "ok"

        then:
        outputContains("this.value = ${output}")
        outputContains("bean.value = ${output}")

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

    def "keeps iteration order of #type instances"() {
        given:
        buildFile << """
            abstract class SomeTask extends DefaultTask {
                private def underTest = $init
                @TaskAction def action() {
                    println("ORDER=${'$'}{$iterate}")
                }
            }
            tasks.register("ok", SomeTask)
        """

        when:
        configurationCacheRun "ok"
        def expected = result.output.readLines().find { it.startsWith("ORDER=") }.substring(6)

        and:
        configurationCacheRun "ok"

        then:
        outputContains(expected)

        where:
        type      | init                                               | iterate
        'HashSet' | "['first', 'second', 'third'] as HashSet"          | "underTest.join(', ')"
        'HashMap' | "['first': 1, 'second': 2, 'third': 3] as HashMap" | 'underTest.collect { k,v -> "$k=$v" }.join(", ")'
    }

    def "restores task fields whose value is instance of plugin specific version of Guava #type"() {
        buildFile << """
            import ${type.name}

            buildscript {
                ${mavenCentralRepository()}
                dependencies {
                    classpath 'com.google.guava:guava:28.0-jre'
                }
            }

            class SomeBean {
                ${type.simpleName} value
            }

            class SomeTask extends DefaultTask {
                private final SomeBean bean = new SomeBean()
                private final ${type.simpleName} value

                SomeTask() {
                    value = ${reference}
                    bean.value = ${reference}
                }

                @TaskAction
                void run() {
                    println "this.value = " + value
                    println "bean.value = " + bean.value
                }
            }

            task ok(type: SomeTask)
        """

        when:
        configurationCacheRun "ok"
        configurationCacheRun "ok"

        then:
        outputContains("this.value = ${output}")
        outputContains("bean.value = ${output}")

        where:
        type          | reference                         | output
        ImmutableList | "ImmutableList.of('a', 'b', 'c')" | "[a, b, c]"
        ImmutableSet  | "ImmutableSet.of('a', 'b', 'c')"  | "[a, b, c]"
        ImmutableMap  | "ImmutableMap.of(1, 'a', 2, 'b')" | "[1:a, 2:b]"
    }

    def "restores task fields whose value is service of type #type"() {
        buildFile << """
            class SomeBean {
                ${type} value
            }

            class SomeTask extends DefaultTask {
                @Internal
                final SomeBean bean = new SomeBean()
                @Internal
                ${type} value

                @TaskAction
                void run() {
                    value.${invocation}
                    bean.value.${invocation}
                }
            }

            task ok(type: SomeTask) {
                value = ${reference}
                bean.value = ${reference}
            }
        """

        when:
        configurationCacheRun "ok"
        configurationCacheRun "ok"

        then:
        noExceptionThrown()

        where:
        type                             | reference                                                   | invocation
        Logger.name                      | "logger"                                                    | "info('hi')"
        ObjectFactory.name               | "objects"                                                   | "newInstance(SomeBean)"
        ToolingModelBuilderRegistry.name | "project.services.get(${ToolingModelBuilderRegistry.name})" | "toString()"
        WorkerExecutor.name              | "project.services.get(${WorkerExecutor.name})"              | "noIsolation()"
        FileSystemOperations.name        | "project.services.get(${FileSystemOperations.name})"        | "toString()"
        ArchiveOperations.name           | "project.services.get(${ArchiveOperations.name})"           | "toString()"
        ExecOperations.name              | "project.services.get(${ExecOperations.name})"              | "toString()"
        ListenerManager.name             | "project.services.get(${ListenerManager.name})"             | "toString()"
    }

    def "restores task fields whose value is provider of type #type"() {
        buildFile << """
            import ${Inject.name}

            class SomeBean {
                ${type} value
            }

            class SomeTask extends DefaultTask {
                @Internal
                final SomeBean bean = project.objects.newInstance(SomeBean)
                @Internal
                ${type} value

                @TaskAction
                void run() {
                    println "this.value = " + value.getOrNull()
                    println "bean.value = " + bean.value.getOrNull()
                }
            }

            task ok(type: SomeTask) {
                value = ${reference}
                bean.value = ${reference}
            }
        """

        when:
        configurationCacheRun "ok"
        configurationCacheRun "ok"

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

    def "restores task fields whose value is broken #type"() {
        def configurationCache = newConfigurationCacheFixture()

        buildFile << """
            import ${Inject.name}

            class SomeTask extends DefaultTask {
                @Internal
                ${type} value = ${reference} { throw new RuntimeException("broken!") }

                @TaskAction
                void run() {
                    println "this.value = " + value.${query}
                }
            }

            task broken(type: SomeTask) {
            }
        """

        when:
        configurationCacheFails "broken"

        then:
        configurationCache.assertStateStoreFailed()
        failure.assertHasDescription("Configuration cache state could not be cached: field `value` of task `:broken` of type `SomeTask`: error writing value of type 'org.gradle.api.internal.provider.DefaultProvider'")
        failure.assertHasCause("broken!")

        where:
        type               | reference                    | query   | problem
        "Provider<String>" | "project.providers.provider" | "get()" | "value 'provider(?)' failed to unpack provider"
    }

    def "restores task fields whose value is property of type #type"() {
        buildFile << """
            import ${Inject.name}

            class SomeBean {
                @Internal
                final ${type} value

                @Inject
                SomeBean(ObjectFactory objects) {
                    value = ${factory}
                }
            }

            class SomeTask extends DefaultTask {
                @Internal
                final SomeBean bean = project.objects.newInstance(SomeBean)
                @Internal
                final ${type} value

                @Inject
                SomeTask(ObjectFactory objects) {
                    value = ${factory}
                }

                @TaskAction
                void run() {
                    println "this.value = " + value.getOrNull()
                    println "bean.value = " + bean.value.getOrNull()
                }
            }

            task ok(type: SomeTask) {
                value = ${reference}
                bean.value = ${reference}
            }
        """

        when:
        configurationCacheRun "ok"
        configurationCacheRun "ok"

        then:
        def expected = output instanceof File ? file(output.path) : output
        outputContains("this.value = ${expected}")
        outputContains("bean.value = ${expected}")

        where:
        type                          | factory                               | reference            | output
        "Property<String>"            | "objects.property(String)"            | "'value'"            | "value"
        "Property<String>"            | "objects.property(String)"            | "null"               | "null"
        "DirectoryProperty"           | "objects.directoryProperty()"         | "file('abc')"        | new File('abc')
        "DirectoryProperty"           | "objects.directoryProperty()"         | "null"               | "null"
        "RegularFileProperty"         | "objects.fileProperty()"              | "file('abc')"        | new File('abc')
        "RegularFileProperty"         | "objects.fileProperty()"              | "null"               | "null"
        "ListProperty<String>"        | "objects.listProperty(String)"        | "[]"                 | "[]"
        "ListProperty<String>"        | "objects.listProperty(String)"        | "['abc']"            | ['abc']
        "ListProperty<String>"        | "objects.listProperty(String)"        | "null"               | "null"
        "SetProperty<String>"         | "objects.setProperty(String)"         | "[]"                 | "[]"
        "SetProperty<String>"         | "objects.setProperty(String)"         | "['abc']"            | ['abc']
        "SetProperty<String>"         | "objects.setProperty(String)"         | "null"               | "null"
        "MapProperty<String, String>" | "objects.mapProperty(String, String)" | "[:]"                | [:]
        "MapProperty<String, String>" | "objects.mapProperty(String, String)" | "['abc': 'def']"     | ['abc': 'def']
        "MapProperty<String, String>" | "objects.mapProperty(String, String)" | "null"               | "null"
        "Property<$Level.name>"       | "objects.property($Level.name)"       | "${Level.name}.INFO" | "INFO"
    }

    def "restores task fields whose value is FileCollection"() {
        buildFile << """
            import ${Inject.name}

            class SomeBean {
                @Internal
                final FileCollection value

                @Inject
                SomeBean(ProjectLayout layout) {
                    value = ${factory}
                }
            }

            class SomeTask extends DefaultTask {
                @Internal
                final SomeBean bean = project.objects.newInstance(SomeBean)
                @Internal
                final FileCollection value

                @Inject
                SomeTask(ProjectLayout layout) {
                    value = ${factory}
                }

                @TaskAction
                void run() {
                    println "this.value = " + value.files
                    println "bean.value = " + bean.value.files
                }
            }

            task ok(type: SomeTask) {
            }
        """

        when:
        configurationCacheRun "ok"
        configurationCacheRun "ok"

        then:
        def expected = output.collect { file(it) }
        outputContains("this.value = ${expected}")
        outputContains("bean.value = ${expected}")

        where:
        factory                  | output
        "layout.files()"         | []
        "layout.files('a', 'b')" | ['a', 'b']
    }

    def "restores task fields whose value is #kind TextResource"() {

        given:
        file("resource.txt") << 'content'
        createZip("resource.zip") {
            file("resource.txt") << 'content'
        }

        and:
        buildFile << """

            class SomeTask extends DefaultTask {

                @Input
                TextResource textResource = project.resources.text.$expression

                @TaskAction
                def action() {
                    println('> ' + textResource.asString())
                }
            }

            tasks.register("someTask", SomeTask)
        """

        when:
        configurationCacheRun 'someTask'

        then:
        outputContains("> content")

        when:
        configurationCacheRun 'someTask'

        then:
        outputContains("> content")

        where:
        kind               | expression
        'a string'         | 'fromString("content")'
        'a file'           | 'fromFile("resource.txt")'
        'an uri'           | 'fromUri(project.uri(project.file("resource.txt")))'
        'an insecure uri'  | 'fromInsecureUri(project.uri(project.file("resource.txt")))'
        'an archive entry' | 'fromArchiveEntry("resource.zip", "resource.txt")'
    }

    @Issue('https://github.com/gradle/gradle/issues/22255')
    def "finalizeValueOnRead property provider is evaluated only once"() {
        given:
        buildFile << """
            class Oracle extends DefaultTask {

                @Internal final Property<String> answer

                Oracle() {
                    answer = project.objects.property(String)
                    answer.finalizeValueOnRead()
                    answer.set(
                        project.provider {
                            println 'Thinking...'
                            '42'
                        }
                    )
                }

                @TaskAction
                def answer() {
                    println('The answer is ' + answer.get())
                }
            }

            tasks.register('oracle', Oracle)
        """

        when:
        configurationCacheRun 'oracle'

        then:
        output.count('Thinking...') == 1

        when:
        configurationCacheRun 'oracle'

        then:
        output.count('Thinking...') == 0
        outputContains 'The answer is 42'
    }
}
