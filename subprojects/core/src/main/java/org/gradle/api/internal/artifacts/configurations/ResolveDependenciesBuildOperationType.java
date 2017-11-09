/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.UsedByScanPlugin;

import javax.annotation.Nullable;

/**
 * Details about a dependency resolution.
 *
 * @since 4.4
 */
@UsedByScanPlugin
public final class ResolveDependenciesBuildOperationType implements BuildOperationType<ResolveDependenciesBuildOperationType.Details, ResolveDependenciesBuildOperationType.Result> {

    /**
     * Details about a resolved configuration.
     * @since 4.4
     * */
    @UsedByScanPlugin
    public interface Details {
        /**
         * The path of the resolved configuration
         * */
        String getConfigurationPath();

        /**
         * The description of the resolved configuration
         * */
        @Nullable
        String getConfigurationDescription();

        /**
         * The path of the build of the resolved configuration
         * */
        String getBuildPath();

        /**
         * Flag indicating if resolved configuration is visible
         * */
        boolean isConfigurationVisible();

        /**
         * Flag indicating if resolved configuration is transitive
         * */
        boolean isConfigurationTransitive();
    }

    /**
     * The result of a dependency resolution for a configuration.
     * @since 4.4
     * */
    @UsedByScanPlugin
    public interface Result {

        /**
         * The root component of the
         * resolution result.
         * */
        ResolvedComponentResult getRootComponent();
    }
}
