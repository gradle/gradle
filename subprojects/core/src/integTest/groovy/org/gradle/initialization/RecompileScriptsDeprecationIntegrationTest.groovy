/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.initialization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

@Issue('https://github.com/gradle/gradle/issues/1425')
class RecompileScriptsDeprecationIntegrationTest extends AbstractIntegrationSpec {
    def "deprecation warning appears when using --recompile-scripts"() {
        given:
        buildFile << '''
task hello { 
    doLast { 
        println "hello" 
    } 
}'''

        when:
        executer.expectDeprecationWarning().requireGradleDistribution()
        args('--recompile-scripts')

        then:
        succeeds('hello')
        outputContains(StartParameterBuildOptions.RecompileScriptsOption.DEPRECATION_MESSAGE)
    }
}
