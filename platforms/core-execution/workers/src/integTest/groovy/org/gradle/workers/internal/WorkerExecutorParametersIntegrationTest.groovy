/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.workers.internal

import org.gradle.api.services.BuildServiceParameters
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import java.util.concurrent.atomic.AtomicInteger

import static org.gradle.workers.fixtures.WorkerExecutorFixture.ISOLATION_MODES

class WorkerExecutorParametersIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            import org.gradle.workers.WorkerExecutor

            class ParameterTask extends DefaultTask {
                private final WorkerExecutor workerExecutor

                @Internal
                String isolationMode
                @Internal
                Closure paramConfig

                @Inject
                ParameterTask(WorkerExecutor workerExecutor) {
                    this.workerExecutor = workerExecutor
                }

                @TaskAction
                void doWork() {
                    def parameterAction = paramConfig != null ? paramConfig : {}
                    workerExecutor."\${isolationMode}"().submit(ParameterWorkAction.class, parameterAction)
                }

                void parameters(Closure closure) {
                    paramConfig = closure
                }
            }
        """
    }

    def "can provide named parameters with isolation mode #isolationMode"() {
        buildFile << """
            interface Foo extends Named { }
            ext.testObject = objects.named(Foo.class, "bar")

            ${parameterWorkAction('Named', 'println parameters.testParam.name', true)}

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                def namedParam = testObject
                parameters {
                    testParam = namedParam
                }
            }
        """

        when:
        succeeds("runWork")

        then:
        outputContains("bar")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can provide property parameters with isolation mode #isolationMode"() {
        buildFile << """
            ${parameterWorkAction('Property<String>', 'println parameters.testParam.get()')}

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    testParam.set("bar")
                }
            }
        """

        when:
        succeeds("runWork")

        then:
        outputContains("bar")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can provide file collection parameters with isolation mode #isolationMode"() {
        buildFile << """
            ${parameterWorkAction('ConfigurableFileCollection', 'parameters.testParam.files.each { println it.name }')}

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    testParam.from("bar")
                }
            }
        """

        when:
        succeeds("runWork")

        then:
        outputContains("bar")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can provide array parameters with isolation mode #isolationMode"() {
        buildFile << """
            ext.testObject = ["foo", "bar"] as String[]

            ${parameterWorkAction('String[]', 'println "param = " + Arrays.asList(parameters.testParam)', true) }

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                def arrayParam = testObject
                parameters {
                    testParam = arrayParam
                }
            }
        """

        when:
        succeeds("runWork")

        then:
        outputContains("param = [foo, bar]")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can provide zero-length array parameters with isolation mode #isolationMode"() {
        buildFile << """
            ext.testObject = [] as String[]

            ${parameterWorkAction('String[]', 'println "param = " + Arrays.asList(parameters.testParam)', true) }

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                def arrayParam = testObject
                parameters {
                    testParam = arrayParam
                }
            }
        """

        when:
        succeeds("runWork")

        then:
        outputContains("param = []")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can provide list property parameters with isolation mode #isolationMode"() {
        buildFile << """
            ${parameterWorkAction('ListProperty<String>', 'println parameters.testParam.get().join(",")') }

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    testParam.add("foo")
                    testParam.add("bar")
                }
            }
        """

        when:
        succeeds("runWork")

        then:
        outputContains("foo,bar")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can provide set property parameters with isolation mode #isolationMode"() {
        buildFile << """
            ${parameterWorkAction('SetProperty<String>', 'println parameters.testParam.get().join(",")') }

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    testParam.add("foo")
                    testParam.add("bar")
                }
            }
        """

        when:
        succeeds("runWork")

        then:
        outputContains("foo,bar")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can provide map property parameters with isolation mode #isolationMode"() {
        buildFile << """
            ${parameterWorkAction('MapProperty<String, String>', 'println parameters.testParam.get().collect { it.key + ":" + it.value }.join(",")') }

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    testParam.put("foo", "bar")
                    testParam.put("bar", "baz")
                }
            }
        """

        when:
        succeeds("runWork")

        then:
        outputContains("foo:bar,bar:baz")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can provide a Properties object with isolation mode #isolationMode"() {
        buildFile << """
            ${parameterWorkAction('Properties', 'println parameters.testParam.collect { it.key + ":" + it.value }.join(",")', true) }

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    testParam = new Properties()
                    testParam.setProperty("foo", "bar")
                    testParam.setProperty("bar", "baz")
                }
            }
        """

        when:
        succeeds("runWork")

        then:
        outputContains("bar:baz,foo:bar")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can provide file property parameters with isolation mode #isolationMode"() {
        buildFile << """
            ${parameterWorkAction('RegularFileProperty', 'println parameters.testParam.get().getAsFile().name')}

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    testParam.set(new File("bar"))
                }
            }
        """

        when:
        succeeds("runWork")

        then:
        outputContains("bar")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can provide directory property parameters with isolation mode #isolationMode"() {
        buildFile << """
            ${parameterWorkAction('DirectoryProperty', 'println parameters.testParam.get().getAsFile().name')}

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    testParam.set(new File("bar"))
                }
            }
        """

        when:
        succeeds("runWork")

        then:
        outputContains("bar")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can provide build service parameters with isolation mode #isolationMode"() {
        buildFile << """
            import ${BuildServiceParameters.name}
            import ${AtomicInteger.name}

            ${parameterWorkAction('Property<CountingService>', 'println "value = " + parameters.testParam.get().increment()')}

            abstract class CountingService implements BuildService<BuildServiceParameters.None> {
                private final value = new AtomicInteger()

                int increment() {
                    def value = value.incrementAndGet()
                    println("service: value is \${value}")
                    return value
                }
            }

            def countingService = gradle.sharedServices.registerIfAbsent("counting", CountingService) { }

            task runWork(type: ParameterTask) {
                isolationMode = '${isolationMode}'
                parameters {
                    testParam.set(countingService)
                }
            }
        """

        when:
        succeeds("runWork")

        then:
        outputContains("service: value is 1")
        outputContains("value = 1")

        where:
        // TODO - this should work with classloader isolation too
        isolationMode << ['noIsolation']
    }

    def "can provide managed object parameters with isolation mode #isolationMode"() {
        file('buildSrc/src/main/java').createDir()
        file('buildSrc/src/main/java/SomeExtension.java').text = """
            public interface SomeExtension {
                String getValue();
                void setValue(String value);
            }
        """

        buildFile << """
            import org.gradle.internal.instantiation.InstantiatorFactory

            ext.testObject = getServices().get(InstantiatorFactory.class).inject().newInstance(SomeExtension)
            ext.testObject.value = "bar"

            ${parameterWorkAction('SomeExtension', 'println parameters.testParam.value', true) }

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                def objectParam = testObject
                parameters {
                    testParam = objectParam
                }
            }
        """

        when:
        succeeds("runWork")

        then:
        outputContains("bar")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can provide a null parameter with isolation mode #isolationMode"() {
        buildFile << """
            ${parameterWorkAction('String', 'println "testParam is " + parameters.testParam', true)}

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    testParam = null
                }
            }
        """

        when:
        succeeds("runWork")

        then:
        outputContains("testParam is null")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can use a work action with a None parameter and isolation mode #isolationMode"() {
        buildFile << """
            ${noneParameterWorkAction('println "there is no parameter"')}

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
            }
        """

        when:
        succeeds("runWork")

        then:
        outputContains("there is no parameter")

        where:
        isolationMode << ISOLATION_MODES
    }

    String parameterWorkAction(String type, String action, boolean requiresSetter = false) {
        return """
            import org.gradle.workers.WorkAction
            import org.gradle.workers.WorkParameters

            interface TestParameters extends WorkParameters {
                ${type} getTestParam();
                ${-> requiresSetter ? "void setTestParam(${type} testParam);" : ''}
            }

            abstract class ParameterWorkAction implements WorkAction<TestParameters> {
                void execute() {
                    ${action}
                }
            }
        """
    }

    String noneParameterWorkAction(String action) {
        return """
            import org.gradle.workers.WorkAction
            import org.gradle.workers.WorkParameters

            abstract class ParameterWorkAction implements WorkAction<WorkParameters.None> {
                void execute() {
                    ${action}
                }
            }
        """
    }
}

