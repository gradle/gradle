/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.Action;
import org.gradle.api.provider.Property;
import org.gradle.internal.HasInternalProtocol;

/**
 * The distribution management configuration of a Maven publication.
 *
 * @since 4.8
 * @see MavenPom
 */
@HasInternalProtocol
public interface MavenPomDistributionManagement {

    /**
     * The download URL of the corresponding Maven publication.
     */
    Property<String> getDownloadUrl();

    /**
     * Configures the relocation information.
     */
    void relocation(Action<? super MavenPomRelocation> action);

    /**
     * Configures the repository information.
     *
     * @since 8.12
     */
    @Incubating
    void repository(Action<? super MavenPomDeploymentRepository> action);
}
