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

package org.gradle.api.tasks.diagnostics.internal;

import org.gradle.api.Incubating;
import org.gradle.api.plugins.HelpTasksPlugin;

/**
 * The names of the diagnostic tasks added by the {@link HelpTasksPlugin} and the
 * {@code SoftwareReportingTasksPlugin}.
 *
 * @since 8.13
 */
@Incubating
public interface DiagnosticsTaskNames {
    /**
     * The name of the help task group.
     *
     * @since 8.13
     */
    String HELP_GROUP = "help";

    /**
     * The name of the properties report task.
     *
     * @since 8.13
     */
    String PROPERTIES_TASK = "properties";

    /**
     * The name of the dependencies report task.
     *
     * @since 8.13
     */
    String DEPENDENCIES_TASK = "dependencies";

    /**
     * The name of the dependency insight report task.
     *
     * @since 8.13
     */
    String DEPENDENCY_INSIGHT_TASK = "dependencyInsight";

    /**
     * The name of the components report task.
     *
     * @since 8.13
     */
    String COMPONENTS_TASK = "components";

    /**
     * The name of the outgoing variants report task.
     *
     * @since 8.13
     */
    String OUTGOING_VARIANTS_TASK = "outgoingVariants";

    /**
     * The name of the requested configurations report task.
     *
     * @since 8.13
     */
    @Incubating // TODO: We should probably de-incubate this for Gradle 9.0
    String RESOLVABLE_CONFIGURATIONS_TASK = "resolvableConfigurations";

    /**
     * The name of the Artifact Transforms report task.
     *
     * @since 8.13
     */
    @Incubating
    String ARTIFACT_TRANSFORMS_TASK = "artifactTransforms";

    /**
     * The name of the model report task.
     *
     * @since 8.13
     */
    String MODEL_TASK = "model";

    /**
     * The name of the dependendent components report task.
     *
     * @since 8.13
     */
    String DEPENDENT_COMPONENTS_TASK = "dependentComponents";
}
