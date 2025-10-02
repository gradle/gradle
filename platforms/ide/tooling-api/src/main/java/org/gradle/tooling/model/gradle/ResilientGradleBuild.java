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

package org.gradle.tooling.model.gradle;

import org.gradle.api.Incubating;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.Model;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * Provides information about the structure of a Gradle build.
 * This is a "resilient" version of {@link GradleBuild} that provides information about build failures.
 *
 * @since 9.3.0
 */
@Incubating
@NullMarked
public interface ResilientGradleBuild extends Model, BuildModel {

    /**
     * Returns the GradleBuild instance
     * @return the GradleBuild instance
     *
     * @since 9.3.0
     */
    GradleBuild getGradleBuild();

    /**
     * Returns whether the project has failed to load the full build.
     *
     * @return {@code true} if the project has failed, {@code false} otherwise.
     * @since 9.3.0
     */
    boolean didItFail();

    /**
     * Returns the failure that caused the build to fail, if any.
     *
     * @return the failure, or {@code null} if the build did not fail.
     * @since 9.3.0
     */
    @Nullable
    Collection<String> getFailures();
}
