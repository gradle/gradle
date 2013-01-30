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
package org.gradle.api.plugins.sonar.runner

import spock.lang.Specification

class SonarRunnerExtensionTest extends Specification {
    def extension = new SonarRunnerExtension()

    def "declare multiple configuration blocks"() {
        def x = 2

        when:
        extension.sonarProperties {
            x *= 3
        }
        extension.sonarProperties {
            x *= 5
        }

        then:
        extension.sonarPropertiesBlocks.size() == 2

        when:
        extension.sonarPropertiesBlocks*.call()

        then:
        x == 30
    }
}
