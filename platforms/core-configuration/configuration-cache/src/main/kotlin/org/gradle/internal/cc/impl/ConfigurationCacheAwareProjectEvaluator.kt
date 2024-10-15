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

package org.gradle.internal.cc.impl

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateInternal
import org.gradle.configuration.project.ProjectEvaluator
import org.gradle.internal.cc.impl.fingerprint.ConfigurationCacheFingerprintController


internal
class ConfigurationCacheAwareProjectEvaluator(
    private val delegate: ProjectEvaluator,
    private val controller: ConfigurationCacheFingerprintController
) : ProjectEvaluator {
    override fun evaluate(project: ProjectInternal, state: ProjectStateInternal) {
        controller.runCollectingFingerprintForProject(ProjectIdentityPath(project.identityPath, project.buildPath, project.projectPath)) {
            delegate.evaluate(project, state)
        }
    }
}
