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

import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = NOT_EMBEDDED_REASON)
class WrapperUserHomeIntegrationTest extends AbstractWrapperIntegrationSpec {
    void 'uses gradle user home set by -Dgradle.user.home'() {
        given:
        prepareWrapper()
        def gradleUserHome = testDirectory.file('some-custom-user-home')

        when:
        def executer = wrapperExecuter.withGradleUserHomeDir(null)
        executer.withArguments("-Dgradle.user.home=$gradleUserHome.absolutePath")
        executer.run()

        then:
        installationIn gradleUserHome
    }

    @Issue('https://issues.gradle.org/browse/GRADLE-2802')
    void 'uses gradle user home set by -g'() {
        given:
        prepareWrapper()
        def gradleUserHome = testDirectory.file('some-custom-user-home')

        when:
        def executer = wrapperExecuter.withGradleUserHomeDir(null)
        executer.withArguments('-g', gradleUserHome.absolutePath)
        executer.run()

        then:
        installationIn gradleUserHome
    }
}
