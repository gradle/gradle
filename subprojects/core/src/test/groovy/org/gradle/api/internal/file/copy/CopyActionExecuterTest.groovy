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

import org.gradle.api.Action
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.file.BaseDirFileResolver
import org.gradle.api.internal.tasks.SimpleWorkResult
import org.gradle.api.tasks.WorkResult
import org.gradle.internal.nativeplatform.filesystem.FileSystems
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.test.fixtures.file.WorkspaceTest

class CopyActionExecuterTest extends WorkspaceTest {

    def "can execute test"() {
        given:
        file("a").with {
            createFile("a")
        }
        file("b").with {
            createFile("b")
            createDir("b1").createFile("b1")
        }

        def resolver = new BaseDirFileResolver(testDirectory)
        def copySpec = new DestinationRootCopySpec(resolver, new DefaultCopySpec(resolver, new DirectInstantiator()))
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

        Action<FileCopyDetailsInternal> action = Mock(Action)
        def workResult = true
        def copyAction = new CopyAction() {
            WorkResult execute(Action<Action<? super FileCopyDetailsInternal>> stream) {
                stream.execute(action)
                new SimpleWorkResult(workResult)
            }
        }
        def executer = new CopyActionExecuter(new DirectInstantiator(), FileSystems.getDefault())

        when:
        executer.execute(copySpec, copyAction)

        then:
        1 * action.execute({ it.relativePath.pathString == "a" })
        0 * action.execute(_)
    }

    Closure path(path) {
        return { println it.relativePath.pathString; it.relativePath.pathString == path }
    }
}
