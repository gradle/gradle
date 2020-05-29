/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish;

import org.gradle.api.Incubating;
import org.gradle.api.Named;

/**
 * A publication is a description of a consumable representation of one or more artifacts, and possibly associated metadata.
 *
 * @since 1.3
 */
public interface Publication extends Named {

    /**
     * Disables publication of a unique build identifier in Gradle Module Metadata.
     * <p>
     * The build identifier is published by default.
     *
     * @since 6.6
     */
    @Incubating
    void withoutBuildIdentifier();

    /**
     * Enables publication of a unique build identifier in Gradle Module Metadata.
     * <p>
     * The build identifier is published by default.
     *
     * @since 6.6
     */
    @Incubating
    void withBuildIdentifier();
}
