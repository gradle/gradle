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
import spock.lang.Unroll

import static org.gradle.workers.fixtures.WorkerExecutorFixture.ISOLATION_MODES

@Unroll
class WorkerExecutorParametersIntegrationTest extends AbstractIntegrationSpec {
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
                    workerExecutor.execute(ParameterWorkerExecution.class) { 
                        isolationMode = this.isolationMode
                        paramConfig.delegate = parameters
                        parameters(paramConfig)
                    }
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

            ${parameterWorkerExecution('Named', 'println parameters.foo.name')}

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    foo = testObject
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
            ext.testObject = objects.property(String)
            testObject.set("bar")

            ${parameterWorkerExecution('Property', 'println parameters.foo.get()')}

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    foo = testObject
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
            ext.testObject = objects.fileCollection()
            testObject.from("bar")

            ${parameterWorkerExecution('FileCollection', 'parameters.foo.files.each { println it.name }')}

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    foo = testObject
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

            ${parameterRunnableWithType('String[]', 'println "param = " + Arrays.asList(param)') }

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                params = [testObject]
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

            ${parameterRunnableWithType('String[]', 'println "param = " + Arrays.asList(param)') }

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                params = [testObject]
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
            ext.testObject = objects.listProperty(String)
            testObject.add("foo")
            testObject.add("bar")

            ${parameterWorkerExecution('ListProperty', 'println parameters.foo.get().join(",")') }

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    foo = testObject
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
            ext.testObject = objects.setProperty(String)
            testObject.add("foo")
            testObject.add("bar")

            ${parameterWorkerExecution('SetProperty', 'println parameters.foo.get().join(",")') }

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    foo = testObject
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
            ext.testObject = objects.mapProperty(String, String)
            testObject.put("foo", "bar")
            testObject.put("bar", "baz")

            ${parameterWorkerExecution('MapProperty', 'println parameters.foo.get().collect { it.key + ":" + it.value }.join(",")') }

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    foo = testObject
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
            ext.testObject = objects.fileProperty()
            testObject.set(new File("bar"))

            ${parameterWorkerExecution('RegularFileProperty', 'println parameters.foo.get().getAsFile().name')}

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    foo = testObject
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
            ext.testObject = objects.directoryProperty()
            testObject.set(new File("bar"))

            ${parameterWorkerExecution('DirectoryProperty', 'println parameters.foo.get().getAsFile().name')}

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    foo = testObject
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

            ${parameterWorkerExecution('SomeExtension', 'println parameters.foo.value') }

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    foo = testObject
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
            ${parameterWorkerExecution('String', 'println "foo is " + parameters.foo')}

            task runWork(type: ParameterTask) {
                isolationMode = ${isolationMode}
                parameters {
                    foo = null
                }
            } 
        """

        when:
        succeeds("runWork")

        then:
        outputContains("foo is null")

        where:
        isolationMode << ISOLATION_MODES
    }

    String parameterRunnableWithType(String type, String action) {
        return """
            class ParameterRunnable implements Runnable {
                private ${type} param

                @Inject
                ParameterRunnable(${type} param) {
                    this.param = param
                }

                void run() {
                    ${action}
                }
            }
        """
    }

    String parameterWorkerExecution(String type, String action) {
        return """
            interface TestParameters extends WorkerParameters {
                ${type} getFoo();
                void setFoo(${type} foo);
            }
            
            abstract class ParameterWorkerExecution implements WorkerExecution<TestParameters> {
                void execute() {
                    ${action}
                }
            }
        """
    }
}

