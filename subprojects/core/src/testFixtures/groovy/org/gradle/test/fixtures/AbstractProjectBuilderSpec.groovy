/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.test.fixtures

import org.gradle.api.Task
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.internal.tasks.execution.DefaultTaskExecutionContext
import org.gradle.api.internal.tasks.properties.DefaultTaskProperties
import org.gradle.api.internal.tasks.properties.PropertyWalker
import org.gradle.execution.ProjectExecutionServices
import org.gradle.internal.execution.impl.DefaultWorkValidationContext
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

/**
 * An abstract class for writing tests using ProjectBuilder.
 * The fixture automatically takes care of deleting files creating in the temporary project directory used by the Project instance.
 * <p>
 * ProjectBuilder internally uses native services.
 * <p>
 * The project isn't available until the subclass's {@code setup()} method because initializing it before then, would mean that we
 * would create a temporary project directory which didn't know about the class and method for which it was being created.
 */
@CleanupTestDirectory
@UsesNativeServices
abstract class AbstractProjectBuilderSpec extends Specification {
    // Naming the field "temporaryFolder" since that is the default field intercepted by the
    // @CleanupTestDirectory annotation.
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())

    @Rule SetSystemProperties systemProperties

    ProjectInternal project
    ProjectExecutionServices executionServices

    def setup() {
        project = TestUtil.createRootProject(temporaryFolder.testDirectory)
        executionServices = new ProjectExecutionServices(project)
        System.setProperty("user.dir", temporaryFolder.testDirectory.absolutePath)
    }

    void execute(Task task) {
        def taskExecutionContext = new DefaultTaskExecutionContext(
            null,
            DefaultTaskProperties.resolve(executionServices.get(PropertyWalker), executionServices.get(FileCollectionFactory), task as TaskInternal),
            new DefaultWorkValidationContext(),
            {}
        )
        executionServices.get(TaskExecuter).execute((TaskInternal) task, (TaskStateInternal) task.state, taskExecutionContext)
        task.state.rethrowFailure()
    }
}
