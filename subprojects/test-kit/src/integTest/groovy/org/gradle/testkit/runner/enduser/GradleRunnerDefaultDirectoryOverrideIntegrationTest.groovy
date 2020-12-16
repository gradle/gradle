/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.testkit.runner.enduser

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.testkit.runner.fixtures.NoDebug
import org.gradle.testkit.runner.fixtures.NonCrossVersion
import org.gradle.testkit.runner.internal.DefaultGradleRunner
import org.gradle.util.TextUtil
import spock.lang.IgnoreIf

@NonCrossVersion
@NoDebug
@IgnoreIf({ GradleContextualExecuter.embedded })
// These tests run builds that themselves run a build in a test worker with 'gradleTestKit()' dependency, which needs to pick up Gradle modules from a real distribution
class GradleRunnerDefaultDirectoryOverrideIntegrationTest extends BaseTestKitEndUserIntegrationTest {

    def withCustomTestKitDirectory() {
        requireIsolatedTestKitDir = true
        executer.beforeExecute {
            usingInitScript(file("testKitDirInit.gradle") << """
                allprojects {
                    tasks.withType(Test) {
                        systemProperty "$DefaultGradleRunner.TEST_KIT_DIR_SYS_PROP", "${TextUtil.normaliseFileSeparators(testKitDir.absolutePath)}"
                    }
                }
            """)
        }
    }

}
