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
import org.junit.Assume

import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath
import static org.gradle.util.TextUtil.normaliseFileSeparators
import static org.hamcrest.CoreMatchers.notNullValue

@IntegrationTestTimeout(180)
class WorkerDaemonIntegrationTest extends AbstractWorkerExecutorIntegrationTest {

    def "uses the worker home directory as working directory for worker execution"() {
        def workerHomeDir = executer.gradleUserHomeDir.file("workers").getAbsolutePath()
        fixture.withRunnableClassInBuildScript()
        buildFile << """
            import org.gradle.workers.IsolationMode

            $runnableThatPrintsWorkingDirectory

            task runInWorker(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
                runnableClass = WorkingDirRunnable.class
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
        fixture.withRunnableClassInBuildScript()
        buildFile << """
            import org.gradle.workers.IsolationMode

            $runnableThatPrintsWorkingDirectory

            task runInWorker(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
                runnableClass = WorkingDirRunnable.class
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
        fixture.withRunnableClassInBuildSrc()
        outputFileDir.createDir()

        buildFile << """
            import org.gradle.internal.jvm.Jvm

            $optionVerifyingRunnable

            task runInDaemon(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
                runnableClass = OptionVerifyingRunnable.class
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
        assertRunnableExecuted("runInDaemon")
    }

    def "worker daemons honor different executable specified in fork options"() {
        def differentJvm = AvailableJavaHomes.differentJdkWithValidJre
        Assume.assumeNotNull(differentJvm)
        def differentJavacExecutablePath = normaliseFileSeparators(differentJvm.getJavaExecutable().absolutePath)

        fixture.withJava7CompatibleClasses()
        fixture.withRunnableClassInBuildSrc()

        buildFile << """
            ${getExecutableVerifyingRunnable(differentJvm)}

            task runInDaemon(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
                runnableClass = ExecutableVerifyingRunnable.class
                additionalForkOptions = { options ->
                    options.executable = new File('${differentJavacExecutablePath}')
                }
            }
        """

        when:
        succeeds("runInDaemon")

        then:
        assertRunnableExecuted("runInDaemon")
    }

    def getRunnableThatPrintsWorkingDirectory() {
        return """
            class WorkingDirRunnable extends TestRunnable {
                @Inject
                public WorkingDirRunnable(List<String> files, File outputDir, Foo foo) {
                    super(files, outputDir, foo);
                    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                        @Override
                        public void run() {
                            printWorkingDirectory("Shutdown")
                        }
                    }));
                }
                
                public void run() {
                    super.run()
                    printWorkingDirectory("Execution")
                }
                
                void printWorkingDirectory(String phase) {
                    println phase + " working dir: " + System.getProperty("user.dir")
                }
            }
        """
    }

    String getOptionVerifyingRunnable() {
        return """
            import java.io.File;
            import java.util.regex.Pattern;
            import java.util.List;
            import org.gradle.other.Foo;
            import java.lang.management.ManagementFactory;
            import java.lang.management.RuntimeMXBean;
            import javax.inject.Inject;

            public class OptionVerifyingRunnable extends TestRunnable {
                @Inject
                public OptionVerifyingRunnable(List<String> files, File outputDir, Foo foo) {
                    super(files, outputDir, foo);
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

                    assert runtimeMxBean.getBootClassPath().replaceAll(Pattern.quote(File.separator),'/').endsWith("/foo");

                    assert System.getenv("foo").equals("bar")

                    super.run();
                }
            }
        """
    }

    String getExecutableVerifyingRunnable(Jvm differentJvm) {
        return """
            import java.io.File;
            import java.util.List;
            import org.gradle.other.Foo;
            import java.net.URL;

            public class ExecutableVerifyingRunnable extends TestRunnable {
                @Inject
                public ExecutableVerifyingRunnable(List<String> files, File outputDir, Foo foo) {
                    super(files, outputDir, foo);
                }

                public void run() {
                    assert new File('${normaliseFileSeparators(differentJvm.jre.homeDir.absolutePath)}').canonicalPath.equals(new File(System.getProperty("java.home")).canonicalPath);

                    super.run();
                }
            }
        """
    }
}
