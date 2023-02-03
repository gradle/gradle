/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.nativeplatform.test.cpp.plugins

import org.gradle.nativeplatform.test.AbstractNativeUnitTestIntegrationTest

abstract class AbstractCppUnitTestIntegrationTest extends AbstractNativeUnitTestIntegrationTest {
    @Override
    String getLanguageTaskSuffix() {
        return "Cpp"
    }

    @Override
    protected String getTestComponentDsl() {
        return "unitTest"
    }

    @Override
    String[] getTasksToBuildAndRunUnitTest() {
        return getTasksToBuildAndRunUnitTest(null)
    }

    @Override
    protected String[] getTasksToBuildAndRunUnitTest(String architecture) {
        def testTasks = tasks.withArchitecture(architecture).test
        return [testTasks.compile, testTasks.link, testTasks.install] + testTasks.run
    }

    @Override
    protected String[] getTasksToCompileComponentUnderTest() {
        return getTasksToCompileComponentUnderTest(null)
    }

    @Override
    protected String[] getTasksToAssembleComponentUnderTest() {
        return getTasksToAssembleComponentUnderTest(null)
    }

    @Override
    protected String[] getTasksToRelocate() {
        return getTasksToRelocate(null)
    }
}
