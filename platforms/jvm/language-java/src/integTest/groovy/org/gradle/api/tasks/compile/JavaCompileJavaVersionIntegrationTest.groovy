/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.integtests.fixtures.jvm.TestJavaClassUtil
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Assume

import static org.gradle.internal.serialize.JavaClassUtil.getClassMajorVersion

class JavaCompileJavaVersionIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainFixture {

    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "requires use of specific JDK version")
    def "not up-to-date when default Java version changes"() {
        def otherJdk = AvailableJavaHomes.getDifferentVersion(jdk.javaVersion)
        Assume.assumeTrue(otherJdk != null)

        given:
        buildFile << """
            plugins {
                id("java-library")
            }
        """
        withHelloJava()

        when:
        executer.withJvm(jdk)
        succeeds "compileJava"

        then:
        executedAndNotSkipped ":compileJava"
        assertCompiledWith(jdk)

        when:
        executer.withJvm(jdk)
        succeeds "compileJava"

        then:
        skipped ":compileJava"
        assertCompiledWith(jdk)

        when:
        executer.withJvm(otherJdk)
        succeeds "compileJava", "--info"

        then:
        executedAndNotSkipped ":compileJava"
        assertCompiledWith(otherJdk)
        output.contains "Value of input property 'javaVersion' has changed for task ':compileJava'"

        where:
        jdk << AvailableJavaHomes.supportedDaemonJdks
    }

    def "not up-to-date when java version for forking changes"() {
        def otherJdk = AvailableJavaHomes.getDifferentVersion(jdk.javaVersion)
        Assume.assumeTrue(otherJdk != null)

        given:
        buildFile << """
            plugins {
                id("java-library")
            }

            def compileJavaVersion = providers.systemProperty("compileJavaVersion")

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(compileJavaVersion.get())
                }
            }
        """
        withHelloJava()

        when:
        compileWith(jdk)
        succeeds "compileJava"

        then:
        executedAndNotSkipped ":compileJava"
        assertCompiledWith(jdk)

        when:
        compileWith(jdk)
        succeeds "compileJava"

        then:
        skipped ":compileJava"
        assertCompiledWith(jdk)

        when:
        compileWith(otherJdk)
        succeeds "compileJava", "--info"

        then:
        executedAndNotSkipped ":compileJava"
        assertCompiledWith(otherJdk)
        output.contains "Value of input property 'javaVersion' has changed for task ':compileJava'"

        where:
        jdk << AvailableJavaHomes.supportedWorkerJdks
    }

    private TestFile withHelloJava() {
        file("src/main/java/Hello.java") << """
            public class Hello { }
        """
    }

    void compileWith(Jvm jvm) {
        withInstallations(jvm)
        executer.withArgument("-DcompileJavaVersion=${jvm.javaVersionMajor}")
    }

    void assertCompiledWith(Jvm jvm) {
        assert getClassMajorVersion(javaClassFile("Hello.class")) == TestJavaClassUtil.getClassVersion(jvm.javaVersion)
    }
}
