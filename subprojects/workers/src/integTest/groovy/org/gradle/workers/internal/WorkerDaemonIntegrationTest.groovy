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

package org.gradle.workers.internal

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.internal.jvm.Jvm
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.workers.fixtures.WorkerExecutorFixture
import org.junit.Assume

import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath
import static org.gradle.util.TextUtil.normaliseFileSeparators
import static org.hamcrest.CoreMatchers.notNullValue

@IntegrationTestTimeout(180)
class WorkerDaemonIntegrationTest extends AbstractWorkerExecutorIntegrationTest {
    WorkerExecutorFixture.ExecutionClass workerExecutionThatPrintsWorkingDirectory
    WorkerExecutorFixture.ExecutionClass workerExecutionThatVerifiesOptions

    def setup() {
        workerExecutionThatPrintsWorkingDirectory = fixture.getWorkerExecutionThatCreatesFiles("WorkingDirExecution")
        workerExecutionThatPrintsWorkingDirectory.with {
            constructorAction = """
                Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                    @Override
                    public void run() {
                        println "Shutdown working dir: " + System.getProperty("user.dir")
                    }
                }));
            """
            action += """
                println "Execution working dir: " + System.getProperty("user.dir")
            """
        }

        workerExecutionThatVerifiesOptions = fixture.getWorkerExecutionThatCreatesFiles("OptionVerifyingWorkerExecution")
        workerExecutionThatVerifiesOptions.with {
            imports += [
                    "java.util.regex.Pattern",
                    "java.lang.management.ManagementFactory",
                    "java.lang.management.RuntimeMXBean"
            ]
            action += """
                RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
                List<String> arguments = runtimeMxBean.getInputArguments();
                assert arguments.contains("-Dfoo=bar");
                assert arguments.contains("-Dbar=baz");
                assert arguments.contains("-Xmx128m");
                assert arguments.contains("-Xms128m");
                assert arguments.contains("-Dfile.encoding=UTF-8");
                assert arguments.contains("-ea");
    
                assert runtimeMxBean.getBootClassPath().replaceAll(Pattern.quote(File.separator),'/').endsWith("/foo");
    
                assert System.getenv("foo").equals("bar")
            """
        }
    }

    def "uses the worker home directory as working directory for worker execution"() {
        def workerHomeDir = executer.gradleUserHomeDir.file("workers").getAbsolutePath()
        fixture.withWorkerExecutionClassInBuildScript()
        workerExecutionThatPrintsWorkingDirectory.writeToBuildFile()
        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
                workerExecutionClass = ${workerExecutionThatPrintsWorkingDirectory.name}.class
            }
        """

        when:
        args("--info")
        def gradle = executer.withTasks("runInWorker").start()

        then:
        gradle.waitForFinish()

        and:
        gradle.standardOutput.readLines().find {
            normaliseFileSeparators(it).matches "Starting process 'Gradle Worker Daemon \\d+'. Working directory: " + normaliseFileSeparators(workerHomeDir) + ".*"
        }

        and:
        gradle.standardOutput.contains("Execution working dir: " + workerHomeDir)

        and:
        GradleContextualExecuter.isLongLivingProcess() || gradle.standardOutput.contains("Shutdown working dir: " + workerHomeDir)
    }

    def "setting the working directory of a worker is not supported"() {
        fixture.withWorkerExecutionClassInBuildScript()
        workerExecutionThatPrintsWorkingDirectory.writeToBuildFile()
        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
                workerExecutionClass = ${workerExecutionThatPrintsWorkingDirectory.name}.class
                additionalForkOptions = { it.workingDir = project.file("unsupported") }
            }
        """

        when:
        fails("runInWorker")

        then:
        failureCauseContains('setting the working directory of a worker is not supported')
    }

    @Requires(TestPrecondition.JDK_ORACLE)
    def "interesting worker daemon fork options are honored"() {
        Assume.assumeThat(Jvm.current().jre, notNullValue())
        fixture.withWorkerExecutionClassInBuildSrc()
        workerExecutionThatVerifiesOptions.writeToBuildFile()
        outputFileDir.createDir()
        buildFile << """
            import org.gradle.internal.jvm.Jvm

            task runInDaemon(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
                runnableClass = ${workerExecutionThatVerifiesOptions}.class
                additionalForkOptions = { options ->
                    options.with {
                        minHeapSize = "128m"
                        maxHeapSize = "128m"
                        systemProperty("foo", "bar")
                        jvmArgs("-Dbar=baz")
                        bootstrapClasspath = fileTree(new File(Jvm.current().jre.homeDir, "lib")).include("*.jar")
                        bootstrapClasspath(new File("${normaliseFileSeparators(systemSpecificAbsolutePath('foo'))}"))
                        defaultCharacterEncoding = "UTF-8"
                        enableAssertions = true
                        environment "foo", "bar"
                    }
                }
            }
        """

        when:
        succeeds("runInDaemon")

        then:
        assertWorkerExecuted("runInDaemon")
    }

    def "worker daemons honor different executable specified in fork options"() {
        def differentJvm = AvailableJavaHomes.differentJdkWithValidJre
        Assume.assumeNotNull(differentJvm)
        def differentJavacExecutablePath = normaliseFileSeparators(differentJvm.getJavaExecutable().absolutePath)
        def workerExecution = getWorkerExecutionThatVerifiesExecutable(differentJvm)

        fixture.withJava7CompatibleClasses()
        fixture.withWorkerExecutionClassInBuildSrc()
        workerExecution.writeToBuildFile()

        buildFile << """
            task runInDaemon(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
                workerExecutionClass = ${workerExecution.name}.class
                additionalForkOptions = { options ->
                    options.executable = new File('${differentJavacExecutablePath}')
                }
            }
        """

        when:
        succeeds("runInDaemon")

        then:
        assertWorkerExecuted("runInDaemon")
    }

    WorkerExecutorFixture.ExecutionClass getWorkerExecutionThatVerifiesExecutable(Jvm differentJvm) {
        def workerClass = fixture.getWorkerExecutionThatCreatesFiles("ExecutableVerifyingWorkerExecution")
        workerClass.imports.add("java.net.URL")
        workerClass.action += """
            assert new File('${normaliseFileSeparators(differentJvm.jre.homeDir.absolutePath)}').canonicalPath.equals(new File(System.getProperty("java.home")).canonicalPath);
        """
        return workerClass
    }
}
