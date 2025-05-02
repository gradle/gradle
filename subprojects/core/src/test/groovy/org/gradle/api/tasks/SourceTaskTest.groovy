/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.tasks

class SourceTaskTest extends AbstractTaskTest {
    private SourceTask task

    SourceTask getTask() {
        return task
    }

    def setup() {
        task = createTask(SourceTask.class)
    }

    def "can append to source"() {
        given:
        File file1 = temporaryFolder.file('file1.txt').createFile()
        File file2 = temporaryFolder.file('file2.txt').createFile()
        file2.createNewFile()

        task.source = file1
        task.source = task.source + project.layout.files(file2)

        expect:
        task.source.asList() == [file1, file2]
    }
}

