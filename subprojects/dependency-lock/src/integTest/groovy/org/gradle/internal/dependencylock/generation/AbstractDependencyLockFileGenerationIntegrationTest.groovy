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

package org.gradle.internal.dependencylock.generation

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

abstract class AbstractDependencyLockFileGenerationIntegrationTest extends AbstractIntegrationSpec {

    public static final String MYCONF_CUSTOM_CONFIGURATION = 'myConf'

    TestFile lockFile
    TestFile sha1File

    def setup() {
        lockFile = file('gradle/dependencies.lock')
        sha1File = file('gradle/dependencies.lock.sha1')
    }

    protected void succeedsWithEnabledDependencyLocking(String... tasks) {
        withEnabledDependencyLocking()
        succeeds(tasks)
    }

    protected void failsWithEnabledDependencyLocking(String... tasks) {
        withEnabledDependencyLocking()
        fails(tasks)
    }

    private void withEnabledDependencyLocking() {
        args("--$StartParameterBuildOptions.DependencyLockOption.LONG_OPTION")
    }
}
