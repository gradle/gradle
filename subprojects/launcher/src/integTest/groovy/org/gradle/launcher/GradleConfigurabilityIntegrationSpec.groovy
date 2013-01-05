/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.launcher

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.executer.GradleDistributionExecuter
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.nativeplatform.filesystem.FileSystems
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TextUtil
import spock.lang.IgnoreIf

/**
 * by Szczepan Faber, created at: 1/20/12
 */
@IgnoreIf( { !GradleDistributionExecuter.getSystemPropertyExecuter().forks })
class GradleConfigurabilityIntegrationSpec extends AbstractIntegrationSpec {

    def setup() {
        executer.requireIsolatedDaemons()
    }

    def buildSucceeds(String script) {
        file('build.gradle') << script
        executer.withArguments("--info").withNoDefaultJvmArgs().run()
    }

    def "honours jvm args specified in gradle.properties"() {
        given:
        file("gradle.properties") << "org.gradle.jvmargs=-Dsome-prop=some-value -Xmx16m"

        expect:
        buildSucceeds """
assert System.getProperty('some-prop') == 'some-value'
assert java.lang.management.ManagementFactory.runtimeMXBean.inputArguments.contains('-Xmx16m')
        """
    }

    @Requires(TestPrecondition.SYMLINKS)
    def "connects to the daemon if java home is a symlink"() {
        given:
        def javaHome = Jvm.current().javaHome
        def javaLink = file("javaLink")
        FileSystems.default.createSymbolicLink(javaLink, javaHome)
        file("tmp").deleteDir().createDir()

        String linkPath = TextUtil.escapeString(javaLink.absolutePath)
        file("gradle.properties") << "org.gradle.java.home=$linkPath"

        when:
        buildSucceeds "println 'java home =' + System.getProperty('java.home')"

        then:
        javaLink != javaHome
        javaLink.canonicalFile == javaHome.canonicalFile

        cleanup:
        javaLink.usingNativeTools().deleteDir()
    }

    //TODO SF add coverage for reconnecting to those daemons.
    def "honours jvm sys property that contain a space in gradle.properties"() {
        given:
        file("gradle.properties") << 'org.gradle.jvmargs=-Dsome-prop="i have space"'

        expect:
        buildSucceeds """
assert System.getProperty('some-prop').toString() == 'i have space'
        """
    }

    def "honours jvm option that contain a space in gradle.properties"() {
        given:
        file("gradle.properties") << 'org.gradle.jvmargs=-XX:HeapDumpPath="/tmp/with space" -Dsome-prop="and some more stress..."'

        expect:
        buildSucceeds """
def inputArgs = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()
assert inputArgs.find { it.contains('-XX:HeapDumpPath=') }
"""
    }

    @IgnoreIf({ AvailableJavaHomes.bestAlternative == null })
    def "honours java home specified in gradle.properties"() {
        given:
        File javaHome = AvailableJavaHomes.bestAlternative
        String javaPath = TextUtil.escapeString(javaHome.canonicalPath)
        file("gradle.properties") << "org.gradle.java.home=$javaPath"

        expect:
        buildSucceeds "assert System.getProperty('java.home').startsWith('$javaPath')"
    }
}
