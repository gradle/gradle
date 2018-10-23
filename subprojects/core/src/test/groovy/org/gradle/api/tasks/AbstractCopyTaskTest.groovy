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

import com.google.common.collect.ImmutableSortedSet
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec
import org.gradle.api.internal.tasks.execution.TaskProperties
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.WorkspaceTest
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices

@UsesNativeServices
class AbstractCopyTaskTest extends WorkspaceTest {

    def taskPropertiesWithOutput = Mock(TaskProperties) {
        getOutputFileProperties() >> ImmutableSortedSet.of(Mock(TaskOutputFilePropertySpec))
        hasDeclaredOutputs() >> true
    }
    TestCopyTask task
    TestFile projectDir

    def setup() {
        projectDir = file("project").createDir()
        def project = (ProjectInternal) ProjectBuilder.builder()
            .withProjectDir(projectDir)
            .withGradleUserHomeDir(file("userHome").createDir())
            .build()
        task = TestUtil.createTask(TestCopyTask, project)
    }

    def "copy spec methods delegate to main spec of copy action"() {
        given:
        projectDir.file("include") << "bar"

        when:
        task.from projectDir.absolutePath
        task.include "include"

        then:
        task.mainSpec.getIncludes() == ["include"].toSet()
        task.mainSpec.buildRootResolver().source.files == task.project.fileTree(projectDir).files
    }

    static class TestCopyTask extends AbstractCopyTask {
        CopyAction copyAction

        protected CopyAction createCopyAction() {
            copyAction
        }

        @OutputDirectory
        File getDestinationDir() {
            project.file("dest")
        }
    }
}
