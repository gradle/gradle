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
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/**
 * The lazy properties behind the eager getters and setters of {@link CheckstyleExtension}.
 *
 * @since 10.0.0
 */
@Incubating
public interface CheckstyleExtensionLazyProperties extends CodeQualityExtensionLazyProperties {

    /**
     * The properties available for use in the configuration file.
     *
     * @see CheckstyleExtension#getConfigProperties()
     * @since 10.0.0
     */
    MapProperty<String, Object> getConfigProperties();

    /**
     * The maximum number of errors that are tolerated before breaking the build.
     *
     * @see CheckstyleExtension#getMaxErrors()
     * @since 10.0.0
     */
    Property<Integer> getMaxErrors();

    /**
     * The maximum number of warnings that are tolerated before breaking the build.
     *
     * @see CheckstyleExtension#getMaxWarnings()
     * @since 10.0.0
     */
    Property<Integer> getMaxWarnings();

    /**
     * Whether rule violations are to be displayed on the console.
     *
     * @see CheckstyleExtension#isShowViolations()
     * @since 10.0.0
     */
    Property<Boolean> getShowViolations();
}
