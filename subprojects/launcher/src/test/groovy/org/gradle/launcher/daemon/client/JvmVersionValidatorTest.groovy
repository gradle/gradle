/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.launcher.daemon.client

import org.gradle.api.JavaVersion
import spock.lang.Specification

class JvmVersionValidatorTest extends Specification {

    def "can parse version number"() {
        expect:
        JvmVersionValidator.parseJavaVersionCommandOutput(new BufferedReader(new StringReader(output))) == JavaVersion.toVersion("1.5")

        where:
        output << [
                'java version "1.5"',
                'java version "1.5"\ntrailers',
                'headers\njava version "1.5"',
                'java version "1.5"\r\ntrailers',
                'headers\r\njava version "1.5"',
        ]
    }

    def "can parse version number for Java 8 "() {
        expect:
        JvmVersionValidator.parseJavaVersionCommandOutput(new BufferedReader(new StringReader(output))) == JavaVersion.toVersion("1.8")

        where:
        output << [
                'java version "1.8.0_11"',
                'openjdk version "1.8.0-internal"',
        ]
    }

    def "fails to parse version number"() {
        when:
        JvmVersionValidator.parseJavaVersionCommandOutput(new BufferedReader(new StringReader(output)))

        then:
        thrown RuntimeException

        where:
        output << [
                '',
                '\n',
                'foo',
                'foo\nbar',
                'foo\r\nbar'
        ]
    }
}
