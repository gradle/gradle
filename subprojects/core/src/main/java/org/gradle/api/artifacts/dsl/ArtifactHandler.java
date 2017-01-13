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
import org.gradle.api.Action;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.PublishArtifact;

/**
 * This class is for defining artifacts to be published and adding them to configurations. Creating publish artifacts
 * does not mean to create an archive. What is created is a domain object which represents a file to be published
 * and information on how it should be published (e.g. the name).
 *
 * <p>To create an publish artifact and assign it to a configuration you can use the following syntax:</p>
 *
 * <code>&lt;configurationName> &lt;artifact-notation>, &lt;artifact-notation> ...</code>
 *
 * or
 *
 * <code>&lt;configurationName> &lt;artifact-notation> { ... some code to configure the artifact }</code>
 *
 * <p>The notation can be one of the following types:</p>
 *
 * <ul>
 *
 * <li>{@link org.gradle.api.tasks.bundling.AbstractArchiveTask}. The information for publishing the artifact is extracted from the archive task (e.g. name, extension, ...).
 * An archive artifact is represented using an instance of {@link PublishArtifact}.</li>
 *
 * <li>{@link java.io.File}. The information for publishing the artifact is extracted from the file name. You can tweak the resulting values by using
 * a closure to configure the properties of the artifact instance. A file artifact is represented using an instance of {@link org.gradle.api.artifacts.ConfigurablePublishArtifact}
 * </li>
 *
 * <li>{@link java.util.Map}. The map should contain a 'file' key. This is converted to an artifact as described above. You can also
 * specify other properties of the artifact using entries in the map.
 * </li>
 *
 * </ul>
 *
 * <h2>Examples</h2>
 * <p>An example showing how to associate an archive task with a configuration via the artifact handler.
 * This way the archive can be published or referred in other projects via the configuration.
 * <pre autoTested=''>
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
    PublishArtifact add(String configurationName, Object artifactNotation, Closure configureClosure);

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
