/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.launcher.toolchains.r88


import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.tooling.GradleConnectionException
import org.junit.Assume

@TargetGradleVersion(">=8.8")
class DaemonToolchainCrossVersionSpec extends ToolingApiSpecification {
    void assertJavaVersion(javaVersion) {
        buildFile << """
            def javaVersion = org.gradle.internal.jvm.Jvm.current().javaVersion
            println javaVersion
            assert javaVersion.majorVersion == "${javaVersion}"
        """
    }

    TestFile getBuildPropertiesFile() {
        return file("gradle/gradle-build.properties")
    }

    void assertJvmCriteria(String version) {
        Map<String, String> properties = buildPropertiesFile.properties
        assert properties.get("daemon.jvm.toolchain.version") == version
    }

    void writeJvmCriteria(String version) {
        Properties properties = new Properties()
        properties.put("daemon.jvm.toolchain.version", version)
        buildPropertiesFile.writeProperties(properties)
        assertJvmCriteria(version)
    }

    def setup() {
        requireDaemons()
    }

    def "Given daemon toolchain version When executing any task Then daemon jvm was set up with expected configuration"() {
        given:
        writeJvmCriteria("11")
        assertJavaVersion("11")

        when:
        def stdout = new ByteArrayOutputStream()
        withConnection {
            it.newBuild().forTasks("help").setStandardOutput(stdout).run()
        }
        then:
        assertJvmCriteria("11")
//        stdout.toString().contains("Daemon JVM discovery is an incubating feature.")
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given other daemon toolchain version When executing any task Then daemon jvm was set up with expected configuration"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm.javaVersion.majorVersion)
        assertJavaVersion(otherJvm.javaVersion.majorVersion)

        when:
        withConnection {
            it.newBuild().
                forTasks("help").
                run()
        }
        then:
        assertJvmCriteria(otherJvm.javaVersion.majorVersion)
    }

    def "Given daemon toolchain criteria that doesn't match installed ones When executing any task Then fails with the expected message"() {
        given:
        // Java 10 is not available
        def java10 = AvailableJavaHomes.getAvailableJdks { it.javaVersion == "10" }
        Assume.assumeTrue(java10.isEmpty())
        writeJvmCriteria("10")
        assertJavaVersion("10")

        when:
        withConnection {
            it.newBuild().forTasks("help").run()
        }
        then:
        def e= thrown(GradleConnectionException)
        e.cause.message.contains("Cannot find a Java installation on your machine")
    }
}
