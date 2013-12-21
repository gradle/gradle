/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.artifacts.maven;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.ArtifactRepository;

/**
 * An {@link ArtifactRepository} which can be used to publish artifacts to Maven repositories.
 */
public interface MavenResolver extends ArtifactRepository, PomFilterContainer {
    /**
     * Returns a Maven settings object. This can be used for example to figure out where the local repository is
     * located. This property is filled after publishing. Before this property is null.
     */
    Object getSettings();

    /**
     * Adds an action to be executed immediately before a deployment to this resolver. The action is executed after all
     * artifacts have been build, including generation of the POM. The action can modify the set of artifacts to be
     * deployed.
     *
     * @param action The action to execute.
     */
    void beforeDeployment(Action<? super MavenDeployment> action);

    /**
     * Adds a closure to be executed immediately before a deployment to this resolver. The closure is passed a {@link
     * org.gradle.api.artifacts.maven.MavenDeployment} as a parameter. The closure is executed after all artifacts have
     * been build, including generation of the POM. The closure can modify the set of artifacts to be deployed.
     *
     * @param action The closure to execute.
     */
    void beforeDeployment(Closure action);
}
