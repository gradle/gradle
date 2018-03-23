/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import org.gradle.test.fixtures.file.TestFile

import java.nio.file.Files

class OutputDirectoryContainingDanglingBrokenSymbolicLinkUpToDateIntegrationTest extends AbstractBrokenSymbolicLinkUpToDateIntegrationTest {
    @Override
    void makeScenarioProject() {
        buildFile << """
            task checkCreated {
                outputs.dir file('outputs')
                doLast {
                    ${Files.canonicalName}.deleteIfExists(new File('${symbolicLinkUnderTest.absolutePath}').toPath())
                    ${Files.canonicalName}.createSymbolicLink(new File('${symbolicLinkUnderTest.absolutePath}').toPath(), new File('${targetFile.absolutePath}').toPath())
                }
            }
        """
    }

    @Override
    TestFile getSymbolicLinkUnderTest() {
        return file('outputs/sym-link')
    }

    @Override
    TestFile getTargetFile() {
        return file('some-missing-file-system-element')
    }

    @Override
    TestFile getAlternateTargetFile() {
        return file('some-other-missing-file-system-element')
    }

    @Override
    TestFile getOutputFileToClean() {
        return symbolicLinkUnderTest.parentFile
    }

    @Override
    String getCachingDisabledMessage() {
        return "Could not pack property '\$1': Symbolic link is broken: ${symbolicLinkUnderTest.absolutePath}"
    }
}
