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

package org.gradle.integtests.tooling.r88

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.tooling.fixture.DaemonJvmPropertiesFixture
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.model.build.BuildEnvironment
import org.junit.Assume

@TargetGradleVersion(">=8.8")
class DaemonToolchainCrossVersionTest extends ToolingApiSpecification implements DaemonJvmPropertiesFixture {

    @Requires(IntegTestPreconditions.Java8HomeAvailable)
    def "Given daemon toolchain version When executing any task Then daemon jvm was set up with expected configuration"() {
        given:
        def jdk8 = AvailableJavaHomes.jdk8
        writeJvmCriteria(jdk8.javaVersion.majorVersion)
        captureJavaHome()

        when:
        withConnection {
            it.newBuild().forTasks("help").run()
        }

        then:
        assertDaemonUsedJvm(jdk8.javaHome)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given other daemon toolchain version When executing any task Then daemon jvm was set up with expected configuration"() {
        given:
        def otherJvm = AvailableJavaHomes.differentVersion
        writeJvmCriteria(otherJvm.javaVersion.majorVersion)
        captureJavaHome()

        when:
        withConnection {
            it.newBuild().forTasks("help").run()
        }

        then:
        assertDaemonUsedJvm(otherJvm.javaHome)
    }

    def "Given daemon toolchain criteria that doesn't match installed ones When executing any task Then fails with the expected message"() {
        given:
        // Java 10 is not available
        def java10 = AvailableJavaHomes.getAvailableJdks { it.javaVersion == "10" }
        Assume.assumeTrue(java10.isEmpty())
        writeJvmCriteria("10")
        captureJavaHome()

        when:
        withConnection {
            it.newBuild().forTasks("help").run()
        }

        then:
        def e= thrown(GradleConnectionException)
        e.cause.message.contains("Cannot find a Java installation on your machine")
    }

    @Requires(IntegTestPreconditions.Java8HomeAvailable)
    def "Given daemon toolchain criteria When obtaining build information Then build environment java home matches with expected one"() {
        given:
        def jdk8 = AvailableJavaHomes.jdk8
        writeJvmCriteria(jdk8.javaVersion.majorVersion)
        captureJavaHome()

        when:
        BuildEnvironment env = withConnection {
            it.getModel(BuildEnvironment.class)
        }

        then:
        env.java.javaHome == jdk8.javaHome
    }
}
