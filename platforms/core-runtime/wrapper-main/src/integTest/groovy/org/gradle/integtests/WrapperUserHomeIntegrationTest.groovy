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

import java.util.function.Consumer

@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = NOT_EMBEDDED_REASON)
class WrapperUserHomeIntegrationTest extends AbstractWrapperIntegrationSpec {

    def 'uses gradle user home set by -Dgradle.user.home'() {
        given:
        prepareWrapper().run()
        def gradleUserHome = testDirectory.file('some-custom-user-home')

        when:
        def executer = wrapperExecuter.withGradleUserHomeDir(null)
        executer.withArguments("-Dgradle.user.home=$gradleUserHome.absolutePath")
        executer.run()

        then:
        installationIn gradleUserHome
    }

    @Issue('https://issues.gradle.org/browse/GRADLE-2802')
    def 'uses gradle user home set by -g'() {
        given:
        prepareWrapper().run()
        def gradleUserHome = testDirectory.file('some-custom-user-home')

        when:
        def executer = wrapperExecuter.withGradleUserHomeDir(null)
        executer.withArguments('-g', gradleUserHome.absolutePath)
        executer.run()

        then:
        installationIn gradleUserHome
    }

    def 'uses gradle user home in gradle.properties via a system property'() {
        given:
        prepareWrapper().run()
        def gradleUserHome = testDirectory.file('some-custom-user-home')
        writeProperties(testDirectory.file('gradle.properties')) { props ->
            props.setProperty('systemProp.gradle.user.home', gradleUserHome.absolutePath)
        }

        when:
        wrapperExecuter.withGradleUserHomeDir(null).run()

        then:
        installationIn gradleUserHome
    }

    def 'command-line gradle user home configuration has precedence'() {
        given:
        prepareWrapper().run()
        def userGradleUserHome = testDirectory.file('user-custom-user-home')
        def cmdGradleUserHome = testDirectory.file('cmd-custom-user-home')
        writeProperties(testDirectory.file('gradle.properties')) { props ->
            props.setProperty('systemProp.gradle.user.home', userGradleUserHome.absolutePath)
        }

        when:
        wrapperExecuter.withGradleUserHomeDir(null).withArguments('-g', cmdGradleUserHome.absolutePath).run()

        then:
        installationIn cmdGradleUserHome
    }

    def 'ignores gradle user home system property declared in gradle user home'() {
        given:
        prepareWrapper().run()
        def gradleUserHome = testDirectory.file('some-custom-user-home')
        gradleUserHome.mkdirs()
        def userGradleProperties = new File(gradleUserHome, 'gradle.properties')
        userGradleProperties.text = 'systemProp.gradle.user.home=missingLocation'

        when:
        def res = wrapperExecuter.withGradleUserHomeDir(null).withArguments('-g', gradleUserHome.absolutePath).run()

        then:
        res.assertOutputContains("WARNING Ignored custom Gradle user home location configured in Gradle user home: ${userGradleProperties.absolutePath}")
    }

    private static def writeProperties(File target, Consumer<Properties> config) {
        Properties props = new Properties()
        config.accept(props)
        try (OutputStream out = new FileOutputStream(target)) {
            props.store(out, null);
        }
    }
}
