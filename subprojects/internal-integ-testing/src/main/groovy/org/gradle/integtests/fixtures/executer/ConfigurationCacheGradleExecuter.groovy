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

package org.gradle.integtests.fixtures.executer

import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheMaxProblemsOption
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheQuietOption
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.util.GradleVersion


class ConfigurationCacheGradleExecuter extends DaemonGradleExecuter {

    static final List<String> CONFIGURATION_CACHE_ARGS = [
        "--${ConfigurationCacheOption.LONG_OPTION}",
        "-D${ConfigurationCacheQuietOption.PROPERTY_NAME}=true",
        "-D${ConfigurationCacheMaxProblemsOption.PROPERTY_NAME}=0",
        "-Dorg.gradle.configuration-cache.internal.load-after-store=${testWithLoadAfterStore()}"
    ].collect { it.toString() }

    static boolean testWithLoadAfterStore() {
        return !System.getProperty("org.gradle.configuration-cache.internal.test-disable-load-after-store")
    }

    ConfigurationCacheGradleExecuter(
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
        if (args.contains("--no-configuration-cache")) { // Don't enable if explicitly disabled
            return args
        } else {
            return args + CONFIGURATION_CACHE_ARGS
        }
    }
}
