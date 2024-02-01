/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.buildinit.plugins

abstract class AbstractJvmLibraryInitIntegrationSpec extends AbstractInitIntegrationSpec {

    @Override
    String subprojectName() { 'lib' }

    def setup() {
        executer.beforeExecute { e ->
            // The init task may not necessarily create a build that uses the current JVM, either because the test "user" selects a different one or uses the default, which may be different.
            //
            // Enable discovery and download so the test builds can find the correct JVM, and to better approximate what a real user would experience, which is important in this
            // particular case because the init task may be the first experience a developer has of Gradle
            e.withToolchainDetectionEnabled()
            e.withToolchainDownloadEnabled()
        }
    }
}
