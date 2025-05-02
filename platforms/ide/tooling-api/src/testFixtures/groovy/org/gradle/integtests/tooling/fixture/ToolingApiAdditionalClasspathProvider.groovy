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

package org.gradle.integtests.tooling.fixture

import org.gradle.integtests.fixtures.executer.GradleDistribution


/**
 * Provides TAPI client additional classpath.
 */
interface ToolingApiAdditionalClasspathProvider {

    /**
     * Additional classpath for given TAPI and target Gradle distribution to be added to the loader of the test class.
     */
    List<File> additionalClasspathFor(ToolingApiDistribution toolingApi, GradleDistribution distribution)
}
