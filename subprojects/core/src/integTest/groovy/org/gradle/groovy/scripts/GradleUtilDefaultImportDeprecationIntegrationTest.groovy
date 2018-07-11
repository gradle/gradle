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

@Issue('https://github.com/gradle/gradle/issues/1793')
class GradleUtilDefaultImportDeprecationIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.requireOwnGradleUserHomeDir()
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
        expect:
        succeeds('noop')

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
        outputContains("build.gradle' is using GradleVersion from the private org.gradle.util package without an explicit import. The support for implicit import of internal classes is deprecated and will be removed in Gradle 5.0. Please either stop using these internal classes (recommended) or import them explicitly at the top of your build file.")
    }

    def "deprecation warning points to the offending file"() {
        file("foo.gradle") << '''
task noop{
    doLast {
        GradleVersion.current() 
    }
}
'''
        buildFile << "apply from: 'foo.gradle'"
        when:
        executer.expectDeprecationWarning()
        succeeds('noop')

        then:
        outputContains("foo.gradle' is using GradleVersion from the private org.gradle.util package without an explicit import. The support for implicit import of internal classes is deprecated and will be removed in Gradle 5.0. Please either stop using these internal classes (recommended) or import them explicitly at the top of your build file.")
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
        outputContains("build.gradle' is using CollectionUtils and GradleVersion from the private org.gradle.util package without an explicit import. The support for implicit import of internal classes is deprecated and will be removed in Gradle 5.0. Please either stop using these internal classes (recommended) or import them explicitly at the top of your build file.")
    }
}
