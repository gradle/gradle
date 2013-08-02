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
package org.gradle.api.tasks

import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.test.fixtures.file.WorkspaceTest
import org.gradle.util.HelperUtil
import org.junit.Test

class AbstractCopyTaskTest extends WorkspaceTest {

    TestCopyTask task

    def setup() {
        task = HelperUtil.createTask(TestCopyTask)
    }

    void usesDefaultSourceWhenNoSourceHasBeenSpecified() {
        given:
        def defaultSource = Mock(FileTree)

        when:
        task.defaultSource = defaultSource

        then:
        task.source.is(defaultSource)
    }

    public void doesNotUseDefaultSourceWhenSourceHasBeenSpecifiedOnSpec() {
        when:
        FileTree source = Mock(FileTree)
        task.defaultSource = source
        task.from "foo"

        then:
        !task.source.is(source)
    }

    @Test
    public void copySpecMethodsDelegateToMainSpecOfCopyAction() {
        given:
        file("include") << "bar"

        expect:
        task.rootSpec.source.isEmpty()

        when:
        task.from testDirectory.absolutePath
        task.include "include"

        then:
        task.mainSpec.getIncludes() == ["include"].toSet()
        task.mainSpec.source.files == task.project.fileTree(testDirectory).files
    }

    static class TestCopyTask extends AbstractCopyTask {
        CopyAction copyAction
        FileCollection defaultSource

        protected CopyAction createCopyAction() {
            copyAction
        }

    }
}
