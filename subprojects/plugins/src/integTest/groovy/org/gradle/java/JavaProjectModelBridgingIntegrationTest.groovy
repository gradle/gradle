/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.java

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JavaProjectModelBridgingIntegrationTest extends AbstractIntegrationSpec {
    def "rule can configure source set input and output directories using the associated ClassDirectoryBinary"() {
        buildFile << """
plugins {
    id 'java'
}

model {
    binaries.mainClasses {
        inputs.withType(JavaSourceSet) { source.srcDirs = ['src'] }
        inputs.withType(JvmResourceSet) { source.srcDirs = ['res'] }
        classesDir = file('out/classes')
        resourcesDir = classesDir
    }
}
"""

        file('src/Thing.java') << "class Thing { }"
        file('res/file.txt') << "resource"

        when:
        run("classes")

        then:
        file('build/classes').assertDoesNotExist()
        file('build/resources').assertDoesNotExist()
        file('out/classes/Thing.class').assertIsFile()
        file('out/classes/file.txt').assertIsFile()
    }
}
