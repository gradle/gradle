/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.Model;

/**
 * Provides information about the structure of a Gradle build.
 *
 * @since 1.8
 */
@Incubating
public interface GradleBuild extends Model, BuildModel {
    /**
     * Returns the identifier for this Gradle build.
     *
     * @since 2.13
     */
    @Incubating
    BuildIdentifier getBuildIdentifier();

    /**
     * Returns the root project for this build.
     *
     * @return The root project
     */
    BasicGradleProject getRootProject();

    /**
     * Returns the set of all projects for this build.
     *
     * @return The set of all projects.
     */
    DomainObjectSet<? extends BasicGradleProject> getProjects();

    /**
     * Returns the builds that were included into this one.
     *
     * @since 3.3
     */
    @Incubating
    DomainObjectSet<? extends GradleBuild> getIncludedBuilds();
}
