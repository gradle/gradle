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

package org.gradle.jvm.toolchain

import org.gradle.api.problems.Severity
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.InstalledJdkTestPreconditions
import org.gradle.integtests.fixtures.modes.ToBeFixedForIsolatedProjects

class JavaInstallationRegistryIntegrationTest extends AbstractIntegrationSpec {

    def "installation registry has no installations without environment setup or auto-detection"() {
        buildFile << """
            import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;

            abstract class ShowPlugin implements Plugin<Project> {
                @Inject
                abstract JavaInstallationRegistry getRegistry()

                void apply(Project project) {
                    project.tasks.register("show") {
                        def installations = registry.listInstallations()
                        assert installations.size() == 1
                        assert installations[0].location == org.gradle.internal.jvm.Jvm.current().javaHome
                    }
                }
            }

            apply plugin: ShowPlugin
        """

        expect:
        succeeds("show", "-Dorg.gradle.java.installations.auto-detect=false")
    }

    @Requires(InstalledJdkTestPreconditions.MoreThanOneJavaHomeAvailable)
    def "installation registry is populated by environment"() {
        def firstJavaHome = AvailableJavaHomes.availableJvms[0].javaHome.absolutePath
        def secondJavaHome = AvailableJavaHomes.availableJvms[1].javaHome.absolutePath

        buildFile << """
            import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;

            abstract class ShowPlugin implements Plugin<Project> {
                @Inject
                abstract JavaInstallationRegistry getRegistry()

                void apply(Project project) {
                    project.tasks.register("show") {
                       registry.listInstallations().each { println it.location }
                    }
                }
            }

            apply plugin: ShowPlugin
        """

        when:
        result = executer
            .withEnvironmentVars([JDK1: new File("/unknown/env").absolutePath, JDK2: firstJavaHome])
            .withArgument("-Dorg.gradle.java.installations.paths=${new File("/unknown/path").absolutePath}," + secondJavaHome)
            .withArgument("-Dorg.gradle.java.installations.fromEnv=JDK1,JDK2")
            .withArgument("--info")
            .withTasks("show")
            .run()
        then:
        outputContains("Directory '${new File("/unknown/path").absolutePath}' (Gradle property 'org.gradle.java.installations.paths') used for java installations does not exist")
        outputContains("Directory '${new File("/unknown/env").absolutePath}' (environment variable 'JDK1') used for java installations does not exist")
        outputContains(firstJavaHome)
        outputContains(secondJavaHome)

        when:
        result = executer
            .withEnvironmentVars([JDK1: new File("/unknown/env").absolutePath, JDK2: firstJavaHome])
            .withArgument("-Dorg.gradle.java.installations.paths=${new File("/other/path").absolutePath}," + secondJavaHome)
            .withArgument("-Dorg.gradle.java.installations.fromEnv=JDK1,JDK2")
            .withTasks("show")
            .run()
        then:
        outputContains("Directory '${new File("/other/path").absolutePath}' (Gradle property 'org.gradle.java.installations.paths') used for java installations does not exist")
        outputContains("Directory '${new File("/unknown/env").absolutePath}' (environment variable 'JDK1') used for java installations does not exist")
        outputContains(firstJavaHome)
        outputContains(secondJavaHome)
    }

