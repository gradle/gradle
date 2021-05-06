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
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.util.TestPrecondition
import org.gradle.workers.fixtures.OptionsVerifier
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath
import static org.gradle.util.internal.TextUtil.normaliseFileSeparators

class WorkerExecutorLegacyApiIntegrationTest extends AbstractIntegrationSpec {
    static final ISOLATION_MODES = (org.gradle.workers.IsolationMode.values() - org.gradle.workers.IsolationMode.AUTO).collect { "IsolationMode.${it.toString()}" }
    static final OUTPUT_FILE_NAME = "output.txt"
    boolean isOracleJDK = TestPrecondition.JDK_ORACLE.fulfilled && (Jvm.current().jre != null)

    def setup() {
       executer.beforeExecute {
           expectDocumentedDeprecationWarning("The WorkerExecutor.submit() method has been deprecated. This is scheduled to be removed in Gradle 8.0. Please use the noIsolation(), classLoaderIsolation() or processIsolation() method instead. See https://docs.gradle.org/current/userguide/upgrading_version_5.html#method_workerexecutor_submit_is_deprecated for more details.")
       }
    }

    @Unroll
    def "can submit an item of work with the legacy API using isolation mode #isolationMode"() {
        buildFile << """
            ${legacyWorkerTypeAndTask}

            task runWork(type: WorkerTask) {
                text = "foo"
                arrayOfThings = ["foo", "bar", "baz"]
                listOfThings = ["foo", "bar", "baz"]
                outputFile = file("${OUTPUT_FILE_NAME}")

                isolationMode = ${isolationMode}
            }
        """

        when:
        succeeds("runWork")

        then:
        file(OUTPUT_FILE_NAME).readLines().containsAll(
                "text = foo",
                "array = [foo, bar, baz]",
                "list = [foo, bar, baz]"
        )

        where:
        isolationMode << ISOLATION_MODES
    }

