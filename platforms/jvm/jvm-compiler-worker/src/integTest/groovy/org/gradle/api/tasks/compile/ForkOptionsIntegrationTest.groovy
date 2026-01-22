/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.tasks.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ForkOptionsIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        javaFile('src/Dummy.java', 'class Dummy {}')
        buildFile << """
            tasks.register('compileJava', JavaCompile) {
                sourceCompatibility = '1.8'
                targetCompatibility = '1.8'
                classpath = files()
                source = 'src'
                destinationDirectory = layout.buildDirectory.dir('classes')
            }
        """
    }

    def "a property of the forked compiler process can be configured with a 'blank' value"() {
        given:
        buildFile << """
            tasks.withType(JavaCompile).configureEach {
                options.fork = true
                options.forkOptions.jvmArgs = ['-Dline.separator=\\n', '-XshowSettings:properties']
            }
        """

        when:
        succeeds 'compileJava'

        then:
        result.error.contains 'line.separator = \\n'
    }
}
