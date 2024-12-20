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

package org.gradle.api.publish.maven;

import org.gradle.api.Incubating;
import org.gradle.api.provider.Property;

/**
 * The repository information of the Maven distributionManagement node.
 *
 * @see MavenPom
 * @see MavenPomDistributionManagement
 * @since 8.12
 */
@Incubating
public interface MavenPomDeploymentRepository {
    /**
     * A unique identifier for a repository.
     *
     * @since 8.12
     */
    Property<String> getId();

    /**
     * Human readable name of the repository.
     *
     * @since 8.12
     */
    Property<String> getName();

    /**
     * Whether to assign snapshots a unique version comprised of the timestamp and build number, or to use the same version each time.
     *
     * Default value: <code>true</code>
     *
     * @since 8.12
     */
    Property<Boolean> getUniqueVersion();

    /**
     * The url of the repository, in the form <code>protocol://hostname/path</code>.
     *
     * @since 8.12
     */
    Property<String> getUrl();

    /**
     * The type of layout this repository uses for locating and storing artifacts - can be <code>legacy</code> or <code>default</code>.
     *
     * Default value: <code>default</code>
     *
     * @since 8.12
     */
    Property<String> getLayout();
}
