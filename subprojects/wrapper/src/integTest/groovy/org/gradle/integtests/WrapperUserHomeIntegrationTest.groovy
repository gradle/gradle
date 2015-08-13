/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

@LeaksFileHandles
class WrapperUserHomeIntegrationTest extends AbstractIntegrationSpec {

    void setup() {
        assert distribution.binDistribution.exists() : "bin distribution must exist to run this test, you need to run the :distributions:binZip task"
        executer.requireIsolatedDaemons()
    }

    private prepareWrapper() {
        file("build.gradle") << """
            wrapper {
                distributionUrl = '${distribution.binDistribution.toURI()}'
            }
        """
        executer.withTasks('wrapper').run()
        executer.usingExecutable('gradlew').inDirectory(testDirectory).withGradleUserHomeDir(null)
    }

    private def installationIn(TestFile userHomeDir) {
        def distDir = userHomeDir.file("wrapper/dists/gradle-${distribution.version.version}-bin").assertIsDir()
        assert distDir.listFiles().length == 1
        return distDir.listFiles()[0].file("gradle-${distribution.version.version}").assertIsDir()
    }

    void 'uses gradle user home set by -Dgradle.user.home'() {
        given:
        prepareWrapper()
        def gradleUserHome = testDirectory.file('user-home')

        when:
        args "-Dgradle.user.home=$gradleUserHome.absolutePath"
        succeeds()

        then:
        installationIn gradleUserHome exists()
    }

    @Issue('https://issues.gradle.org/browse/GRADLE-2802')
    void 'uses gradle user home set by -g'() {
        given:
        prepareWrapper()
        def gradleUserHome = testDirectory.file('user-home')

        when:
        args '-g', gradleUserHome.absolutePath
        succeeds()

        then:
        installationIn gradleUserHome exists()
    }
}
