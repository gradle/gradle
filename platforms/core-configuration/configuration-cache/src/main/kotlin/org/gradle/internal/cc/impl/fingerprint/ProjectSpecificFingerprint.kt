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

package org.gradle.internal.cc.impl.fingerprint

import org.gradle.util.Path


internal
sealed class ProjectSpecificFingerprint {
    data class ProjectIdentity(
        val identityPath: Path,
        val buildPath: Path,
        val projectPath: Path
    ) : ProjectSpecificFingerprint()

    data class ProjectFingerprint(
        val projectIdentityPath: Path,
        val value: ConfigurationCacheFingerprint
    ) : ProjectSpecificFingerprint()

    data class ProjectDependency(
        val consumingProject: Path,
        val targetProject: Path
    ) : ProjectSpecificFingerprint()

    data class CoupledProjects(
        val referringProject: Path,
        val targetProject: Path
    ) : ProjectSpecificFingerprint()
}
