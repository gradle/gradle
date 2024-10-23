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

package org.gradle.integtests.fixtures.executer

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.util.GradleVersion

class IsolatedProjectsGradleExecuter extends DaemonGradleExecuter {

    static final List<String> ISOLATED_PROJECTS_ARGS = [
        "-D${StartParameterBuildOptions.IsolatedProjectsOption.PROPERTY_NAME}=true",
        "-D${StartParameterBuildOptions.ConfigurationCacheMaxProblemsOption.PROPERTY_NAME}=0",
    ].collect { it.toString() }

    IsolatedProjectsGradleExecuter(
        GradleDistribution distribution,
        TestDirectoryProvider testDirectoryProvider,
        GradleVersion gradleVersion,
        IntegrationTestBuildContext buildContext
    ) {
        super(distribution, testDirectoryProvider, gradleVersion, buildContext)
    }

    @Override
    protected List<String> getAllArgs() {
        def args = super.getAllArgs()
        // Don't enable if CC is disabled
        if (args.contains("--no-configuration-cache")) {
            return args
        }
        // Don't enable if IP explicitly disabled
        if (args.contains("-D${StartParameterBuildOptions.IsolatedProjectsOption.PROPERTY_NAME}=false")) {
            return args
        }
        return args + ISOLATED_PROJECTS_ARGS
    }
}