    @Unroll
    def "can control forking via forkMode with the legacy API using fork mode #forkMode"() {
        executer.requireIsolatedDaemons()
        executer.withWorkerDaemonsExpirationDisabled()

        buildFile << """
            ${legacyWorkerTypeAndTask}

            task runWork(type: WorkerTask) {
                def daemonCount = 0

                text = "foo"
                arrayOfThings = ["foo", "bar", "baz"]
                listOfThings = ["foo", "bar", "baz"]
                outputFile = file("${OUTPUT_FILE_NAME}")

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
        file(OUTPUT_FILE_NAME).readLines().containsAll(
                "text = foo",
                "array = [foo, bar, baz]",
                "list = [foo, bar, baz]"
        )

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
                outputFile = file("${OUTPUT_FILE_NAME}")
            }
        """

        when:
        fails("runInWorker")

        then:
        failureHasCause("A failure occurred while executing RunnableWithDifferentConstructor")
        failureHasCause("Could not create an instance of type RunnableWithDifferentConstructor.")
        failureHasCause("Too many parameters provided for constructor for type RunnableWithDifferentConstructor. Expected 2, received 4.")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "interesting worker daemon fork options are honored"() {
        OptionsVerifier optionsVerifier = new OptionsVerifier(file('process.json'))
        optionsVerifier.with {
            minHeap("128m")
            maxHeap("128m")
            systemProperty("foo", "bar")
            jvmArgs("-Dbar=baz")
            defaultCharacterEncoding("UTF-8")
            enableAssertions()
            environmentVariable("foo", "bar")
            if (isOracleJDK) {
                bootstrapClasspath(normaliseFileSeparators(systemSpecificAbsolutePath('foo')))
            }
        }

        buildFile << """
            import org.gradle.internal.jvm.Jvm

            ${legacyWorkerTypeAndTask}
            ${getOptionVerifyingRunnable(optionsVerifier)}

            task runInDaemon(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
                runnableClass = OptionVerifyingRunnable.class
                workerConfiguration = {
                    forkOptions { options ->
                        options.with {
                            ${optionsVerifier.toDsl()}
                        }
                    }
                }

                text = "foo"
                arrayOfThings = ["foo", "bar", "baz"]
                listOfThings = ["foo", "bar", "baz"]
                outputFile = file("${OUTPUT_FILE_NAME}")
            }
        """

        when:
        succeeds("runInDaemon")

        then:
        optionsVerifier.verifyAllOptions()

        and:
        file(OUTPUT_FILE_NAME).readLines().containsAll(
                "text = foo",
                "array = [foo, bar, baz]",
                "list = [foo, bar, baz]"
        )
    }

    def "can set a custom display name for work items in #isolationMode"() {
        def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

        given:
        buildFile << """
            ${legacyWorkerTypeAndTask}

            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
                displayName = "Test Work"

                text = "foo"
                arrayOfThings = ["foo", "bar", "baz"]
                listOfThings = ["foo", "bar", "baz"]
                outputFile = file("${OUTPUT_FILE_NAME}")
            }
        """

        when:
        succeeds("runInWorker")

        then:
        def operation = buildOperations.only(ExecuteWorkItemBuildOperationType)
        operation.displayName == "Test Work"
        with (operation.details) {
            className == "TestRunnable"
            displayName == "Test Work"
        }

        where:
        isolationMode << ISOLATION_MODES
    }

    @Issue("https://github.com/gradle/gradle/issues/10411")
    def "does not leak project state across multiple builds"() {
        executer.withBuildJvmOpts('-Xms256m', '-Xmx512m').requireIsolatedDaemons().requireDaemon()

        buildFile << """
            ${legacyWorkerTypeAndTask}

            ext.memoryHog = new byte[1024*1024*150] // ~150MB

            tasks.withType(WorkerTask) { task ->
                isolationMode = IsolationMode.PROCESS
                displayName = "Test Work"

                text = "foo"
                arrayOfThings = ["foo", "bar", "baz"]
                listOfThings = ["foo", "bar", "baz"]
                outputFile = file("${OUTPUT_FILE_NAME}")

                // Force a new daemon to be used
                workerConfiguration = {
                    forkOptions { options ->
                        options.with {
                            systemProperty("foobar", task.name)
                        }
                    }
                }
            }
            task startDaemon1(type: WorkerTask)
            task startDaemon2(type: WorkerTask)
            task startDaemon3(type: WorkerTask)
        """

        expect:
        succeeds("startDaemon1")
        succeeds("startDaemon2")
        succeeds("startDaemon3")
    }

    @Issue("https://github.com/gradle/gradle/issues/10323")
    def "can use a Properties object as a parameter"() {
        buildFile << """
            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.TaskAction
            import org.gradle.workers.IsolationMode
            import org.gradle.workers.WorkerExecutor

            task myTask(type: MyTask) {
                description 'My Task'
                outputFile = file("\${buildDir}/workOutput")
            }

            class MyTask extends DefaultTask {
                private final WorkerExecutor workerExecutor

                @OutputFile
                File outputFile

                @Inject
                MyTask(WorkerExecutor workerExecutor) {
                    this.workerExecutor = workerExecutor
                }

                @TaskAction
                def run() {
                    Properties myProps = new Properties()
                    myProps.setProperty('key1', 'value1')
                    myProps.setProperty('key2', 'value2')
                    myProps.setProperty('key3', 'value3')

                    workerExecutor.submit(MyRunner.class) { config ->
                        config.isolationMode = IsolationMode.NONE

                        config.params(myProps, outputFile)
                    }

                    workerExecutor.await()
                }

                private static class MyRunner implements Runnable {
                    Properties myProps
                    File outputFile

                    @Inject
                    MyRunner(Properties myProps, File outputFile) {
                        this.myProps = myProps
                        this.outputFile = outputFile
                    }

                    @Override
                    void run() {
                        Properties myProps = this.myProps;
                        def writer = outputFile.newWriter()
                        try {
                            myProps.store(writer, null)
                        } finally {
                            writer.close()
                        }
                    }
                }
            }
        """

        expect:
        succeeds(":myTask")

        and:
        file("build/workOutput").text.readLines().containsAll([
                "key1=value1",
                "key2=value2",
                "key3=value3"
        ])
    }

    String getLegacyWorkerTypeAndTask() {
        return """
            class TestRunnable implements Runnable {
                final String text
                final String[] arrayOfThings
                final ListProperty<String> listOfThings
                final File outputFile

                @Inject
                TestRunnable(String text, String[] arrayOfThings, ListProperty<String> listOfThings, File outputFile) {
                    this.text = text
                    this.arrayOfThings = arrayOfThings
                    this.listOfThings = listOfThings
                    this.outputFile = outputFile
                }

                void run() {
                    outputFile.withWriter { writer ->
                        PrintWriter out = new PrintWriter(writer)
                        out.println "text = " + text
                        out.println "array = " + arrayOfThings
                        out.println "list = " + listOfThings.get()
                    }
                }
            }

            class WorkerTask extends DefaultTask {
                private final WorkerExecutor workerExecutor

                @Internal
                String text
                @Internal
                String[] arrayOfThings
                @Internal
                ListProperty<String> listOfThings
                @Internal
                File outputFile
                @Internal
                IsolationMode isolationMode = IsolationMode.AUTO
                @Internal
                Class<?> runnableClass = TestRunnable.class
                @Internal
                String displayName
                @Internal
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
                        config.displayName = this.displayName
                        config.params = [text, arrayOfThings, listOfThings, outputFile]
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

    String getOptionVerifyingRunnable(OptionsVerifier optionsVerifier) {
        return """
            import java.util.regex.Pattern;
            import java.util.List;
            import java.lang.management.ManagementFactory;
            import java.lang.management.RuntimeMXBean;

            public class OptionVerifyingRunnable extends TestRunnable {
                @Inject
                public OptionVerifyingRunnable(String text, String[] arrayOfThings, ListProperty<String> listOfThings, File outputFile) {
                    super(text, arrayOfThings, listOfThings, outputFile);
                }

                public void run() {
                    ${optionsVerifier.dumpProcessEnvironment(isOracleJDK)}

                    super.run();
                }
            }
        """
    }
}
