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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

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
        succeeds("show", "-Porg.gradle.java.installations.auto-detect=false")
    }

    @Requires(IntegTestPreconditions.MoreThanOneJavaHomeAvailable)
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
            .withArgument("-Porg.gradle.java.installations.paths=${new File("/unknown/path").absolutePath}," + secondJavaHome)
            .withArgument("-Porg.gradle.java.installations.fromEnv=JDK1,JDK2")
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
            .withArgument("-Porg.gradle.java.installations.paths=${new File("/other/path").absolutePath}," + secondJavaHome)
            .withArgument("-Porg.gradle.java.installations.fromEnv=JDK1,JDK2")
            .withTasks("show")
            .run()
        then:
        outputContains("Directory '${new File("/other/path").absolutePath}' (Gradle property 'org.gradle.java.installations.paths') used for java installations does not exist")
        outputContains("Directory '${new File("/unknown/env").absolutePath}' (environment variable 'JDK1') used for java installations does not exist")
        outputContains(firstJavaHome)
        outputContains(secondJavaHome)
    }

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
                .withArgument("-Porg.gradle.java.installations.paths=" + relativePath(rootProject, javaHome))
                .withTasks("show")
                .inDirectory(new File(rootProject, subproject))
                .run()
        then:
        outputContains(javaHome)
    }

    private static String relativePath(TestFile from, String to) {
        from.toPath().relativize(new File(to).toPath()).toString()
    }

}
