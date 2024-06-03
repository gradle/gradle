/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.java.compile.daemon

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.java.compile.AbstractJavaCompilerIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.TextUtil
import spock.lang.Issue

class DaemonJavaCompilerIntegrationTest extends AbstractJavaCompilerIntegrationSpec {

    @Override
    String compilerConfiguration() {
        """
            tasks.withType(JavaCompile) {
                options.fork = true
            }
        """
    }

    @Override
    String logStatement() {
        "compiler daemon"
    }

    def setup() {
        executer.withArguments("-d")
    }

    def "respects fork options settings"() {
        goodCode()
        buildFile << """
            import org.gradle.workers.internal.WorkerDaemonClientsManager
            import org.gradle.internal.jvm.Jvm

            tasks.withType(JavaCompile) {
                options.forkOptions.memoryInitialSize = "128m"
                options.forkOptions.memoryMaximumSize = "256m"
                options.forkOptions.jvmArgs = ["-Dfoo=bar"]

                doLast {
                    assert services.get(WorkerDaemonClientsManager).idleClients.find {
                        new File(it.forkOptions.javaForkOptions.executable).canonicalPath == Jvm.current().javaExecutable.canonicalPath &&
                        it.forkOptions.javaForkOptions.minHeapSize == "128m" &&
                        it.forkOptions.javaForkOptions.maxHeapSize == "256m" &&
                        it.forkOptions.javaForkOptions.systemProperties['foo'] == "bar"
                    }
                }
            }
        """

        expect:
        succeeds "compileJava"
    }

    def "handles -sourcepath being specified"() {
        goodCode()
        buildFile << """
            tasks.withType(JavaCompile) {
                options.sourcepath = project.layout.files()
            }
        """

        expect:
        succeeds "compileJava"
    }

    @Issue("https://github.com/gradle/gradle/issues/3098")
    @Requires([
        UnitTestPreconditions.Jdk8OrEarlier,
        UnitTestPreconditions.JdkOracle
    ])
    def "handles -bootclasspath being specified"() {
        def jre = AvailableJavaHomes.getBestJre()
        def bootClasspath = TextUtil.escapeString(jre.absolutePath) + "/lib/rt.jar"
        goodCode()
        buildFile << """
            tasks.withType(JavaCompile) {
                options.bootstrapClasspath = project.layout.files("$bootClasspath")
            }
        """

        expect:
        succeeds "compileJava"
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "computes target jvm version when using toolchain"() {
        given:
        def jdk = AvailableJavaHomes.differentVersion
        def javaVersion = jdk.javaVersion.getMajorVersion()

        and:
        goodCode()
        buildFile << """
            java.toolchain {
                languageVersion = JavaLanguageVersion.of(${javaVersion})
            }

            assert configurations.apiElements.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == ${javaVersion}
            assert configurations.runtimeElements.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == ${javaVersion}
            assert configurations.compileClasspath.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == ${javaVersion}
            assert configurations.runtimeClasspath.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == ${javaVersion}
        """

        expect:
        executer.withArgument("-Porg.gradle.java.installations.paths=" + jdk.javaHome.absolutePath)
        succeeds("compileJava")
    }

}
