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
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.TestPrecondition
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.workers.fixtures.OptionsVerifier
import org.gradle.workers.fixtures.WorkerExecutorFixture
import org.junit.Assume

import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath
import static org.gradle.util.internal.TextUtil.normaliseFileSeparators

@IntegrationTestTimeout(180)
class WorkerDaemonIntegrationTest extends AbstractWorkerExecutorIntegrationTest implements JavaToolchainFixture {
    boolean isOracleJDK = TestPrecondition.satisfied(UnitTestPreconditions.JdkOracle) && (Jvm.current().jre != null)

    WorkerExecutorFixture.WorkActionClass workActionThatPrintsWorkingDirectory

    def setup() {
        workActionThatPrintsWorkingDirectory = fixture.getWorkActionThatCreatesFiles("WorkingDirAction")
        workActionThatPrintsWorkingDirectory.with {
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
    }

    def "uses the worker home directory as working directory for work action"() {
        def workerHomeDir = executer.gradleUserHomeDir.file("workers").getAbsolutePath()
        fixture.withWorkActionClassInBuildScript()
        workActionThatPrintsWorkingDirectory.writeToBuildFile()
        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = 'processIsolation'
                workActionClass = ${workActionThatPrintsWorkingDirectory.name}.class
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
        fixture.withWorkActionClassInBuildScript()
        workActionThatPrintsWorkingDirectory.writeToBuildFile()
        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = 'processIsolation'
                workActionClass = ${workActionThatPrintsWorkingDirectory.name}.class
                def workDirPath = project.file("unsupported")
                additionalForkOptions = { it.workingDir = workDirPath }
            }
        """

        when:
        fails("runInWorker")

        then:
        failureCauseContains('Setting the working directory of a worker is not supported')
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

        fixture.withWorkActionClassInBuildSrc()
        def workActionThatVerifiesOptions = getWorkActionThatVerifiesOptions(optionsVerifier).writeToBuildFile()
        outputFileDir.createDir()

        buildFile << """
            import org.gradle.internal.jvm.Jvm

            task runInDaemon(type: WorkerTask) {
                isolationMode = 'processIsolation'
                workActionClass = ${workActionThatVerifiesOptions.name}.class
                def fileTree = (${isOracleJDK}) ? project.fileTree(new File(Jvm.current().jre, "lib")).include("*.jar") : null
                additionalForkOptions = { options ->
                    options.with {
                        ${optionsVerifier.toDsl()}
                    }
                }
            }
        """

        when:
        succeeds("runInDaemon")

        then:
        optionsVerifier.verifyAllOptions()

        and:
        assertWorkerExecuted("runInDaemon")
    }

    def "worker daemons honor different executable specified in fork options"() {
        def differentJvm = AvailableJavaHomes.differentJdkWithValidJre
        Assume.assumeNotNull(differentJvm)
        def differentJavacExecutablePath = normaliseFileSeparators(differentJvm.getJavaExecutable().absolutePath)
        def workAction = getWorkActionThatVerifiesExecutable(differentJvm)

        fixture.withJava7CompatibleClasses()
        fixture.withWorkActionClassInBuildSrc()
        workAction.writeToBuildFile()

        buildFile << """
            task runInDaemon(type: WorkerTask) {
                isolationMode = 'processIsolation'
                workActionClass = ${workAction.name}.class
                additionalForkOptions = { options ->
                    options.executable = new File('${differentJavacExecutablePath}')
                }
            }
        """

        when:
        withInstallations(AvailableJavaHomes.jdk11).succeeds("runInDaemon")

        then:
        assertWorkerExecuted("runInDaemon")
    }

    def "worker application classpath is isolated from the worker process classloader"() {
        fixture.workActionThatCreatesFiles.action += """
            try {
                ${fixture.workActionThatCreatesFiles.name}.class.getClassLoader().loadClass("org.gradle.api.Project");
            } catch (ClassNotFoundException e) {
                return;
            }
            assert false : "org.gradle.api.Project leaked onto the application classpath";
        """
        fixture.withWorkActionClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: WorkerTask) {
                isolationMode = 'processIsolation'
                workActionClass = ${fixture.workActionThatCreatesFiles.name}.class
            }
        """

        when:
        succeeds("runInDaemon")

        then:
        assertWorkerExecuted("runInDaemon")
    }

    WorkerExecutorFixture.WorkActionClass getWorkActionThatVerifiesExecutable(Jvm differentJvm) {
        def workerClass = fixture.getWorkActionThatCreatesFiles("ExecutableVerifyingWorkAction")
        workerClass.imports.add("java.net.URL")
        workerClass.action += """
            assert new File('${normaliseFileSeparators(differentJvm.jre.absolutePath)}').canonicalPath.equals(new File(System.getProperty("java.home")).canonicalPath);
        """
        return workerClass
    }

    WorkerExecutorFixture.WorkActionClass getWorkActionThatVerifiesOptions(OptionsVerifier optionsVerifier) {
        def workActionThatVerifiesOptions = fixture.getWorkActionThatCreatesFiles("OptionVerifyingWorkAction")
        workActionThatVerifiesOptions.with {
            imports += [
                    "java.util.regex.Pattern",
                    "java.lang.management.ManagementFactory",
                    "java.lang.management.RuntimeMXBean"
            ]
            action += """
                ${optionsVerifier.dumpProcessEnvironment(isOracleJDK)}
            """
        }
        return workActionThatVerifiesOptions
    }
}
