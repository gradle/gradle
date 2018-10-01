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

package org.gradle.groovy.scripts

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

@Issue(['https://github.com/gradle/gradle/issues/1793', 'https://github.com/gradle/gradle/issues/6286'])
class GradleUtilImportIntegrationTest extends AbstractIntegrationSpec {

    def "no deprecation warning with #importStatement and #className"() {
        given:
        buildFile << """
${importStatement}
task noop{
    doLast {
        ${className}.current() 
    }
}
"""
        expect:
        succeeds('noop')

        where:
        importStatement                        | className
        ''                                     | 'org.gradle.util.GradleVersion'
        'import org.gradle.util.GradleVersion' | 'GradleVersion'
    }

    def "cannot use org.gradle.util class without explicit import"() {
        buildFile << '''
task noop{
    doLast {
        GradleVersion.current() 
    }
}
'''
        when:
        fails('noop')

        then:
        failureCauseContains("unknown property 'GradleVersion'")
    }
}
