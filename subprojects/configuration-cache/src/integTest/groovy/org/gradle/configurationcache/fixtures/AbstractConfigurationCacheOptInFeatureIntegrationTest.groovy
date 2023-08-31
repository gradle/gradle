/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache.fixtures

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheProblemsFixture

abstract class AbstractConfigurationCacheOptInFeatureIntegrationTest extends AbstractIntegrationSpec {
    static final String WARN_PROBLEMS_CLI_OPT = "--${StartParameterBuildOptions.ConfigurationCacheProblemsOption.LONG_OPTION}=warn"

    protected ConfigurationCacheProblemsFixture problems

    def setup() {
        // Verify that the previous test cleaned up state correctly
        assert System.getProperty(StartParameterBuildOptions.ConfigurationCacheOption.PROPERTY_NAME) == null
        assert System.getProperty(StartParameterBuildOptions.IsolatedProjectsOption.PROPERTY_NAME) == null
        problems = new ConfigurationCacheProblemsFixture(executer, testDirectory)
    }

    def cleanup() {
        // Verify that the test (or fixtures) has cleaned up state correctly
        assert System.getProperty(StartParameterBuildOptions.ConfigurationCacheOption.PROPERTY_NAME) == null
        assert System.getProperty(StartParameterBuildOptions.IsolatedProjectsOption.PROPERTY_NAME) == null
    }

    abstract void configurationCacheRun(String... tasks)

    abstract void configurationCacheRunLenient(String... tasks)

    abstract void configurationCacheFails(String... tasks)
}
