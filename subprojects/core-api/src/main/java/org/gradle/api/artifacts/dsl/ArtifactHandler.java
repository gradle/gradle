/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.artifacts.dsl;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * This class is for defining artifacts to be published and adding them to configurations. Creating publish artifacts
 * does not mean to create an archive. What is created is a domain object which represents a file to be published
 * and information on how it should be published (e.g. the name).
 *
 * <p>To create an publish artifact and assign it to a configuration you can use the following syntax:</p>
 *
 * <code>&lt;configurationName&gt; &lt;artifact-notation&gt;, &lt;artifact-notation&gt; ...</code>
 *
 * or
 *
 * <code>&lt;configurationName&gt; &lt;artifact-notation&gt; { ... some code to configure the artifact }</code>
 *
 * <p>The notation can be one of the following types:</p>
 *
 * <ul>
 *
 * <li>{@link PublishArtifact}.</li>
 *
 * <li>{@link org.gradle.api.tasks.bundling.AbstractArchiveTask}. The information for publishing the artifact is extracted from the archive task (e.g. name, extension, ...). The task will be executed if the artifact is required.</li>
 *
 * <li>A {@link org.gradle.api.file.RegularFile} or {@link org.gradle.api.file.Directory}.</li>
 *
 * <li>A {@link org.gradle.api.provider.Provider} of {@link java.io.File}, {@link org.gradle.api.file.RegularFile}, {@link org.gradle.api.file.Directory} or {@link org.gradle.api.Task}, with the limitation that the latter has to define a single file output property. The information for publishing the artifact is extracted from the file or directory name. When the provider represents an output of a particular task, that task will be executed if the artifact is required.</li>
 *
 * <li>{@link java.io.File}. The information for publishing the artifact is extracted from the file name.</li>
 *
 * <li>{@link java.util.Map}. The map should contain a 'file' key. This is converted to an artifact as described above. You can also specify other properties of the artifact using entries in the map.
 * </li>
 *
 * </ul>
 *
 * <p>In each case, a {@link ConfigurablePublishArtifact} instance is created for the artifact, to allow artifact properties to be configured. You can also override the default values for artifact properties by using a closure to configure the properties of the artifact instance</p>
 *
 * <h2>Examples</h2>
 * <p>An example showing how to associate an archive task with a configuration via the artifact handler.
 * This way the archive can be published or referred in other projects via the configuration.
 * <pre class='autoTested'>
 * configurations {
 *   //declaring new configuration that will be used to associate with artifacts
 *   schema
 * }
 *
 * task schemaJar(type: Jar) {
 *   //some imaginary task that creates a jar artifact with some schema
 * }
 *
 * //associating the task that produces the artifact with the configuration
 * artifacts {
 *   //configuration name and the task:
 *   schema schemaJar
 * }
 * </pre>
 */
@ServiceScope(Scope.Project.class)
public interface ArtifactHandler {
    /**
     * Adds an artifact to the given configuration.
     *
     * @param configurationName The name of the configuration.
     * @param artifactNotation The artifact notation, in one of the notations described above.
     * @return The artifact.
     */
    PublishArtifact add(String configurationName, Object artifactNotation);

    /**
     * Adds an artifact to the given configuration.
     *
     * @param configurationName The name of the configuration.
     * @param artifactNotation The artifact notation, in one of the notations described above.
     * @param configureClosure The closure to execute to configure the artifact.
     * @return The artifact.
     */
    PublishArtifact add(String configurationName, Object artifactNotation, @DelegatesTo(ConfigurablePublishArtifact.class) Closure configureClosure);

    /**
     * Adds an artifact to the given configuration.
     *
     * @param configurationName The name of the configuration.
     * @param artifactNotation The artifact notation, in one of the notations described above.
     * @param configureAction The action to execute to configure the artifact.
     * @return The artifact.
     *
     * @since 3.3.
     */
    PublishArtifact add(String configurationName, Object artifactNotation, Action<? super ConfigurablePublishArtifact> configureAction);
}
