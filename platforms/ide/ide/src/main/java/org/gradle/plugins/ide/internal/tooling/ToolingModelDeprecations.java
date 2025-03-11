/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling;

import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.deprecation.DeprecationLogger;

public class ToolingModelDeprecations {

    public static void nagOnNonRootTarget(Project target, Project root) {
        if (((ProjectInternal) target).getProjectIdentity().equals(((ProjectInternal) root).getProjectIdentity())) {
            return;
        }

        DeprecationLogger.deprecateBehaviour("Querying build-scoped models with non-root project target.")
            .withContext("This model is always built for the root project regardless of the provided target.")
            .withAdvice("Use root project as the target.")
            .willBecomeAnErrorInGradle9()
            .withUpgradeGuideSection(8, "non_root_tooling_model_targets")
            .nagUser();
    }
}
