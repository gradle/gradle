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

import com.google.common.base.Joiner
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

@Issue('https://github.com/gradle/gradle/issues/1793')
class GradleUtilDefaultImportDeprecationIntegrationTest extends AbstractIntegrationSpec {

    def warning(String... names) {
        return "You're using ${Joiner.on(",").join(names as HashSet)} from org.gradle.util internal package. Default imports was deprecated now. You should either remove the usage (recommended) or import them explicitly (not recommended)"
    }

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
        when:
        succeeds('noop')

        then:
        !output.contains("from org.gradle.util internal package which was deprecated now.")

        where:
        importStatement                        | className
        ''                                     | 'org.gradle.util.GradleVersion'
        'import org.gradle.util.GradleVersion' | 'GradleVersion'
    }

    def "deprecation warning when using org.gradle.util class without explicit import"() {
        buildFile << '''
task noop{
    doLast {
        GradleVersion.current() 
    }
}
'''
        when:
        executer.expectDeprecationWarning()
        succeeds('noop')

        then:
        outputContains(warning('GradleVersion'))
    }

    def "multiple implicit imports will only be warned once"() {
        buildFile << '''
task noop{
    doLast {
        GradleVersion.current() 
        CollectionUtils.toList([])
    }
}
'''
        when:
        executer.expectDeprecationWarning()
        succeeds('noop')

        then:
        outputContains(warning('GradleVersion', 'CollectionUtils'))
    }
}
