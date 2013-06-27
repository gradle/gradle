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

package org.gradle.api.file

import org.gradle.api.Project
import org.gradle.api.internal.DynamicObjectAware
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import spock.lang.Specification

class ProjectCopySpecTest extends Specification {

    Project project

    @Rule TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()

    def setup() {
        project = ProjectBuilder.builder().withProjectDir(testDirectoryProvider.testDirectory).build()
    }

    TestFile getCopySource() {
        testDirectoryProvider.testDirectory.createDir("source")
    }

    TestFile getCopyDest() {
        testDirectoryProvider.testDirectory.createDir("dest")
    }

    boolean isEnhanced(obj) {
        assert obj instanceof DynamicObjectAware
        true
    }

    def "copy spec is enhanced"() {
        given:
        def copySpecRootCalled = false
        def copySpecEachFileCalled = false
        def copySpecNestedEachFileCalled = false
        def copyRootCalled = false
        def copyEachFileCalled = false
        def copyNestedEachFileCalled = false

        copySource.createFile("file")
        def copySpec = project.copySpec {
            copySpecRootCalled = true
            isEnhanced delegate
            from copySource

            from copySource, {
                eachFile {
                    copySpecNestedEachFileCalled = true
                    isEnhanced delegate
                }
            }

            eachFile {
                copySpecEachFileCalled = true
                isEnhanced delegate
            }
        }

        expect:
        isEnhanced copySpec
        project.copy {
            copyRootCalled = true
            into copyDest
            with copySpec
            from copySource
            from copySource, {
                eachFile {
                    copyNestedEachFileCalled = true
                    isEnhanced delegate
                }
            }
            isEnhanced delegate

            eachFile {
                isEnhanced delegate
                copyEachFileCalled = true
            }
        }

        and:
        copyRootCalled
        copyEachFileCalled
        copyNestedEachFileCalled
        copySpecRootCalled
        copySpecEachFileCalled
        copySpecNestedEachFileCalled
    }

}
