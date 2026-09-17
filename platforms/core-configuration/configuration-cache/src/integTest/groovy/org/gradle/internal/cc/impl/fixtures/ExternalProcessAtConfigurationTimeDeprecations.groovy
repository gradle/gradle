/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl.fixtures

import groovy.transform.SelfType
import org.gradle.integtests.fixtures.HasGradleExecutor
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter

/**
 * Apply this trait to tests that start an external process at configuration time and therefore
 * expect the "external process at configuration time" deprecation.
 *
 * The deprecation is only emitted when the configuration cache is off, so the expectation is
 * declared only under the non-configuration-cache executer, mirroring
 * {@link org.gradle.integtests.fixtures.StableConfigurationCacheDeprecations}.
 */
@SelfType(HasGradleExecutor)
trait ExternalProcessAtConfigurationTimeDeprecations {
    void expectExternalProcessAtConfigurationTimeDeprecation(int count = 1) {
        if (GradleContextualExecuter.notConfigCache) {
            count.times {
                executer.expectDocumentedDeprecationWarning("Starting an external process at configuration time has been deprecated. " +
                    "This will fail with an error in Gradle 11. " +
                    "Use ProviderFactory.exec/javaexec or a ValueSource to obtain the process output, or move the process execution into a task. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#config_time_external_processes")
            }
        }
    }
}
