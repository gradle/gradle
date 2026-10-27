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

package org.gradle.testkit.runner.enduser

import org.gradle.testkit.runner.BaseGradleRunnerIntegrationTest
import org.gradle.testkit.runner.fixtures.NonCrossVersion
import org.gradle.testkit.runner.internal.DefaultGradleRunner
import org.gradle.util.internal.TextUtil
import org.gradle.util.UsesNativeServices

@NonCrossVersion
@UsesNativeServices
abstract class BaseTestKitEndUserIntegrationTest extends BaseGradleRunnerIntegrationTest {
    public static final String NOT_EMBEDDED_REASON = "These tests run builds that themselves run a build in a test worker with 'gradleTestKit()' dependency, which needs to pick up Gradle modules from a real distribution"

    def setup() {
        requireIsolatedTestKitDir = true
        executer.beforeExecute {
            usingInitScript(file("tempDirInit.gradle") << """
                allprojects {
                    tasks.withType(Test) {
                        systemProperty "$DefaultGradleRunner.TEST_KIT_DIR_SYS_PROP", "${TextUtil.normaliseFileSeparators(testKitDir.absolutePath)}"
                        systemProperty "java.io.tmpdir", "${TextUtil.normaliseFileSeparators(file("tmp").createDir().absolutePath)}"
                    }
                }
            """)
        }
    }

    def cleanup() {
        testKitDaemons().visible.each { it.stops() }
    }

}
