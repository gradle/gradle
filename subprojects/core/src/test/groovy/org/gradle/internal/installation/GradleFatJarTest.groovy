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

package org.gradle.internal.installation

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class GradleFatJarTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def jarFile = tmpDir.file('lib.jar')

    def "contains marker file for fat JAR"() {
        given:
        createJarWithMarkerFile(jarFile)

        expect:
        GradleFatJar.containsMarkerFile(jarFile)
    }

    def "does not contain marker file standard JAR file"() {
        given:
        createJarWithoutMarkerFile(jarFile)

        expect:
        !GradleFatJar.containsMarkerFile(jarFile)
    }

    private void createJarWithMarkerFile(TestFile jar) {
        handleAsJarFile(jar) { contents ->
            contents.createFile(GradleFatJar.MARKER_FILENAME)
        }
    }

    private void createJarWithoutMarkerFile(TestFile jar) {
        handleAsJarFile(jar) { contents ->
            contents.createFile('content.txt')
        }
    }

    private void handleAsJarFile(TestFile jar, Closure c = {}) {
        TestFile contents = tmpDir.createDir('contents')
        c(contents)
        contents.zipTo(jar)
    }
}
