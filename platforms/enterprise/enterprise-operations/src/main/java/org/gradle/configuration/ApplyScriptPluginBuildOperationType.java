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

package org.gradle.configuration;

import org.gradle.internal.operations.BuildOperationType;

import javax.annotation.Nullable;

/**
 * Details about a script plugin being applied.
 *
 * @since 4.0
 */
public final class ApplyScriptPluginBuildOperationType implements BuildOperationType<ApplyScriptPluginBuildOperationType.Details, ApplyScriptPluginBuildOperationType.Result> {

    public interface Details {

        /**
         * The absolute path to the script file.
         * Null if was not a script file.
         * Null if uri != null.
         */
        @Nullable
        String getFile();

        /**
         * The URI of the script.
         * Null if was not applied as URI.
         * Null if file != null.
         */
        @Nullable
        String getUri();

        /**
         * The target of the script.
         * One of "gradle", "settings", "project" or null.
         * If null, the target is an arbitrary object.
         */
        @Nullable
        String getTargetType();

        /**
         * If the target is a project, its path.
         */
        @Nullable
        String getTargetPath();

        /**
         * The build path, if the target is a known type (i.e. targetType != null)
         */
        @Nullable
        String getBuildPath();

        /**
         * A unique ID for this plugin application, within this build operation tree.
         *
         * @see org.gradle.configuration.internal.ExecuteListenerBuildOperationType.Details#getApplicationId()
         * @since 4.10
         */
        long getApplicationId();

    }

    public interface Result {
    }

    private ApplyScriptPluginBuildOperationType() {
    }
}
