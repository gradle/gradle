/*
 * Copyright 2023 the original author or authors.
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

/**
 * A {@link Publication} of Gradle Module Metadata.
 *
 * @since 8.1
 */
@Incubating
public interface GMMPublication extends Publication {
    /**
     * Provides the software component that should be published.
     *
     * <ul>
     *     <li>Any artifacts declared by the component will be included in the publication.</li>
     *     <li>The dependencies declared by the component will be included in the published meta-data.</li>
     * </ul>
     *
     * Currently, 3 types of component are supported: 'components.java' (added by the JavaPlugin), 'components.web' (added by the WarPlugin)
     * and `components.javaPlatform` (added by the JavaPlatformPlugin).
     *
     * For any individual GMMPublication, only a single component can be provided in this way.
     *
     * The following example demonstrates how to publish the 'java' component as GMM.
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
     */
    void from(SoftwareComponent component);
}
