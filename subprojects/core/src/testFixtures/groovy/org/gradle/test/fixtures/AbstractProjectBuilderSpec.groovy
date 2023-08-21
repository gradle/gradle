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
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.internal.tasks.execution.DefaultTaskExecutionContext
import org.gradle.api.internal.tasks.properties.DefaultTaskProperties
import org.gradle.api.problems.Problems
import org.gradle.execution.ProjectExecutionServices
import org.gradle.execution.plan.LocalTaskNode
import org.gradle.internal.execution.BuildOutputCleanupRegistry
import org.gradle.internal.execution.WorkValidationContext
import org.gradle.internal.execution.impl.DefaultWorkValidationContext
import org.gradle.internal.properties.bean.PropertyWalker
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.ProjectBuilderImpl
import org.gradle.util.SetSystemProperties
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

import java.util.function.Predicate

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
    protected final DocumentationRegistry documentationRegistry = new DocumentationRegistry()

    // Naming the field "temporaryFolder" since that is the default field intercepted by the
    // @CleanupTestDirectory annotation.
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())

    @Rule SetSystemProperties systemProperties

    private ProjectInternal rootProject
    ProjectExecutionServices executionServices

    def setup() {
        System.setProperty("user.dir", temporaryFolder.testDirectory.absolutePath)
        // This prevents the ProjectBuilder from finding the Gradle build's root settings.gradle
        // and treating the root of the repository as the root of the build
        new File(temporaryFolder.testDirectory, "settings.gradle") << ""
        rootProject = TestUtil.createRootProject(temporaryFolder.testDirectory)
        executionServices = new ProjectExecutionServices(rootProject)
    }

    final ProjectInternal getProject() {
        assert rootProject!=null
        return rootProject
    }

    def cleanup() {
        // The root project needs to be cleaned up
        ProjectBuilderImpl.stop(project)
    }

    void execute(Task task) {
        def workValidationContext = new DefaultWorkValidationContext(WorkValidationContext.TypeOriginInspector.NO_OP, Stub(Problems))
        def taskExecutionContext = new DefaultTaskExecutionContext(
            new LocalTaskNode(task as TaskInternal, workValidationContext, { null }),
            DefaultTaskProperties.resolve(executionServices.get(PropertyWalker), executionServices.get(FileCollectionFactory), task as TaskInternal),
            workValidationContext,
            { context -> }
        )
        project.gradle.services.get(BuildOutputCleanupRegistry).resolveOutputs()
        executionServices.get(TaskExecuter).execute((TaskInternal) task, (TaskStateInternal) task.state, taskExecutionContext)
        task.state.rethrowFailure()
    }

    protected static boolean assertHasMatchingCause(Throwable t, Predicate<String> predicate) {
        if (t == null) {
            return false
        }

        def cause = t
        while (true) {
            if (predicate.test(cause.getMessage())) {
                return true
            }
            def nextCause = cause.cause
            if (nextCause == null || nextCause === cause) {
                break
            }
            cause = nextCause
        }

        return false
    }
}
