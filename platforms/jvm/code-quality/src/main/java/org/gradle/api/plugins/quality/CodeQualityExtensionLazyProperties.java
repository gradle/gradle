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

package org.gradle.api.plugins.quality;

import org.gradle.api.Incubating;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;

/**
 * The lazy properties behind the eager getters and setters of {@link CodeQualityExtension}.
 *
 * @since 10.0.0
 */
@Incubating
public interface CodeQualityExtensionLazyProperties {

    /**
     * The version of the code quality tool to be used.
     *
     * @see CodeQualityExtension#getToolVersion()
     * @since 10.0.0
     */
    Property<String> getToolVersion();

    /**
     * The source sets to be analyzed as part of the {@code check} and {@code build} tasks.
     *
     * @see CodeQualityExtension#getSourceSets()
     * @since 10.0.0
     */
    ListProperty<SourceSet> getSourceSets();

    /**
     * Whether to allow the build to continue if there are warnings.
     *
     * @see CodeQualityExtension#isIgnoreFailures()
     * @since 10.0.0
     */
    Property<Boolean> getIgnoreFailures();
}
