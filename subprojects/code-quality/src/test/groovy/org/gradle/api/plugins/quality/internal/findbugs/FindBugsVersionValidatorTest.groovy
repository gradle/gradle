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

package org.gradle.api.plugins.quality.internal.findbugs

import org.gradle.api.JavaVersion
import org.gradle.util.VersionNumber
import spock.lang.Specification

class FindBugsVersionValidatorTest extends Specification {
    def "validates findbugs version in classpath"() {

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
