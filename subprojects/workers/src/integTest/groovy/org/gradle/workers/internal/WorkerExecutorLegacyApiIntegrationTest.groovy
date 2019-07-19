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
import org.gradle.internal.jvm.Jvm
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath
import static org.gradle.util.TextUtil.normaliseFileSeparators
import static org.gradle.workers.fixtures.WorkerExecutorFixture.ISOLATION_MODES

class WorkerExecutorLegacyApiIntegrationTest extends AbstractIntegrationSpec {
    boolean isOracleJDK = TestPrecondition.JDK_ORACLE.fulfilled && (Jvm.current().jre != null)

    @Unroll
    def "can submit an item of work with the legacy API using isolation mode #isolationMode"() {
        buildFile << """
            ${legacyWorkerTypeAndTask}

            task runWork(type: WorkerTask) {
                text = "foo"
                arrayOfThings = ["foo", "bar", "baz"]
                listOfThings = ["foo", "bar", "baz"]

                isolationMode = ${isolationMode}
            }
        """

        when:
        succeeds("runWork")

        then:
        result.groupedOutput.task(":runWork").output.contains """
            text = foo
            array = [foo, bar, baz]
            list = [foo, bar, baz]
         """.stripIndent().trim()

        where:
        isolationMode << ISOLATION_MODES
    }

    @Unroll
    def "can control forking via forkMode with the legacy API using fork mode #isolationMode"() {
        executer.withWorkerDaemonsExpirationDisabled()

        buildFile << """
            ${legacyWorkerTypeAndTask}

            task runWork(type: WorkerTask) {
                def daemonCount = 0

                text = "foo"
                arrayOfThings = ["foo", "bar", "baz"]
                listOfThings = ["foo", "bar", "baz"]
                
                workerConfiguration = {
                    forkMode = ${forkMode}
                }

                doFirst {
                    daemonCount = services.get(org.gradle.workers.internal.WorkerDaemonClientsManager.class).allClients.size()
                }
                
                doLast {
                    assert services.get(org.gradle.workers.internal.WorkerDaemonClientsManager.class).allClients.size() ${operator} daemonCount
                }
            }
        """

        when:
        succeeds("runWork")

        then:
        result.groupedOutput.task(":runWork").output.contains """
            text = foo
            array = [foo, bar, baz]
            list = [foo, bar, baz]
         """.stripIndent().trim()

        where:
        forkMode          | operator
        "ForkMode.NEVER"  | "=="
        "ForkMode.ALWAYS" | ">"
    }



    @Unroll
    def "produces a sensible error when parameters are incorrect in #isolationMode"() {
        buildFile << """
            ${legacyWorkerTypeAndTask}
            ${runnableWithDifferentConstructor}

            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
                runnableClass = RunnableWithDifferentConstructor.class

                text = "foo"
                arrayOfThings = ["foo", "bar", "baz"]
                listOfThings = ["foo", "bar", "baz"]
            }
        """

        when:
        fails("runInWorker")

        then:
        failureHasCause("A failure occurred while executing RunnableWithDifferentConstructor")
        failureHasCause("Could not create an instance of type RunnableWithDifferentConstructor.")
        failureHasCause("Too many parameters provided for constructor for class RunnableWithDifferentConstructor. Expected 2, received 3.")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "interesting worker daemon fork options are honored"() {
        buildFile << """
            import org.gradle.internal.jvm.Jvm

            ${legacyWorkerTypeAndTask}
            ${optionVerifyingRunnable}

            task runInDaemon(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
                runnableClass = OptionVerifyingRunnable.class
                workerConfiguration = {
                    forkOptions { options ->
                        options.with {
                            minHeapSize = "128m"
                            maxHeapSize = "128m"
                            systemProperty("foo", "bar")
                            jvmArgs("-Dbar=baz")
                            if (${isOracleJDK}) {
                                bootstrapClasspath = fileTree(new File(Jvm.current().jre.homeDir, "lib")).include("*.jar")
                                bootstrapClasspath(new File("${normaliseFileSeparators(systemSpecificAbsolutePath('foo'))}"))
                            }
                            defaultCharacterEncoding = "UTF-8"
                            enableAssertions = true
                            environment "foo", "bar"
                        }
                    }
                }
                
                text = "foo"
                arrayOfThings = ["foo", "bar", "baz"]
                listOfThings = ["foo", "bar", "baz"]
            }
        """

        when:
        succeeds("runInDaemon")

        then:
        result.groupedOutput.task(":runInDaemon").output.contains """
            text = foo
            array = [foo, bar, baz]
            list = [foo, bar, baz]
         """.stripIndent().trim()
    }

    String getLegacyWorkerTypeAndTask() {
        return """
            import javax.inject.Inject

            class TestRunnable implements Runnable {
                final String text
                final String[] arrayOfThings
                final ListProperty<String> listOfThings
                
                @Inject
                TestRunnable(String text, String[] arrayOfThings, ListProperty<String> listOfThings) {
                    this.text = text
                    this.arrayOfThings = arrayOfThings
                    this.listOfThings = listOfThings
                }
                
                void run() {
                    println "text = " + text
                    println "array = " + arrayOfThings
                    println "list = " + listOfThings.get()
                }
            }
            
            class WorkerTask extends DefaultTask {
                WorkerExecutor workerExecutor
                String text
                String[] arrayOfThings
                ListProperty<String> listOfThings
                IsolationMode isolationMode = IsolationMode.AUTO
                Class<?> runnableClass = TestRunnable.class
                Closure workerConfiguration
                
                @Inject
                WorkerTask(WorkerExecutor workerExecutor) {
                    this.workerExecutor = workerExecutor
                    this.listOfThings = project.objects.listProperty(String)
                }
                
                @TaskAction
                void doWork() {
                    workerExecutor.submit(runnableClass) { config ->
                        config.isolationMode = this.isolationMode
                        config.params = [text, arrayOfThings, listOfThings]
                        if (workerConfiguration != null) {
                            workerConfiguration.delegate = config
                            workerConfiguration(config)
                        }
                    }
                }    
            }
        """
    }

    String getRunnableWithDifferentConstructor() {
        return """
            public class RunnableWithDifferentConstructor implements Runnable {
                @javax.inject.Inject
                public RunnableWithDifferentConstructor(List<String> files, File outputDir) { 
                }
                public void run() {
                }
            }
        """
    }

    String getOptionVerifyingRunnable() {
        return """
            import java.util.regex.Pattern;
            import java.util.List;
            import java.lang.management.ManagementFactory;
            import java.lang.management.RuntimeMXBean;
            import javax.inject.Inject;

            public class OptionVerifyingRunnable extends TestRunnable {
                @Inject
                public OptionVerifyingRunnable(String text, String[] arrayOfThings, ListProperty<String> listOfThings) {
                    super(text, arrayOfThings, listOfThings);
                }

                public void run() {
                    RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
                    List<String> arguments = runtimeMxBean.getInputArguments();
                    assert arguments.contains("-Dfoo=bar");
                    assert arguments.contains("-Dbar=baz");
                    assert arguments.contains("-Xmx128m");
                    assert arguments.contains("-Xms128m");
                    assert arguments.contains("-Dfile.encoding=UTF-8");
                    assert arguments.contains("-ea");

                    if (${isOracleJDK}) {
                        assert runtimeMxBean.getBootClassPath().replaceAll(Pattern.quote(File.separator),'/').endsWith("/foo");
                    }

                    assert System.getenv("foo").equals("bar")

                    super.run();
                }
            }
        """
    }
}
