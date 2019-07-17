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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.workers.fixtures.WorkerExecutorFixture
import spock.lang.Unroll

import static org.gradle.workers.fixtures.WorkerExecutorFixture.ISOLATION_MODES

@Unroll
class WorkerExecutorParametersIntegrationTest extends AbstractIntegrationSpec {
    WorkerExecutorFixture fixture = new WorkerExecutorFixture(temporaryFolder)

    def setup() {
        buildFile << """
            import javax.inject.Inject
            import org.gradle.workers.WorkerExecutor
            
            class ParameterTask extends DefaultTask {
                WorkerExecutor workerExecutor
                IsolationMode isolationMode
                Closure paramConfig

                @Inject
                ParameterTask(WorkerExecutor workerExecutor) {
                    this.workerExecutor = workerExecutor
                }

                @TaskAction
                void doWork() {
                    def parameterAction = paramConfig != null ? paramConfig : {}
                    workerExecutor."\${getWorkerMethod(isolationMode)}"().submit(ParameterWorkerExecution.class, parameterAction)
                }
                
                void parameters(Closure closure) {
                    paramConfig = closure
                }
                
                ${fixture.workerMethodTranslation}
            }  
        """
    }

    def "can provide named parameters with isolation mode #isolationMode"() {
        buildFile << """
            interface Foo extends Named { }
            ext.testObject = objects.named(Foo.class, "bar")

            ${parameterWorkerExecution('Named', 'println parameters.testParam.name', true)}

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    testParam = testObject
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
            ${parameterWorkerExecution('Property<String>', 'println parameters.testParam.get()')}

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
            ${parameterWorkerExecution('ConfigurableFileCollection', 'parameters.testParam.files.each { println it.name }')}

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

            ${parameterWorkerExecution('String[]', 'println "param = " + Arrays.asList(parameters.testParam)', true) }

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    testParam = testObject
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

            ${parameterWorkerExecution('String[]', 'println "param = " + Arrays.asList(parameters.testParam)', true) }

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    testParam = testObject
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
            ${parameterWorkerExecution('ListProperty<String>', 'println parameters.testParam.get().join(",")') }

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
            ${parameterWorkerExecution('SetProperty<String>', 'println parameters.testParam.get().join(",")') }

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
            ${parameterWorkerExecution('MapProperty<String, String>', 'println parameters.testParam.get().collect { it.key + ":" + it.value }.join(",")') }

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

    def "can provide file property parameters with isolation mode #isolationMode"() {
        buildFile << """
            ${parameterWorkerExecution('RegularFileProperty', 'println parameters.testParam.get().getAsFile().name')}

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
            ${parameterWorkerExecution('DirectoryProperty', 'println parameters.testParam.get().getAsFile().name')}

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

            ${parameterWorkerExecution('SomeExtension', 'println parameters.testParam.value', true) }

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    testParam = testObject
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
            ${parameterWorkerExecution('String', 'println "testParam is " + parameters.testParam', true)}

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

    def "can use a worker execution with a None parameter and isolation mode #isolationMode"() {
        buildFile << """
            ${noneParameterWorkerExecution('println "there is no parameter"')}

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

    String parameterWorkerExecution(String type, String action, boolean requiresSetter = false) {
        return """
            import org.gradle.workers.WorkerExecution
            import org.gradle.workers.WorkerParameters

            interface TestParameters extends WorkerParameters {
                ${type} getTestParam();
                ${-> requiresSetter ? "void setTestParam(${type} testParam);" : ''}
            }
            
            abstract class ParameterWorkerExecution implements WorkerExecution<TestParameters> {
                void execute() {
                    ${action}
                }
            }
        """
    }

    String noneParameterWorkerExecution(String action) {
        return """
            import org.gradle.workers.WorkerExecution
            import org.gradle.workers.WorkerParameters

            abstract class ParameterWorkerExecution implements WorkerExecution<WorkerParameters.None> {
                void execute() {
                    ${action}
                }
            }
        """
    }
}

