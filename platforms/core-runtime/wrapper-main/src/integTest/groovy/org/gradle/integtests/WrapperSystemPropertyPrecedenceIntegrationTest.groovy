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
class WrapperSystemPropertyPrecedenceIntegrationTest extends AbstractWrapperIntegrationSpec {

    @Issue('https://github.com/gradle/gradle/issues/36189')
    def 'system properties respect project < user < cli precedence'() {
        given:
        def originalSystemProps = new Properties()
        originalSystemProps.putAll(System.getProperties())

        prepareWrapper().run()
        buildKotlinFile << 'println("foo=" + System.getProperty("foo", "<null>"))'

        def gradleUserHome = testDirectory.file('some-custom-user-home')
        gradleUserHome.mkdirs()
        new File(gradleUserHome, 'gradle.properties') << (user ? 'systemProp.foo=user' : '')
        testDirectory.file('gradle.properties') <<  (project ? 'systemProp.foo=project' : '')

        when:
        def executer = wrapperExecuter.withGradleUserHomeDir(gradleUserHome)
        if (cli) {
            executer.withArguments('-Dfoo=cli')
        }
        def res = executer.run()

        then:
        res.assertOutputContains("foo=$expectedValue")

        cleanup:
        System.setProperties(originalSystemProps)

        where:
        project   | user   | cli   | expectedValue
        'project' | 'user' | 'cli' | 'cli'
        'project' | 'user' | null  | 'user'
        'project' | null   | 'cli' | 'cli'
        null      | 'user' | 'cli' | 'cli'
        'project' | null   | null  | 'project'
        null      | 'user' | null  | 'user'
        null      | null   | 'cli' | 'cli'
    }
}
