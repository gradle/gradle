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

package org.gradle.integtests.environment

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.util.TextUtil
import spock.lang.IgnoreIf
import org.gradle.integtests.fixtures.GradleDistributionExecuter

@IgnoreIf({ GradleDistributionExecuter.systemPropertyExecuter == GradleDistributionExecuter.Executer.daemon })
class SingleUseDaemonIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        // Need forking executer
        // '-ea' is always set on the forked process. So I've added it explicitly here. // TODO:DAZ Clean this up
        executer.withForkingExecuter().withEnvironmentVars(["JAVA_OPTS": "-ea"])
        distribution.requireIsolatedDaemons()
    }

    def "should stop single use daemon on build complete"() {
        requireJvmArg('-Xmx32m')

        file('build.gradle') << "println 'hello world'"

        when:
        succeeds()

        then:
        wasForked()
        and:
        executer.getDaemonRegistry().all.empty
    }

    @IgnoreIf({ AvailableJavaHomes.bestAlternative == null})
    def "should not fork build if java home from gradle properties matches current process"() {
        def alternateJavaHome = AvailableJavaHomes.bestAlternative

        file('gradle.properties') << "org.gradle.java.home=${TextUtil.escapeString(alternateJavaHome.canonicalPath)}"

        file('build.gradle') << "println 'javaHome=' + org.gradle.util.Jvm.current().javaHome.absolutePath"

        when:
        executer.withJavaHome(alternateJavaHome)
        succeeds()

        then:
        !wasForked();
    }

    def "jvm arguments from gradle properties should be used to run build"() {
        requireJvmArg('-Xmx32m')

        file('build.gradle') << """
assert java.lang.management.ManagementFactory.runtimeMXBean.inputArguments.contains('-Xmx32m')
"""

        when:
        succeeds()

        then:
        wasForked()

        when:
        runWithJvmArg('-Xmx32m')
        succeeds()

        then:
        !wasForked()
    }

    def "system properties from gradle properties should be used to run build"() {
        requireJvmArg('-Dsome-prop=some-value')

        file('build.gradle') << """
assert System.getProperty('some-prop') == 'some-value'
"""

        when:
        succeeds()

        then:
        wasForked()

        when:
        runWithJvmArg('-Dsome-prop=some-value')
        succeeds()

        then:
        !wasForked()
    }

    private def requireJvmArg(String jvmArg) {
        file('gradle.properties') << "org.gradle.jvmargs=$jvmArg -ea"
    }

    private def runWithJvmArg(String jvmArg) {
        executer.withEnvironmentVars(["JAVA_OPTS": "$jvmArg -ea"])
    }

    private def wasForked() {
        result.output.contains('daemon')
    }
}
