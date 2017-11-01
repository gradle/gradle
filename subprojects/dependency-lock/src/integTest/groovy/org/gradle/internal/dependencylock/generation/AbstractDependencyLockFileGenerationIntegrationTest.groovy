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

import groovy.json.JsonSlurper
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.dependencylock.fixtures.LockFile
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.initialization.StartParameterBuildOptions.DependencyLockOption

abstract class AbstractDependencyLockFileGenerationIntegrationTest extends AbstractIntegrationSpec {

    public static final String ROOT_PROJECT_PATH = ':'
    public static final String MYCONF_CONFIGURATION_NAME = 'myConf'

    TestFile lockFile
    TestFile sha1File

    def setup() {
        lockFile = file('gradle/dependencies.lock')
        sha1File = file('gradle/dependencies.lock.sha1')
    }

    protected void succeedsWithEnabledDependencyLocking(String... tasks) {
        withEnabledDependencyLockingCommandLineOption()
        succeeds(tasks)
    }

    protected void failsWithEnabledDependencyLocking(String... tasks) {
        withEnabledDependencyLockingCommandLineOption()
        fails(tasks)
    }

    protected void withEnabledDependencyLockingCommandLineOption() {
        args("--$DependencyLockOption.LONG_OPTION")
    }

    protected void withDisabledDependencyLockingCommandLineOption() {
        args("--no-$DependencyLockOption.LONG_OPTION")
    }

    protected void assertLockFileAndHashFileExist() {
        assert lockFile.exists()
        assert sha1File.exists()
    }

    protected void assertLockFileAndHashFileDoNotExist() {
        assert !lockFile.exists()
        assert !sha1File.exists()
    }

    protected LockFile parseLockFile() {
        new LockFile(new JsonSlurper().parse(lockFile))
    }
}
