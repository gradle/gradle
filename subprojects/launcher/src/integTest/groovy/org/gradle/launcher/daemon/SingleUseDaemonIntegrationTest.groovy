/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.daemon

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.util.TextUtil
import spock.lang.IgnoreIf
import org.gradle.integtests.fixtures.executer.GradleDistributionExecuter

@IgnoreIf({ GradleDistributionExecuter.systemPropertyExecuter == GradleDistributionExecuter.Executer.daemon })
class SingleUseDaemonIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        // Need forking executer
        // '-ea' is always set on the forked process. So I've added it explicitly here. // TODO:DAZ Clean this up
        executer.withForkingExecuter().withEnvironmentVars(["JAVA_OPTS": "-ea"])
        distribution.requireIsolatedDaemons()
    }

    def "stops single use daemon on build complete"() {
        requireJvmArg('-Xmx32m')

        file('build.gradle') << "println 'hello world'"

        when:
        succeeds()

        then:
        wasForked()
        and:
        executer.getDaemonRegistry().all.empty
    }

    def "stops single use daemon when build fails"() {
        requireJvmArg('-Xmx32m')

        file('build.gradle') << "throw new RuntimeException('bad')"

        when:
        fails()

        then:
        wasForked()
        failureHasCause "bad"

        and:
        executer.getDaemonRegistry().all.empty
    }

    @IgnoreIf({ AvailableJavaHomes.bestAlternative == null})
    def "does not fork build if java home from gradle properties matches current process"() {
        def alternateJavaHome = AvailableJavaHomes.bestAlternative

        file('gradle.properties') << "org.gradle.java.home=${TextUtil.escapeString(alternateJavaHome.canonicalPath)}"

        file('build.gradle') << "println 'javaHome=' + org.gradle.internal.jvm.Jvm.current().javaHome.absolutePath"

        when:
        executer.withJavaHome(alternateJavaHome)
        succeeds()

        then:
        !wasForked();
    }

    def "forks build to run when immutable jvm args set regardless of the environment"() {
        when:
        requireJvmArg('-Xmx32m')
        runWithJvmArg('-Xmx32m')

        and:
        file('build.gradle') << """
assert java.lang.management.ManagementFactory.runtimeMXBean.inputArguments.contains('-Xmx32m')
"""

        then:
        succeeds()

        and:
        wasForked()
    }

    def "does not fork build and configures system properties from gradle properties"() {
        when:
        requireJvmArg('-Dsome-prop=some-value')

        and:
        file('build.gradle') << """
assert System.getProperty('some-prop') == 'some-value'
"""

        then:
        succeeds()

        and:
        !wasForked()
    }

    private def requireJvmArg(String jvmArg) {
        file('gradle.properties') << "org.gradle.jvmargs=$jvmArg"
    }

    private def runWithJvmArg(String jvmArg) {
        executer.withEnvironmentVars(["JAVA_OPTS": "$jvmArg -ea"])
    }

    private def wasForked() {
        result.output.contains('fork a new JVM')
    }
}
