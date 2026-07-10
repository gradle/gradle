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

package org.gradle.performance.fixture

import groovy.transform.CompileStatic

@CompileStatic
interface InvocationSpec {

    List<String> getTasksToRun()

    List<String> getJvmArguments()

    List<String> getArgs()

    /**
     * A directory for any files associated with the Gradle Profiler invocation.
     * <p>
     * The project-under-test will be checked out into a {@link #getProjectCheckoutDirectory() subdirectory}.
     * Other necessary files can be stored in this working directory or other dedicated subdirectories.
     */
    File getWorkingDirectory()

    /**
     * A directory under the working directory, into which the project-under-test will be checked out.
     * <p>
     * This directory should contain only the project-under-test files.
     * Everything else, like dedicated Gradle User Home or profiler logs, should live in the working directory
     * to avoid artificially incurred VFS overhead and indexing by IDE in the sync tests.
     */
    File getProjectCheckoutDirectory()

    boolean isExpectFailure()

    interface Builder {
        File getWorkingDirectory()

        void setWorkingDirectory(File workingDirectory)

        Builder expectFailure()
    }
}
