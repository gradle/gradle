/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class GradleBuildTaskIntegrationTest extends AbstractIntegrationSpec {
    def "handles properties which are not String when calling GradleBuild"() {
        given:
        buildFile << """
            task buildInBuild(type:GradleBuild) {
                buildFile = 'other.gradle'
                startParameter.searchUpwards = false
                startParameter.projectProperties['foo'] = true // not a String
            }
        """
        file('other.gradle') << 'assert foo==true'

        when:
        run 'buildInBuild'

        then:
        noExceptionThrown()
    }

    def "nested build can use Gradle home directory that is different to outer build"() {
        given:
        def dir = file("other-home")
        buildFile << """
            task otherBuild(type:GradleBuild) {
                buildFile = 'other.gradle'
                startParameter.searchUpwards = false
                startParameter.gradleUserHomeDir = file("${dir.toURI()}")
            }
        """

        file('other.gradle') << '''
println "user home dir: " + gradle.gradleUserHomeDir
println "build script code source: " + getClass().protectionDomain.codeSource.location
'''

        when:
        run 'otherBuild'

        then:
        output.contains("user home dir: $dir")
        output.contains("build script code source: ${dir.toURI()}")
    }
}
