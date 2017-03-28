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

import groovy.transform.NotYetImplemented
import org.gradle.api.JavaVersion
import org.gradle.api.specs.Spec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.jvm.JvmInstallation
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TextUtil
import org.junit.Assume
import org.junit.Rule
import spock.lang.Unroll

import static org.hamcrest.CoreMatchers.*

class WorkerExecutorIntegrationTest extends AbstractWorkerExecutorIntegrationTest {
    private final String fooPath = TextUtil.normaliseFileSeparators(file('foo').absolutePath)

    @Rule
    public final BlockingHttpServer blockingServer = new BlockingHttpServer()
    @Rule
    public final BuildOperationsFixture buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    @Unroll
    def "can create and use a worker runnable defined in buildSrc in #forkMode"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                forkMode = $forkMode
            }
        """

        when:
        succeeds("runInWorker")

        then:
        assertRunnableExecuted("runInWorker")

        where:
        forkMode << ['ForkMode.ALWAYS', 'ForkMode.NEVER']
    }

    @Unroll
    def "can create and use a worker runnable defined in build script in #forkMode"() {
        withRunnableClassInBuildScript()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                forkMode = $forkMode
            }
        """

        when:
        succeeds("runInWorker")

        then:
        assertRunnableExecuted("runInWorker")

        where:
        forkMode << ['ForkMode.ALWAYS', 'ForkMode.NEVER']
    }

    @Unroll
    def "can create and use a worker runnable defined in an external jar in #forkMode"() {
        def runnableJarName = "runnable.jar"
        withRunnableClassInExternalJar(file(runnableJarName))

        buildFile << """
            buildscript {
                dependencies {
                    classpath files("$runnableJarName")
                }
            }

            task runInWorker(type: WorkerTask) {
                forkMode = $forkMode
            }
        """

        when:
        succeeds("runInWorker")

        then:
        assertRunnableExecuted("runInWorker")

        where:
        forkMode << ['ForkMode.ALWAYS', 'ForkMode.NEVER']
    }

    def "re-uses an existing idle worker daemon"() {
        executer.withWorkerDaemonsExpirationDisabled()
        withRunnableClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: WorkerTask) {
                forkMode = ForkMode.ALWAYS
            }

            task reuseDaemon(type: WorkerTask) {
                forkMode = ForkMode.ALWAYS
                dependsOn runInDaemon
            }
        """

        when:
        succeeds("reuseDaemon")

        then:
        assertSameDaemonWasUsed("runInDaemon", "reuseDaemon")
    }

    def "starts a new worker daemon when existing worker daemons are incompatible"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: WorkerTask)

            task startNewDaemon(type: WorkerTask) {
                dependsOn runInDaemon
                forkMode = ForkMode.ALWAYS

                // Force a new daemon to be used
                additionalForkOptions = {
                    it.systemProperty("foo", "bar")
                }
            }
        """

        when:
        succeeds("startNewDaemon")

        then:
        assertDifferentDaemonsWereUsed("runInDaemon", "startNewDaemon")
    }

    def "starts a new worker daemon when there are no idle compatible worker daemons available"() {
        blockingServer.start()
        blockingServer.expectConcurrentExecution("runInDaemon", "startNewDaemon")

        withRunnableClassInBuildSrc()
        withBlockingRunnableClassInBuildSrc("http://localhost:${blockingServer.port}")

        buildFile << """
            task runInDaemon(type: WorkerTask) {
                forkMode = ForkMode.ALWAYS
                runnableClass = BlockingRunnable.class
            }

            task startNewDaemon(type: WorkerTask) {
                forkMode = ForkMode.ALWAYS
                runnableClass = BlockingRunnable.class
            }

            task runAllDaemons {
                dependsOn runInDaemon, startNewDaemon
            }
        """

        when:
        args("--parallel")
        succeeds("runAllDaemons")

        then:
        assertDifferentDaemonsWereUsed("runInDaemon", "startNewDaemon")
    }

    def "re-uses an existing compatible worker daemon when a different runnable is executed"() {
        executer.withWorkerDaemonsExpirationDisabled()
        withRunnableClassInBuildSrc()
        withAlternateRunnableClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: WorkerTask) {
                forkMode = ForkMode.ALWAYS
            }

            task reuseDaemon(type: WorkerTask) {
                forkMode = ForkMode.ALWAYS
                runnableClass = AlternateRunnable.class
                dependsOn runInDaemon
            }
        """

        when:
        succeeds("reuseDaemon")

        then:
        assertSameDaemonWasUsed("runInDaemon", "reuseDaemon")
    }

    @Unroll
    def "throws if worker used from a thread with no current build operation in #forkMode"() {
        given:
        withRunnableClassInBuildSrc()

        and:
        buildFile << """
            class WorkerTaskUsingCustomThreads extends WorkerTask {
                @TaskAction
                void executeTask() {
                    def thrown = null
                    def customThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                workerExecutor.submit(runnableClass) { config ->
                                    config.forkMode = $forkMode
                                    config.forkOptions(additionalForkOptions)
                                    config.classpath(additionalClasspath)
                                    config.params = [ list.collect { it as String }, new File(outputFileDirPath), foo ]
                                }.get()
                            } catch(Exception ex) {
                                thrown = ex
                            }
                        }
                    })
                    customThread.start()
                    customThread.join()
                    if(thrown) {
                        throw thrown
                    }
                }
            }

            task runInWorker(type: WorkerTaskUsingCustomThreads)
        """.stripIndent()

        when:
        fails 'runInWorker'

        then:
        failure.assertHasCause 'No worker lease associated with the current thread'

        where:
        forkMode << ['ForkMode.ALWAYS', 'ForkMode.NEVER']
    }

    @Requires(TestPrecondition.JDK_ORACLE)
    def "interesting worker daemon fork options are honored"() {
        Assume.assumeThat(Jvm.current().jre, notNullValue())
        withRunnableClassInBuildSrc()
        outputFileDir.createDir()

        buildFile << """
            import org.gradle.internal.jvm.Jvm

            $optionVerifyingRunnable

            task runInDaemon(type: WorkerTask) {
                forkMode = ForkMode.ALWAYS
                runnableClass = OptionVerifyingRunnable.class
                additionalForkOptions = { options ->
                    options.with {
                        minHeapSize = "128m"
                        maxHeapSize = "128m"
                        systemProperty("foo", "bar")
                        jvmArgs("-Dbar=baz")
                        bootstrapClasspath = fileTree(new File(Jvm.current().jre.homeDir, "lib")).include("*.jar")
                        bootstrapClasspath(new File('${fooPath}'))
                        defaultCharacterEncoding = "UTF-8"
                        enableAssertions = true
                        workingDir = file('${outputFileDirPath}')
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

    @NotYetImplemented
    def "worker daemons honor different executable specified in fork options"() {
        def differentJvm = findAnotherJvm()
        Assume.assumeNotNull(differentJvm)
        def differentJavaExecutablePath = TextUtil.normaliseFileSeparators(differentJvm.getExecutable("java").absolutePath)

        withRunnableClassInBuildSrc()

        buildFile << """
            ${getExecutableVerifyingRunnable(differentJvm.javaHome)}

            task runInDaemon(type: WorkerTask) {
                forkMode = ForkMode.ALWAYS
                runnableClass = ExecutableVerifyingRunnable.class
                additionalForkOptions = { options ->
                    options.executable = new File('${differentJavaExecutablePath}')
                }
            }
        """

        when:
        succeeds("runInDaemon")

        then:
        assertRunnableExecuted("runInDaemon")
    }

    @Unroll
    def "can set a custom display name for work items in #forkMode"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                forkMode = $forkMode
                displayName = "Test Work"
            }
        """.stripIndent()

        when:
        succeeds("runInWorker")

        then:
        buildOperations.hasOperation("Test Work")

        where:
        forkMode << ['ForkMode.ALWAYS', 'ForkMode.NEVER']
    }

    def "can use a parameter that references classes in other packages in #forkMode"() {
        withRunnableClassInBuildSrc()
        withParameterClassReferencingClassInAnotherPackage()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                forkMode = $forkMode
            }
        """

        expect:
        succeeds("runInWorker")

        where:
        forkMode << ['ForkMode.ALWAYS', 'ForkMode.NEVER']
    }

    void withParameterClassReferencingClassInAnotherPackage() {
        file("buildSrc/src/main/java/org/gradle/another/Bar.java").text = """
            package org.gradle.another;
            
            import java.io.Serializable;
            
            public class Bar implements Serializable { }
        """

        file("buildSrc/src/main/java/org/gradle/other/Foo.java").text = """
            package org.gradle.other;

            import java.io.Serializable;
            import org.gradle.another.Bar;

            public class Foo implements Serializable { 
                Bar bar = new Bar();
            }
        """
    }

    String getBlockingRunnableThatCreatesFiles(String url) {
        return """
            import java.io.File;
            import java.util.List;
            import org.gradle.other.Foo;
            import java.net.URL;
            import javax.inject.Inject;

            public class BlockingRunnable extends TestRunnable {
                @Inject
                public BlockingRunnable(List<String> files, File outputDir, Foo foo) {
                    super(files, outputDir, foo);
                }

                public void run() {
                    super.run();
                    try {
                        new URL("$url/" + outputDir.getName()).openConnection().getHeaderField("RESPONSE");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        """
    }

    String getAlternateRunnable() {
        return """
            import java.io.File;
            import java.util.List;
            import org.gradle.other.Foo;
            import java.net.URL;
            import javax.inject.Inject;

            public class AlternateRunnable extends TestRunnable {
                @Inject
                public AlternateRunnable(List<String> files, File outputDir, Foo foo) {
                    super(files, outputDir, foo);
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

                    assert runtimeMxBean.getBootClassPath().replaceAll(Pattern.quote(File.separator),'/').endsWith("${fooPath}");

                    assert new File(System.getProperty("user.dir")).equals(new File('${outputFileDirPath}'));

                    //NotYetImplemented
                    //assert System.getenv("foo").equals("bar")

                    super.run();
                }
            }
        """
    }

    String getExecutableVerifyingRunnable(File differentJvmHome) {
        return """
            import java.io.File;
            import java.util.List;
            import org.gradle.other.Foo;
            import java.net.URL;

            public class ExecutableVerifyingRunnable extends TestRunnable {
                public ExecutableVerifyingRunnable(List<String> files, File outputDir, Foo foo) {
                    super(files, outputDir, foo);
                }

                public void run() {
                    assert new File(System.getProperty("java.home")).equals(new File('${differentJvmHome.absolutePath}'));

                    super.run();
                }
            }
        """
    }

    void withBlockingRunnableClassInBuildSrc(String url) {
        file("buildSrc/src/main/java/org/gradle/test/BlockingRunnable.java") << """
            package org.gradle.test;

            ${getBlockingRunnableThatCreatesFiles(url)}
        """

        addImportToBuildScript("org.gradle.test.BlockingRunnable")
    }

    void withAlternateRunnableClassInBuildSrc() {
        file("buildSrc/src/main/java/org/gradle/test/AlternateRunnable.java") << """
            package org.gradle.test;

            $alternateRunnable
        """

        addImportToBuildScript("org.gradle.test.AlternateRunnable")
    }

    Jvm findAnotherJvm() {
        def current = Jvm.current()
        AvailableJavaHomes.getAvailableJdk(new Spec<JvmInstallation>() {
            @Override
            boolean isSatisfiedBy(JvmInstallation jvm) {
                return jvm.javaHome != current.javaHome && jvm.javaVersion >= JavaVersion.VERSION_1_7
            }
        })
    }
}
