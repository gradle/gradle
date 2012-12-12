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

package org.gradle.api.publish.maven;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.internal.HasInternalProtocol;
import org.gradle.api.publish.Publication;

/**
 * A {@code MavenPublication} is the representation/configuration of how Gradle should publish something in Maven format.
 *
 * The "{@code maven-publish}" plugin creates one {@code MavenPublication} named "{@code maven}" in the project's
 * {@code publishing.publications} container. This publication is configured to publish all of the project's
 * <i>visible</i> configurations (i.e. {@link org.gradle.api.Project#getConfigurations()}).
 * <p>
 * The Maven POM identifying attributes are mapped as follows:
 * <ul>
 * <li>{@code groupId} - {@code project.group}</li>
 * <li>{@code artifactId} - {@code project.name}</li>
 * <li>{@code version} - {@code project.version}</li>
 * </ul>
 * <p>
 * The ability to add multiple publications and finely configure publications will be added in future Gradle versions.
 *
 * <h4>Customising the publication prior to publishing</h4>
 *
 * It is possible to modify the generated POM prior to publication. This is done using the {@link MavenPom#withXml(org.gradle.api.Action)} method
 * of the POM returned via the {@link #getPom()} method, or directly by an action (or closure) passed into {@link #pom(org.gradle.api.Action)}.
 *
 *
 * @since 1.4
 */
@Incubating
@HasInternalProtocol
public interface MavenPublication extends Publication {

    /**
     * The POM that will be published.
     *
     * @return The POM that will be published.
     */
    MavenPom getPom();

    /**
     * Configures the POM that will be published.
     *
     * The supplied action will be executed against the {@link #getPom()} result. This method also accepts a closure argument, by type coercion.
     *
     * @param configure The configuration action.
     */
    void pom(Action<? super MavenPom> configure);

}
