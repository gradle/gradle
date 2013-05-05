/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.openapi.external

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

public class ExternalUtilityTest extends Specification {
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();

    def getTheRightGradleCore() {
        TestFile libDir = tmpDir.testDirectory.file('lib')

        when:
        libDir.deleteDir()
        libDir.createDir().files('gradle-core-0.9.jar', 'gradle-core-worker-0.9.jar', 'gradle-open-api-0.9.jar')*.touch()

        then:
        ExternalUtility.getGradleJar(tmpDir.testDirectory).absolutePath == new File(libDir, 'gradle-core-0.9.jar').absolutePath

        when:
        libDir.deleteDir()
        libDir.createDir().files('gradle-core-0.9-20100315080959+0100.jar', 'gradle-core-worker-0.9-20100315080959+0100.jar', 'gradle-open-api-0.9-20100315080959+0100.jar')*.touch()

        then:
        ExternalUtility.getGradleJar(tmpDir.testDirectory).absolutePath == new File(libDir, 'gradle-core-0.9-20100315080959+0100.jar').absolutePath
    }

    def failWithMultipleGradleCore() {
        tmpDir.testDirectory.file('lib').createDir().files('gradle-core-0.9.jar', 'gradle-core-0.10.jar', 'gradle-open-api-0.9.jar')*.touch()

        when:
        ExternalUtility.getGradleJar(tmpDir.testDirectory)

        then:
        RuntimeException e = thrown()
        println e.message
        e.message.contains('gradle-core-0.9.jar')
        e.message.contains('gradle-core-0.10.jar')
    }

    def returnNullWitNonExistingGradleCore() {
        tmpDir.testDirectory.file('lib').createDir().files('gradle-open-api-0.9.jar')*.touch()

        expect:
        ExternalUtility.getGradleJar(tmpDir.testDirectory) == null
    }

    def failWitNonExistingGradleHome() {
        expect:
        ExternalUtility.getGradleJar(tmpDir.testDirectory) == null
    }

}
