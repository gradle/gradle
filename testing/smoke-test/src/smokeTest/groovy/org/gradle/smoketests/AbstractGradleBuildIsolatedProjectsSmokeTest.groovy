/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.initialization.StartParameterBuildOptions

/**
 * Smoke test building gradle/gradle with isolated projects enabled.
 */
abstract class AbstractGradleBuildIsolatedProjectsSmokeTest extends AbstractGradleBuildConfigurationCacheSmokeTest {

    protected void setMaxIsolatedProjectProblems(int maxProblems) {
        maxConfigurationCacheProblems = maxProblems
    }

    void isolatedProjectsRun(List<String> tasks, int daemonId = 0) {
        def ipOptions = [
            "-D${StartParameterBuildOptions.IsolatedProjectsOption.PROPERTY_NAME}=true",
            // caching is not stable enough
            "-Dorg.gradle.internal.isolated-projects.caching=false",
            // to improve determinism
            "-Dorg.gradle.internal.isolated-projects.parallel=false",
            // we do not expect execution-time issues, so save some build time
            "--dry-run",
        ]
        configurationCacheRun(tasks + ipOptions, daemonId)
    }
}
