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
import org.gradle.api.component.SoftwareComponent;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.api.publish.Publication;

/**
 * A {@code MavenPublication} is the representation/configuration of how Gradle should publish something in Maven format.
 *
 * You directly add a named Maven Publication the project's {@code publishing.publications} container by providing {@link MavenPublication} as the type.
 * <pre>
 * publishing {
 *   publications {
 *     myPublicationName(MavenPublication) {
 *       // Configure the publication here
 *     }
 *   }
 * }
 * </pre>
 *
 * The default Maven POM identifying attributes are mapped as follows:
 * <ul>
 * <li>{@code groupId} - {@code project.group}</li>
 * <li>{@code artifactId} - {@code project.name}</li>
 * <li>{@code version} - {@code project.version}</li>
 * </ul>
 *
 * <p>
 * For certain common use cases, it's often sufficient to specify the component to publish, and nothing more ({@link #from(org.gradle.api.component.SoftwareComponent)}.
 * The published component is used to determine which artifacts to publish, and which dependencies should be listed in the generated POM file.
 * </p><p>
 * To add additional artifacts to the set published, use the {@link #artifact(Object)} and {@link #artifact(Object, org.gradle.api.Action)} methods.
 * You can also completely replace the set of published artifacts using {@link #setArtifacts(Iterable)}.
 * Together, these methods give you full control over what artifacts will be published.
 * </p><p>
 * For any other tweaks to the publication, it is possible to modify the generated POM prior to publication. This is done using the {@link MavenPom#withXml(org.gradle.api.Action)} method
 * of the POM returned via the {@link #getPom()} method, or directly by an action (or closure) passed into {@link #pom(org.gradle.api.Action)}.
 * </p>
 * <h4>Example of publishing a java module with a source artifact and custom POM description</h4>
 * <pre autoTested="true">
 * apply plugin: "java"
 * apply plugin: "maven-publish"
 *
 * task sourceJar(type: Jar) {
 *   from sourceSets.main.allJava
 * }
 *
 * publishing {
 *   publications {
 *     myPublication(MavenPublication) {
 *       from components.java
 *       artifact sourceJar {
 *         classifier "source"
 *       }
 *       pom.withXml {
 *         asNode().appendNode('description', 'A demonstration of Maven POM customization')
 *       }
 *     }
 *   }
 * }
 * </pre>
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

    /**
     * Provides the software component that should be published.
     *
     * <ul>
     *     <li>Any artifacts declared by the component will be included in the publication.</li>
     *     <li>The dependencies declared by the component will be included in the published meta-data.</li>
     * </ul>
     *
     * Currently 2 types of component are supported: 'components.java' (added by the JavaPlugin) and 'components.web' (added by the WarPlugin).
     * For any individual MavenPublication, only a single component can be provided in this way.
     *
     * The following example demonstrates how to publish the 'java' component to a Maven repository.
     * <pre autoTested="true">
     * apply plugin: "java"
     * apply plugin: "maven-publish"
     *
     * publishing {
     *   publications {
     *     maven(MavenPublication) {
     *       from components.java
     *     }
     *   }
     * }
     * </pre>
     *
     * @param component The software component to publish.
     */
    void from(SoftwareComponent component);

    /**
     * Creates a custom {@link MavenArtifact} to be included in the publication.
     *
     * The <code>artifact</code> method can take a variety of input:
     * <ul>
     *     <li>A {@link org.gradle.api.artifacts.PublishArtifact} instance. Extension and classifier values are taken from the wrapped instance.</li>
     *     <li>An {@link org.gradle.api.tasks.bundling.AbstractArchiveTask} instance. Extension and classifier values are taken from the wrapped instance.</li>
     *     <li>Anything that can be resolved to a {@link java.io.File} via the {@link org.gradle.api.Project#file(Object)} method.
     *          Extension and classifier values are interpolated from the file name.</li>
     *     <li>A {@link java.util.Map} that contains a 'source' entry that can be resolved as any of the other input types, including file.
     *         This map can contain a 'classifier' and an 'extension' entry to further configure the constructed artifact.</li>
     * </ul>
     *
     * The following example demonstrates the addition of various custom artifacts.
     * <pre autoTested="true">
     * apply plugin: "maven-publish"
     *
     * task sourceJar(type: Jar) {
     *   classifier "source"
     * }
     *
     * publishing {
     *   publications {
     *     maven(MavenPublication) {
     *       artifact sourceJar // Publish the output of the sourceJar task
     *       artifact 'my-file-name.jar' // Publish a file created outside of the build
     *       artifact source: sourceJar, classifier: 'src', extension: 'zip'
     *     }
     *   }
     * }
     * </pre>
     *
     * @param source The source of the artifact content.
     */
    MavenArtifact artifact(Object source);

    /**
     * Creates an {@link MavenArtifact} to be included in the publication, which is configured by the associated action.
     *
     * The first parameter is used to create a custom artifact and add it to the publication, as per {@link #artifact(Object)}.
     * The created {@link MavenArtifact} is then configured using the supplied action, which can override the extension or classifier of the artifact.
     * This method also accepts the configure action as a closure argument, by type coercion.
     *
     * <pre autoTested="true">
     * apply plugin: "maven-publish"
     *
     * task sourceJar(type: Jar) {
     *   classifier "source"
     * }
     *
     * publishing {
     *   publications {
     *     maven(MavenPublication) {
     *       artifact sourceJar {
     *         // These values will be used instead of the values from the task. The task values will not be updated.
     *         classifier "src"
     *         extension "zip"
     *       }
     *       artifact("my-docs-file.htm") {
     *         classifier "documentation"
     *         extension "html"
     *       }
     *     }
     *   }
     * }
     * </pre>
     *
     * @param source The source of the artifact.
     * @param config An action to configure the values of the constructed {@link MavenArtifact}.
     */
    MavenArtifact artifact(Object source, Action<? super MavenArtifact> config);

    /**
     * Clears any previously added artifacts from {@link #getArtifacts} and creates artifacts from the specified sources.
     * Each supplied source is interpreted as per {@link #artifact(Object)}.
     *
     * For example, to exclude the dependencies declared by a component and instead use a custom set of artifacts:
     * <pre autoTested="true">
     * apply plugin: "java"
     * apply plugin: "maven-publish"
     *
     * task sourceJar(type: Jar) {
     *   classifier "source"
     * }

     * publishing {
     *   publications {
     *     maven(MavenPublication) {
     *       from components.java
     *       artifacts = ["my-custom-jar.jar", sourceJar]
     *     }
     *   }
     * }
     * </pre>
     *
     * @param sources The set of artifacts for this publication.
     */
    void setArtifacts(Iterable<?> sources);

    /**
     * Returns the complete set of artifacts for this publication.
     * @return the artifacts.
     */
    MavenArtifactSet getArtifacts();

    /**
     * Returns the groupId for this publication.
     */
    String getGroupId();

    /**
     * Sets the groupId for this publication.
     */
    void setGroupId(String groupId);

    /**
     * Returns the artifactId for this publication.
     */
    String getArtifactId();

    /**
     * Sets the artifactId for this publication.
     */
    void setArtifactId(String artifactId);

    /**
     * Returns the version for this publication.
     */
    String getVersion();

    /**
     * Sets the version for this publication.
     */
    void setVersion(String version);

}
