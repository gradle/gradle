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
import org.gradle.wrapper.PathAssembler
import org.gradle.wrapper.WrapperConfiguration
import spock.lang.Issue

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

    private     installationIn(String gradleUserHomePath) {
        def config = new WrapperConfiguration(distribution: distribution.binDistribution.toURI())
        File distDir = new PathAssembler(new File(gradleUserHomePath)).getDistribution(config).distributionDir
        new File(distDir, "gradle-$distribution.version.version/bin/gradle")
    }

    void 'uses gradle user home set by -Dgradle.user.home'() {
        given:
        prepareWrapper()
        def gradleUserHomePath = testDirectory.file('user-home').absolutePath

        when:
        args "-Dgradle.user.home=$gradleUserHomePath"
        succeeds()

        then:
        installationIn gradleUserHomePath exists()
    }

    @Issue('http://issues.gradle.org/browse/GRADLE-2802')
    void 'uses gradle user home set by -g'() {
        given:
        prepareWrapper()
        def gradleUserHomePath = testDirectory.file('user-home').absolutePath

        when:
        args '-g', gradleUserHomePath
        succeeds()

        then:
        installationIn gradleUserHomePath exists()
    }
}
