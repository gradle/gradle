/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.plugins.quality.internal.findbugs

import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.util.VersionNumber
import spock.lang.Specification

import static org.gradle.api.plugins.quality.internal.findbugs.FindBugsClasspathValidator.FindBugsVersionTooLowException

class FindBugsClasspathValidatorTest extends Specification {

    def newVer = ['foo.jar', 'findbugs-3.0.0.jar']
    def oldVer = ['foo.jar', 'findbugs-2.0.3.jar']

    def "validates"() {
        expect:
        new FindBugsClasspathValidator(JavaVersion.VERSION_1_5).validateClasspath(oldVer)
        new FindBugsClasspathValidator(JavaVersion.VERSION_1_6).validateClasspath(oldVer)
        new FindBugsClasspathValidator(JavaVersion.VERSION_1_7).validateClasspath(oldVer)
        new FindBugsClasspathValidator(JavaVersion.VERSION_1_7).validateClasspath(newVer)
        new FindBugsClasspathValidator(JavaVersion.VERSION_1_8).validateClasspath(newVer)
        new FindBugsClasspathValidator(JavaVersion.VERSION_1_9).validateClasspath(newVer)
    }

    def "reports newer version of findbugs required"() {
        when: new FindBugsClasspathValidator(JavaVersion.VERSION_1_8).validateClasspath(oldVer)
        then: thrown(FindBugsVersionTooLowException)

        when: new FindBugsClasspathValidator(JavaVersion.VERSION_1_9).validateClasspath(oldVer)
        then: thrown(FindBugsVersionTooLowException)
    }

    def "requires findbugs jar in classpath"() {
        when:
        new FindBugsClasspathValidator(JavaVersion.current()).validateClasspath(['foo.jar'])
        then:
        def ex = thrown(GradleException)
        ex.message.contains('foo.jar')
    }

    def "finds correct findbugs version in classpath"() {

        when:
        def version = new FindBugsClasspathValidator(JavaVersion.VERSION_1_8).getFindbugsVersion(classpath)

        then:
        version.equals(VersionNumber.parse(expectedVersion))

        where:
        expectedVersion << ['3.0.1', '3.0.0', '2.0.3', '1.3.9']

        classpath << [
            ['findbugs-jFormatString-1.3.9.jar', 'asm-5.0.4.jar', 'findbugs-annotations-2.0.0.jar', 'findbugs-3.0.1.jar'],
            ['asm-5.0.4.jar', 'findbugs-annotations-3.0.1.jar', 'findbugs-3.0.0.jar', 'findbugs-jFormatString-2.0.3.jar'],
            ['findbugs-2.0.3.jar', 'findbugs-annotations-1.3.9.jar', 'findbugs-jFormatString-3.0.1.jar', 'asm-5.0.4.jar'],
            ['findbugs-annotations-2.0.3.jar', 'findbugs-1.3.9.jar', 'asm-5.0.4.jar', 'findbugs-jFormatString-3.0.0.jar']
        ]
    }
}