    @Requires(InstalledJdkTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "installation registry is populated by JAVA_HOME environment variable"() {
        def currentJvm = Jvm.current().javaHome.absolutePath
        def otherJvm = AvailableJavaHomes.differentVersion.javaHome.absolutePath

        buildFile << """
            import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;

            abstract class ShowPlugin implements Plugin<Project> {
                @Inject
                abstract JavaInstallationRegistry getRegistry()

                void apply(Project project) {
                    project.tasks.register("show") {
                       registry.listInstallations().each { println it.location }
                    }
                }
            }

            apply plugin: ShowPlugin
        """

        when:
        result = executer
            .withArguments("-Dorg.gradle.java.home=$currentJvm", "--info")
            .withEnvironmentVarsIncludingJavaHome([JAVA_HOME: otherJvm])
            .withTasks("show")
            .requireIsolatedDaemons()
            .run()

        then:
        outputContains(currentJvm)
        outputContains(otherJvm)
    }

    @ToBeFixedForIsolatedProjects(because = "toolchain registry queries cross-project state")
    def "relative file paths are resolved relative to root dir"() {
        def javaHome = AvailableJavaHomes.availableJvms[0].javaHome.absolutePath

        buildTestFixture.withBuildInSubDir()
        def subproject = "app"
        def subprojects = [subproject]
        def rootProject = multiProjectBuild("project", subprojects) {
            buildFile << """
                import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;

                abstract class ShowPlugin implements Plugin<Project> {
                    @Inject
                    abstract JavaInstallationRegistry getRegistry()

                    void apply(Project project) {
                        project.tasks.register("show") {
                           registry.listInstallations().each { println it.location }
                        }
                    }
                }

                allprojects {
                    apply plugin: ShowPlugin
                }
            """
        }

        when:
        result = executer
                .withArgument("-Dorg.gradle.java.installations.paths=" + relativePath(rootProject, javaHome))
                .withTasks("show")
                .inDirectory(new File(rootProject, subproject))
                .run()
        then:
        outputContains(javaHome)
    }

    def "invalid installation paths are reported as problems via the Problems API"() {
        enableProblemsApiCheck()

        def missing1 = new File("/nonexistent/jdk1").absolutePath
        def missing2 = new File("/nonexistent/jdk2").absolutePath

        buildFile << """
            import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;

            abstract class ShowPlugin implements Plugin<Project> {
                @Inject
                abstract JavaInstallationRegistry getRegistry()

                void apply(Project project) {
                    project.tasks.register("show") {
                       registry.listInstallations().each { println it.location }
                    }
                }
            }

            apply plugin: ShowPlugin
        """

        when:
        result = executer
            .withArgument("-Dorg.gradle.java.installations.paths=${missing1},${missing2}")
            .withArgument("-Dorg.gradle.java.installations.auto-detect=false")
            .withTasks("show")
            .run()

        then:
        verifyAll(receivedProblem(0)) {
            fqid == 'jvm-toolchain:invalid-jvm-installation'
            severity == Severity.WARNING
            contextualLabel == "Directory '${missing1}' (Gradle property 'org.gradle.java.installations.paths') used for java installations does not exist"
        }
        verifyAll(receivedProblem(1)) {
            fqid == 'jvm-toolchain:invalid-jvm-installation'
            severity == Severity.WARNING
            contextualLabel == "Directory '${missing2}' (Gradle property 'org.gradle.java.installations.paths') used for java installations does not exist"
        }
    }

    def "installation path pointing to a file instead of a directory is reported as a problem"() {
        enableProblemsApiCheck()

        def notADirectory = file("not-a-jdk.txt")
        notADirectory.touch()

        buildFile << """
            import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;

            abstract class ShowPlugin implements Plugin<Project> {
                @Inject
                abstract JavaInstallationRegistry getRegistry()

                void apply(Project project) {
                    project.tasks.register("show") {
                       registry.listInstallations().each { println it.location }
                    }
                }
            }

            apply plugin: ShowPlugin
        """

        when:
        result = executer
            .withArgument("-Dorg.gradle.java.installations.paths=${notADirectory.absolutePath}")
            .withArgument("-Dorg.gradle.java.installations.auto-detect=false")
            .withTasks("show")
            .run()

        then:
        verifyAll(receivedProblem) {
            fqid == 'jvm-toolchain:invalid-jvm-installation'
            severity == Severity.WARNING
            contextualLabel == "Path for java installation '${notADirectory.absolutePath}' (Gradle property 'org.gradle.java.installations.paths') points to a file, not a directory"
        }
    }

    def "installation path missing java executable is reported as a problem"() {
        enableProblemsApiCheck()

        def emptyDir = file("empty-jdk").createDir()

        buildFile << """
            import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;

            abstract class ShowPlugin implements Plugin<Project> {
                @Inject
                abstract JavaInstallationRegistry getRegistry()

                void apply(Project project) {
                    project.tasks.register("show") {
                       registry.listInstallations().each { println it.location }
                    }
                }
            }

            apply plugin: ShowPlugin
        """

        when:
        result = executer
            .withArgument("-Dorg.gradle.java.installations.paths=${emptyDir.absolutePath}")
            .withArgument("-Dorg.gradle.java.installations.auto-detect=false")
            .withTasks("show")
            .run()

        then:
        verifyAll(receivedProblem) {
            fqid == 'jvm-toolchain:invalid-jvm-installation'
            severity == Severity.WARNING
            contextualLabel == "Path for java installation '${emptyDir.absolutePath}' (Gradle property 'org.gradle.java.installations.paths') does not contain a java executable"
        }
    }

    @Requires(InstalledJdkTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "valid installations are still discovered when invalid paths report problems"() {
        enableProblemsApiCheck()

        def validJavaHome = AvailableJavaHomes.differentVersion.javaHome.absolutePath
        def missing = new File("/nonexistent/jdk").absolutePath

        buildFile << """
            import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;

            abstract class ShowPlugin implements Plugin<Project> {
                @Inject
                abstract JavaInstallationRegistry getRegistry()

                void apply(Project project) {
                    project.tasks.register("show") {
                       registry.listInstallations().each { println it.location }
                    }
                }
            }

            apply plugin: ShowPlugin
        """

        when:
        result = executer
            .withArgument("-Dorg.gradle.java.installations.paths=${missing}," + validJavaHome)
            .withTasks("show")
            .run()

        then:
        outputContains(validJavaHome)
        verifyAll(receivedProblem) {
            fqid == 'jvm-toolchain:invalid-jvm-installation'
            severity == Severity.WARNING
            contextualLabel == "Directory '${missing}' (Gradle property 'org.gradle.java.installations.paths') used for java installations does not exist"
        }
    }

    def "invalid installation paths do not fail the build (regression test for gradle/gradle#34554)"() {
        def missingDir = new File("/nonexistent/jdk").absolutePath
        def notADirectory = file("not-a-jdk.txt")
        notADirectory.touch()
        def emptyDir = file("empty-jdk").createDir()

        buildFile << """
            import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;

            abstract class ShowPlugin implements Plugin<Project> {
                @Inject
                abstract JavaInstallationRegistry getRegistry()

                void apply(Project project) {
                    project.tasks.register("show") {
                       registry.listInstallations().each { println it.location }
                    }
                }
            }

            apply plugin: ShowPlugin
        """

        expect:
        succeeds(
            "show",
            "-Dorg.gradle.java.installations.paths=${missingDir},${notADirectory.absolutePath},${emptyDir.absolutePath}",
            "-Dorg.gradle.java.installations.auto-detect=false"
        )
    }

    private static String relativePath(TestFile from, String to) {
        from.toPath().relativize(new File(to).toPath()).toString()
    }

}
