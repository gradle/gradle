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

package org.gradle.api.publish.gmm.publication;

import org.gradle.api.Incubating;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.publish.Publication;
import org.gradle.internal.HasInternalProtocol;

/**
 * A {@link Publication} that consists only of Gradle Module Metadata.
 *
 * @since 9.10.0
 */
@Incubating
@HasInternalProtocol
public interface GMMPublication extends Publication {

    /**
     * Provides the software component that should be published.
     *
     * <ul>
     *     <li>The variants declared by the component will be included in the published metadata.</li>
     *     <li>The dependencies declared by the component will be included in the published metadata.</li>
     * </ul>
     *
     * Only a single component can be provided to any individual {@code GMMPublication}.
     *
     * The following example demonstrates how to publish the 'java' component as Gradle Module Metadata.
     * <pre class='autoTested'>
     * plugins {
     *     id 'java'
     *     id 'gmm-publish'
     * }
     *
     * publishing {
     *   publications {
     *     gmm(GMMPublication) {
     *       from components.java
     *     }
     *   }
     * }
     * </pre>
     *
     * @param component The software component to publish.
     * @since 9.10.0
     */
    void from(SoftwareComponent component);
}
