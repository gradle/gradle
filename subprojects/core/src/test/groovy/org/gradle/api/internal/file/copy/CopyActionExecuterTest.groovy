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
package org.gradle.api.internal.file.copy

import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults
import org.gradle.test.fixtures.file.WorkspaceTest
import org.gradle.util.TestUtil

class CopyActionExecuterTest extends WorkspaceTest {

    def "correctly executes copy actions, normalising and handling excludes"() {
        given:
        file("a").with {
            createFile("a")
        }
        file("b").with {
            createFile("b")
            createDir("b1").createFile("b1")
        }

        def resolver = TestFiles.resolver(testDirectory)
        def fileCollectionFactory = TestFiles.fileCollectionFactory(testDirectory)
        def copySpec = new DestinationRootCopySpec(resolver, new DefaultCopySpec(fileCollectionFactory, TestUtil.propertyFactory(), TestUtil.instantiatorFactory().decorateLenient(), TestFiles.patternSetFactory))
        copySpec.with {
            into "out"
            from "a", {
                from "b/b1", {
                    it.eachFile {
                        FileCopyDetails fcd -> fcd.exclude()
                    }
                }
            }
        }

        def action = Mock(CopyActionProcessingStreamAction)
        def workResult = true
        def copyAction = new CopyAction() {
            WorkResult execute(CopyActionProcessingStream stream) {
                stream.process(action)
                WorkResults.didWork(workResult)
            }
        }
        def executer = new CopyActionExecuter(TestUtil.instantiatorFactory().decorateLenient(), TestUtil.propertyFactory(), TestFiles.fileSystem(), false,
                TestFiles.documentationRegistry())

        when:
        executer.execute(copySpec, copyAction)

        then:
        1 * action.processFile({ it.relativePath.pathString == "a" })
        0 * action.processFile(_)
    }

    Closure path(path) {
        return { println it.relativePath.pathString; it.relativePath.pathString == path }
    }
}
