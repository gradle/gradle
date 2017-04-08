/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks

import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.tasks.TaskDestroys
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

import java.util.concurrent.Callable

@UsesNativeServices
class DefaultTaskDestroysTest extends Specification {
    File treeFile = new File('tree')
    def tree = [getFiles: { [treeFile] as Set}] as FileTreeInternal
    FileResolver resolver = [
        resolve: { new File(it) },
        resolveFilesAsTree: {tree}
    ] as FileResolver
    TaskMutator taskMutator = Stub(TaskMutator) {
        mutate(_, _) >> { String method, Object action ->
            if (action instanceof Runnable) {
                action.run()
            } else if (action instanceof Callable) {
                return action.call()
            }
        }
    }
    TaskDestroys taskDestroys = new DefaultTaskDestroys(resolver, Mock(TaskInternal), taskMutator)

    def "empty destroys by default"() {
        expect:
        taskDestroys.files != null
        taskDestroys.files.files.isEmpty()
    }

    def "can declare a file that a task destroys"() {
        when:
        taskDestroys.file("a")

        then:
        taskDestroys.files.files == [new File("a")] as Set
    }

    def "can declare multiple files that a task destroys"() {
        when:
        taskDestroys.files("a", "b")

        then:
        taskDestroys.files.files == [new File("a"), new File("b")] as Set
    }

    def "can declare a file collection that a task destroys"() {
        def files = [new File('a'), new File('b')] as Set
        def fileCollection = [getFiles: { files }] as FileCollectionInternal

        when:
        taskDestroys.files(fileCollection)

        then:
        taskDestroys.files.files == [new File("a"), new File("b")] as Set
    }

    def "can declare a file that a task destroys using a closure"() {
        when:
        taskDestroys.file({ 'a' })

        then:
        taskDestroys.files.files == [new File("a")] as Set
    }
}
