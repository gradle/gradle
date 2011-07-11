/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.integtests.tooling.m4

import org.gradle.integtests.tooling.fixture.ToolingApiCompatibilitySuite
import org.gradle.integtests.fixtures.BasicGradleDistribution
import org.gradle.util.GradleVersion

/**
 * @author: Szczepan Faber, created at: 6/29/11
 */
class ToolingApiSuite extends ToolingApiCompatibilitySuite {
    @Override
    boolean accept(BasicGradleDistribution toolingApi, BasicGradleDistribution gradle) {
        def m3 = GradleVersion.version('1.0-milestone-3')
        return GradleVersion.version(toolingApi.version) > m3 && GradleVersion.version(gradle.version) > m3
    }

    @Override
    List<Class<?>> getClasses() {
        return [ToolingApiEclipseLinkedResourcesIntegrationTest,
                ToolingApiMinimalModelFixesTest,
                ToolingApiTaskExecutionFixesTest]
    }
}