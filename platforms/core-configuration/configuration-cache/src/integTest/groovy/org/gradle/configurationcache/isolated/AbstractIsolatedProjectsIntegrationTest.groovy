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

package org.gradle.configurationcache.isolated

import org.gradle.configurationcache.fixtures.AbstractConfigurationCacheOptInFeatureIntegrationTest

import static org.gradle.initialization.StartParameterBuildOptions.IsolatedProjectsOption.PROPERTY_NAME

abstract class AbstractIsolatedProjectsIntegrationTest extends AbstractConfigurationCacheOptInFeatureIntegrationTest {
    public static final String ENABLE_CLI = "-D${PROPERTY_NAME}=true"
    final def fixture = new IsolatedProjectsFixture(this)

    void isolatedProjectsRun(String... tasks) {
        run(ENABLE_CLI, *tasks)
    }

    void isolatedProjectsFails(String... tasks) {
        fails(ENABLE_CLI, *tasks)
    }
}
