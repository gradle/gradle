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

package org.gradle.integtests.tooling.fixture


import groovy.transform.SelfType
import org.gradle.util.GradleVersion

@SelfType(ToolingApiSpecification)
trait WithOldConfigurationsSupport {
    private static final GradleVersion V6_9 = GradleVersion.version("6.9")

    private GradleVersion baseVersion() {
        // This weird dance is because of the `GradleVersion` comes from a different classloader
        GradleVersion.version(targetDist.version.baseVersion.version)
    }

    String getImplementationConfiguration() {
        baseVersion() >= V6_9 ? "implementation" : "compile"
    }

    String getRuntimeConfiguration() {
        baseVersion() >= V6_9 ? "runtimeOnly" : "runtime"
    }

    String getTestImplementationConfiguration() {
        "test${implementationConfiguration.capitalize()}"
    }

    String getTestRuntimeConfiguration() {
        "test${runtimeConfiguration.capitalize()}"
    }
}
