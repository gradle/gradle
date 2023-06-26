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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.test.fixtures.file.TestFile
import org.junit.Assume
import spock.lang.IgnoreIf

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

    @IgnoreIf({ GradleContextualExecuter.parallel })
    @UnsupportedWithConfigurationCache(because = "parallel by default")
    def "reuses compiler daemons within a single project build"() {
        withSingleProjectSources()

        when:
        succeeds("compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", "${compileTaskPath('main2')}"

        and:
        assertOneCompilerDaemonIsCreated()
    }

    @IgnoreIf({ GradleContextualExecuter.parallel })
    @UnsupportedWithConfigurationCache(because = "parallel by default")
    def "reuses compiler daemons within a multi-project build"() {
        withMultiProjectSources()

        when:
        succeeds("compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", ":child${compileTaskPath('main')}"

        and:
        assertOneCompilerDaemonIsCreated()
    }

    @IgnoreIf({ GradleContextualExecuter.parallel })
    def "reuses compiler daemons within a composite build"() {
        Assume.assumeTrue(supportsCompositeBuilds())

        withCompositeBuildSources()

        when:
        succeeds("compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", ":child${compileTaskPath('main')}"

        and:
        assertOneCompilerDaemonIsCreated()
    }

    @IgnoreIf({ GradleContextualExecuter.parallel })
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
        assertTwoCompilerDaemonsAreCreated()
    }

    void assertOneCompilerDaemonIsCreated() {
        def compilerDaemonSets = compilerDaemonIdentityFile.readLines()
        assert compilerDaemonSets.size() > 0
        assert compilerDaemonSets[0].trim() != ""
        assert compilerDaemonSets[0].split(" ").size() == 1
    }

    void assertTwoCompilerDaemonsAreCreated() {
        def compilerDaemonSets = compilerDaemonIdentityFile.readLines()
        assert compilerDaemonSets.size() > 0
        assert compilerDaemonSets[0].split(" ").size() == 2
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
