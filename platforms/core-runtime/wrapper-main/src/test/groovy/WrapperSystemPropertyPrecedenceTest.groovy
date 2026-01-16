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

import org.gradle.wrapper.GradleWrapperMain
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir

class WrapperSystemPropertyPrecedenceTest extends Specification {

    @TempDir
    File tempDir

    @Issue('https://github.com/gradle/gradle/issues/36189')
    def 'updates system properties with project < user < cli precedence'() {
        given:
        def originalSystemProps = new Properties()
        originalSystemProps.putAll(System.getProperties())

        def wrapperDir = new File(tempDir, 'gradle/wrapper')
        wrapperDir.mkdirs()
        def userHomeDir = new File(tempDir, 'gradle-user-home')
        userHomeDir.mkdirs()
        def wrapperJar = new File(wrapperDir, 'gradle-wrapper.jar')
        wrapperJar.text = ''
        def wrapperProperties = new File(wrapperDir, 'gradle-wrapper.properties')
        wrapperProperties.text = mockWapperProperties()
        def gradleProperties =  new File(tempDir, 'gradle.properties')
        gradleProperties.text = "systemProp.gradle.user.home=${userHomeDir.absolutePath}\n" +  (project ? "systemProp.foo=$project" : '')
        def gradleUserProperties = new File(userHomeDir, 'gradle.properties')
        gradleUserProperties.text = (user ? "systemProp.foo=$user" : '')

        when:
        GradleWrapperMain.prepareWrapper((cli ? ["-Dfoo=$cli"] : []) as String[], wrapperJar)

        then:
        System.getProperty("foo") == expectedValue

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

    private def mockWapperProperties() {
        """distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://localhost/distributions/gradle-0.0.0-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists\n"""
    }
}
