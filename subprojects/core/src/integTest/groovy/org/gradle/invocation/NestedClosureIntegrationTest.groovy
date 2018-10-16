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

package org.gradle.invocation

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class NestedClosureIntegrationTest extends AbstractIntegrationSpec {

    @Issue('https://github.com/gradle/gradle/issues/2888')
    def 'can handle nested closure during initialization'() {
        given:
        settingsFile << '''
gradle.projectsLoaded { g ->
    println 'projectsLoaded' 
    g.rootProject {
        println 'rootProject'
        beforeEvaluate { project ->
            println 'beforeEvaluate'
        }
    }
}
'''
        when:
        succeeds('help')

        then:
        output.count('projectsLoaded') == 1
        output.count('rootProject') == 1
        output.count('beforeEvaluate\n') == 1
    }
}
