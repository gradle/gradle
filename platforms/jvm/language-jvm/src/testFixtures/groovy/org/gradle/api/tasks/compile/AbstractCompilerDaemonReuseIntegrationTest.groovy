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

package org.gradle.api.tasks.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Assume

abstract class AbstractCompilerDaemonReuseIntegrationTest extends AbstractIntegrationSpec {
    def compilerDaemonIdentityFileName = "build/compilerId"
    def compilerDaemonIdentityFile = file(compilerDaemonIdentityFileName)

    abstract String getCompileTaskType()

    abstract String getApplyAndConfigure()

    abstract TestJvmComponent getComponent()

    def setup() {
        executer.withWorkerDaemonsExpirationDisabled()
        executer.requireDaemon().requireIsolatedDaemons()
        buildFile << """
            import org.gradle.workers.internal.WorkerDaemonClientsManager
            import org.gradle.workers.internal.DaemonForkOptions

            allprojects {
                ${applyAndConfigure}

                tasks.withType(${compileTaskType}) {
                    options.fork = true
                    finalizedBy ":writeCompilerIdentities"
                }
            }

            task writeCompilerIdentities {
                def compilerDaemonIdentityFile = file("$compilerDaemonIdentityFileName")
                doLast { task ->
                    compilerDaemonIdentityFile.text = services.get(WorkerDaemonClientsManager).allClients.collect { System.identityHashCode(it) }.sort().join(" ") + "\\n"
                }
            }

            task compileAll {
                dependsOn allprojects.collect { it.tasks.withType(${compileTaskType}) }
            }
        """
    }

    @Requires(IntegTestPreconditions.NotParallelExecutor)
    @UnsupportedWithConfigurationCache(because = "parallel by default")
    def "reuses compiler daemons within a single project build"() {
        withSingleProjectSources()

        when:
        succeeds("compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", "${compileTaskPath('main2')}"

        and:
        assertOneCompilerDaemonIsRunning()
    }

    @Requires(IntegTestPreconditions.NotParallelExecutor)
    @UnsupportedWithConfigurationCache(because = "parallel by default")
    def "reuses compiler daemons within a multi-project build"() {
        withMultiProjectSources()

        when:
        succeeds("compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", ":child${compileTaskPath('main')}"

        and:
        assertOneCompilerDaemonIsRunning()
    }

    @Requires(IntegTestPreconditions.NotParallelExecutor)
    def "reuses compiler daemons within a composite build"() {
        Assume.assumeTrue(supportsCompositeBuilds())

        withCompositeBuildSources()

        when:
        succeeds("compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", ":child${compileTaskPath('main')}"

        and:
        assertOneCompilerDaemonIsRunning()
    }

    @Requires(IntegTestPreconditions.NotParallelExecutor)
    def "starts a new daemon when different options are used"() {
        withMultiProjectSources()
        buildFile << """
            project(':child') {
                tasks.withType(${compileTaskType}) {
                    options.forkOptions.jvmArgs = ["-Dfoo=bar"]
                }
            }
        """

        when:
        succeeds("compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", ":child${compileTaskPath('main')}"

        and:
        assertTwoCompilerDaemonsAreRunning()
    }

    List<String> getRunningCompilerDaemons() {
        def compilerDaemonSets = compilerDaemonIdentityFile.readLines()
        assert compilerDaemonSets.size() == 1
        assert compilerDaemonSets[0].trim() != ""
        return Arrays.asList(compilerDaemonSets[0].split(" "))
    }

    void assertOneCompilerDaemonIsRunning() {
        assert runningCompilerDaemons.size() == 1
    }

    void assertRunningCompilerDaemonIs(String expected) {
        assert runningCompilerDaemons == [ expected ]
    }

    void assertTwoCompilerDaemonsAreRunning() {
        assert runningCompilerDaemons.size() == 2
    }

    String newSourceSet(String name) {
        return """
            sourceSets {
                ${name} {
                    compileClasspath += main.compileClasspath
                }
            }
        """
    }

    def withCompositeBuildSources() {
        TestFile child = file("child").createDir()
        component.writeSources(file("src/main"))
        component.writeSources(child.file("src/main"))
        child.file("build.gradle") << """
            allprojects {
                ${applyAndConfigure}

                tasks.withType(${compileTaskType}) {
                    options.fork = true
                }

                group = "org.test"
                version = "1.0"
            }
        """
        child.file("settings.gradle").touch()

        buildFile << """
            dependencies {
                implementation "org.test:child:1.0"
            }
        """
        settingsFile << """
            includeBuild "child"
        """
    }

    boolean supportsCompositeBuilds() {
        true
    }

    def withMultiProjectSources() {
        TestFile child = file("child").createDir()
        component.writeSources(file("src/main"))
        component.writeSources(child.file("src/main"))
        settingsFile << """
            include ":child"
        """
    }

    def withSingleProjectSources() {
        component.writeSources(file("src/main"))
        component.writeSources(file("src/main2"))
        buildFile << newSourceSet("main2")
    }

    String compileTaskPath(String sourceSet) {
        if (sourceSet == "main") {
            return ":compile${component.languageName.capitalize()}"
        } else {
            return ":compile${sourceSet.capitalize()}${component.languageName.capitalize()}"
        }
    }
}
