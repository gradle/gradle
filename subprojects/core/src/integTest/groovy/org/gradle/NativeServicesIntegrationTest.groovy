/*
 * Copyright 2014 the original author or authors.
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

package org.gradle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.internal.nativeintegration.jansi.JansiStorageLocator
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.internal.ToBeImplemented
import org.junit.Rule
import spock.lang.Issue

import static org.gradle.internal.nativeintegration.services.NativeServices.NATIVE_DIR_OVERRIDE
import static org.gradle.internal.nativeintegration.services.NativeServices.NATIVE_SERVICES_OPTION
import static org.gradle.util.internal.TextUtil.normaliseFileSeparators

@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "needs to run a distribution from scratch to not have native services on the classpath already")
class NativeServicesIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def nativeDir = new File(executer.gradleUserHomeDir, 'native')
    def library

    def setup() {
        def jansiLibraryLocator = new JansiStorageLocator()
        def jansiStorage = jansiLibraryLocator.locate(nativeDir)
        library = jansiStorage.targetLibFile
    }

    def "native services libs are unpacked to gradle user home dir"() {
        given:
        executer.withArguments('-q')

        when:
        succeeds("help")

        then:
        nativeDir.directory
    }

    @ToBeImplemented("https://github.com/gradle/gradle/issues/28203")
    def "native services are #description with systemProperties == #systemProperties"() {
        given:
        // We set Gradle User Home to a different temporary directory that is outside
        // a project dir to avoid file lock issues on Windows due to native services being loaded
        executer.withGradleUserHomeDir(tmpDir.testDirectory).withNoExplicitNativeServicesDir()
        nativeDir = new File(executer.gradleUserHomeDir, 'native')
        executer.withArguments(systemProperties.collect { it.toString() })
        buildFile << """
            import org.gradle.workers.WorkParameters

            tasks.register("doWork", WorkerTask)

            abstract class WorkerTask extends DefaultTask {
                @Inject
                abstract WorkerExecutor getWorkerExecutor()

                @TaskAction
                void executeTask() {
                    workerExecutor.processIsolation().submit(NoOpWorkAction) { }
                }
            }

            abstract class NoOpWorkAction implements WorkAction<WorkParameters.None> {
                public void execute() {}
            }
        """

        when:
        succeeds("doWork")

        then:
        nativeDir.exists() == initialized

        where:
        // Works for all cases except -D$NATIVE_SERVICES_OPTION=false
        description       | systemProperties                    | initialized
        "initialized"     | ["-D$NATIVE_SERVICES_OPTION=true"]  | true
        "not initialized" | ["-D$NATIVE_SERVICES_OPTION=false"] | true // Should be false
        "initialized"     | ["-D$NATIVE_SERVICES_OPTION=''"]    | true
        "initialized"     | []                                  | true
    }

    @ToBeImplemented("https://github.com/gradle/gradle/issues/28203")
    def "native services flag should be passed to the daemon and to the worker"() {
        given:
        executer.withArguments(systemProperties.collect { it.toString() })
        buildScript("""
            import org.gradle.workers.WorkParameters
            import org.gradle.internal.nativeintegration.services.NativeServices
            import org.gradle.internal.nativeintegration.NativeCapabilities

            tasks.register("doWork", WorkerTask)
            println("Uses native integration in daemon: " + NativeServices.instance.createNativeCapabilities().useNativeIntegrations())

            abstract class WorkerTask extends DefaultTask {
                @Inject
                abstract WorkerExecutor getWorkerExecutor()

                @TaskAction
                void executeTask() {
                    workerExecutor.processIsolation().submit(NoOpWorkAction) { }
                }
            }

            abstract class NoOpWorkAction implements WorkAction<WorkParameters.None> {
                void execute() {
                    println("Uses native integration in worker: " + NativeServices.instance.createNativeCapabilities().useNativeIntegrations())
                }
            }
        """)

        when:
        succeeds("doWork")

        then:
        outputContains("Uses native integration in daemon: $usesNativeIntegration")
        outputContains("Uses native integration in worker: $usesNativeIntegration")

        where:
        // Works for all cases except -D$NATIVE_SERVICES_OPTION=false
        systemProperties                    | usesNativeIntegration
        ["-D$NATIVE_SERVICES_OPTION=true"]  | true
        ["-D$NATIVE_SERVICES_OPTION=false"] | true // Should be false
        ["-D$NATIVE_SERVICES_OPTION=''"]    | true
        []                                  | true
    }

    @Issue("https://github.com/gradle/gradle/issues/28401")
    def "native services are not initialized inside a test executor but should be initialized for a build inside the executor"() {
        given:
        def nativeDirOverride = normaliseFileSeparators(new File(tmpDir.testDirectory, 'native-libs-for-test-executor').absolutePath)
        buildScript("""
            plugins {
                id("java-gradle-plugin")
                id("groovy")
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    functionalTest(JvmTestSuite) {
                        useSpock("2.2-groovy-3.0")
                        dependencies {
                            implementation(project())
                        }
                    }
                }
            }

            tasks.named("functionalTest", Test) {
                // Override native libs dir for the test executor
                systemProperty("$NATIVE_DIR_OVERRIDE", "${nativeDirOverride}")
            }

            gradlePlugin.testSourceSets.add(sourceSets["functionalTest"])
        """)
        file("src/functionalTest/groovy/TestkitTestPluginFunctionalTest.groovy") << """
            import spock.lang.Specification
            import spock.lang.TempDir
            import org.gradle.testkit.runner.GradleRunner
            import org.gradle.testkit.runner.TaskOutcome

            class TestkitTestPluginFunctionalTest extends Specification {
                @TempDir
                private File projectDir

                private getBuildFile() {
                    new File(projectDir, "build.gradle")
                }

                private getSettingsFile() {
                    new File(projectDir, "settings.gradle")
                }

                def "native services are enabled"() {
                    given:
                    // We check if native dir was created before running a build, which would
                    // mean that native services were initialized by a test executor
                    println("Test executor initialized Native services: " + new File("${nativeDirOverride}").exists())
                    settingsFile << ""
                    buildFile << \"""
                        println("Build inside a test executor initialized Native services: " + new File("${nativeDirOverride}").exists())
                        println("Build inside a test executor uses Native services: " +
                            org.gradle.internal.nativeintegration.services.NativeServices.instance.createNativeCapabilities().useNativeIntegrations())
                    \"""

                    when:
                    def result = GradleRunner.create()
                        .forwardOutput()
                        .withPluginClasspath()
                        .withArguments("help")
                        .withProjectDir(projectDir)
                        .build()

                    then:
                    result.task(":help").outcome == TaskOutcome.SUCCESS
                }
            }
        """

        when:
        succeeds("functionalTest", "--info")

        then:
        outputContains("Test executor initialized Native services: false")
        outputContains("Build inside a test executor initialized Native services: true")
        outputContains("Build inside a test executor uses Native services: true")
    }

    def "daemon with different native services flag is not reused"() {
        given:
        executer.requireDaemon()
        executer.requireIsolatedDaemons()

        when:
        executer.withArguments("-D$NATIVE_SERVICES_OPTION=$firstRunNativeServicesOption")
        succeeds()

        then:
        daemons.daemon.becomesIdle()

        when:
        executer.withArguments("-D$NATIVE_SERVICES_OPTION=$secondRunNativeServicesOption")
        succeeds()

        then:
        daemons.daemons.size() == expectedDaemonCount

        where:
        firstRunNativeServicesOption | secondRunNativeServicesOption | expectedDaemonCount | reuseDescription
        true                         | true                          | 1                   | "reused"
        false                        | false                         | 1                   | "reused"
        true                         | false                         | 2                   | "not reused"
        false                        | true                          | 2                   | "not reused"
    }

    @Issue("GRADLE-3573")
    def "jansi library is unpacked to gradle user home dir and isn't overwritten if existing"() {
        String tmpDirJvmOpt = "-Djava.io.tmpdir=$tmpDir.testDirectory.absolutePath"
        executer.withBuildJvmOpts(tmpDirJvmOpt)

        when:
        succeeds("help")

        then:
        library.exists()
        assertNoFilesInTmp()
        long lastModified = library.lastModified()

        when:
        succeeds("help")

        then:
        library.exists()
        assertNoFilesInTmp()
        lastModified == library.lastModified()
    }

    private void assertNoFilesInTmp() {
        assert tmpDir.testDirectory.listFiles().length == 0
    }

    private DaemonLogsAnalyzer getDaemons() {
        new DaemonLogsAnalyzer(executer.daemonBaseDir)
    }
}
