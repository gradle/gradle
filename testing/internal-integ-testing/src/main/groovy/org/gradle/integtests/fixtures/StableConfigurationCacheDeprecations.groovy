/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.fixtures

import groovy.transform.SelfType
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter

/**
 * Apply this trait to tests that may need to expect common deprecations,
 * typically those that used to be behind the STABLE_CONFIGURATION_CACHE feature flag.
 */
@SelfType(HasGradleExecutor)
trait StableConfigurationCacheDeprecations {
    void expectTaskGetProjectDeprecations(int count = 1) {
        if (GradleContextualExecuter.notConfigCache) {
            count.times {
                executer.expectDocumentedDeprecationWarning("Invocation of Task.project at execution time has been deprecated. " +
                    "This will fail with an error in Gradle 9.0. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#task_project")
            }
        }
    }
}
