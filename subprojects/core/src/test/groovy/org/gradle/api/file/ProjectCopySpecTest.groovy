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

import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.test.fixtures.file.TestFile

class ProjectCopySpecTest extends AbstractProjectBuilderSpec {

    TestFile getCopySource() {
        temporaryFolder.testDirectory.createDir("source")
    }

    TestFile getCopyDest() {
        temporaryFolder.testDirectory.createDir("dest")
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
            delegate.duplicatesStrategy = "include"
            from copySource

            from copySource, {
                delegate.duplicatesStrategy = "include"
                delegate.eachFile {
                    copySpecNestedEachFileCalled = true
                    delegate.duplicatesStrategy = "include"
                }
            }

            eachFile {
                copySpecEachFileCalled = true
                delegate.duplicatesStrategy = "include"
            }
        }

        expect:
        project.copy {
            copyRootCalled = true
            into copyDest
            with copySpec
            from copySource
            from copySource, {
                delegate.duplicatesStrategy = "include"
                eachFile {
                    copyNestedEachFileCalled = true
                    delegate.duplicatesStrategy = "include"
                }
            }
            delegate.duplicatesStrategy = "include"

            eachFile {
                delegate.duplicatesStrategy = "include"
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
