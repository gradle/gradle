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

package org.gradle.api.publish.ivy;

import org.gradle.api.Action;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.provider.Property;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.VersionMappingStrategy;
import org.gradle.api.tasks.Nested;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

/**
 * An {@code IvyPublication} is the representation/configuration of how Gradle should publish something in Ivy format, to an Ivy repository.
 *
 * You directly add a named Ivy publication the project's {@code publishing.publications} container by providing {@link IvyPublication} as the type.
 * <pre>
 * publishing {
 *   publications {
 *     myPublicationName(IvyPublication) {
 *       // Configure the publication here
 *     }
 *   }
 * }
 * </pre>
 *
 * <p>
 * The Ivy module identifying attributes of the publication are mapped as follows:
 * </p>
 * <ul>
 * <li>{@code module} - {@code project.name}</li>
 * <li>{@code organisation} - {@code project.group}</li>
 * <li>{@code revision} - {@code project.version}</li>
 * <li>{@code status} - {@code project.status}</li>
 * </ul>
 *
 * <p>
 * For certain common use cases, it's often sufficient to specify the component to publish, using ({@link #from(org.gradle.api.component.SoftwareComponent)}.
 * The published component is used to determine which artifacts to publish, and which configurations and dependencies should be listed in the generated ivy descriptor file.
 * </p><p>
 * You can add configurations to the generated ivy descriptor file, by supplying a Closure to the {@link #configurations(org.gradle.api.Action)} method.
 * </p><p>
 * To add additional artifacts to the set published, use the {@link #artifact(Object)} and {@link #artifact(Object, org.gradle.api.Action)} methods.
 * You can also completely replace the set of published artifacts using {@link #setArtifacts(Iterable)}.
 * Together, these methods give you full control over the artifacts to be published.
 * </p><p>
 * In addition, {@link IvyModuleDescriptorSpec} provides configuration methods to customize licenses, authors, and the description to be published in the Ivy module descriptor.
 * </p><p>
 * For any other tweaks to the publication, it is possible to modify the generated Ivy descriptor file prior to publication. This is done using
 * the {@link IvyModuleDescriptorSpec#withXml(org.gradle.api.Action)} method, normally via a Closure passed to the {@link #descriptor(org.gradle.api.Action)} method.
 * </p>
 *
 * <pre class='autoTested'>
 * // Example of publishing a java component with an added source jar and custom module description
 * plugins {
 *     id 'java'
 *     id 'ivy-publish'
 * }
 *
 * task sourceJar(type: Jar) {
 *   from sourceSets.main.allJava
 * }
 *
 * publishing {
 *   publications {
 *     myPublication(IvyPublication) {
 *       from components.java
 *       artifact(sourceJar) {
 *         type = "source"
 *         extension = "src.jar"
 *         conf = "runtime"
 *       }
 *       descriptor {
 *         license {
 *           name = "Custom License"
 *         }
 *         author {
 *           name = "Custom Name"
 *         }
 *         description {
 *           text = "Custom Description"
 *         }
 *       }
 *     }
 *   }
 * }
 * </pre>
 *
 * @since 1.3
 */
@HasInternalProtocol
public interface IvyPublication extends Publication {

    /**
     * The module descriptor that will be published.
     *
     * @return The module descriptor that will be published.
     */
    @Nested
    IvyModuleDescriptorSpec getDescriptor();

    /**
     * Configures the descriptor that will be published.
     * <p>
     * The descriptor XML can be modified by using the {@link IvyModuleDescriptorSpec#withXml(org.gradle.api.Action)} method.
     *
     * @param configure The configuration action.
     */
    void descriptor(Action<? super IvyModuleDescriptorSpec> configure);

    /**
     * Provides the software component that should be published.
     *
     * <ul>
     *     <li>Any artifacts declared by the component will be included in the publication.</li>
     *     <li>The dependencies declared by the component will be included in the published meta-data.</li>
     * </ul>
     *
     * Currently 2 types of component are supported: 'components.java' (added by the JavaPlugin) and 'components.web' (added by the WarPlugin).
     * For any individual IvyPublication, only a single component can be provided in this way.
     *
     * The following example demonstrates how to publish the 'java' component to a ivy repository.
     * <pre class='autoTested'>
     * plugins {
     *     id 'java'
     *     id 'ivy-publish'
     * }
     *
     * publishing {
     *   publications {
     *     ivy(IvyPublication) {
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
     * Defines some {@link IvyConfiguration}s that should be included in the published ivy module descriptor file.
     *
     * The following example demonstrates how to add a "testCompile" configuration, and a "testRuntime" configuration that extends it.
     * <pre class='autoTested'>
     * plugins {
     *     id 'java'
     *     id 'ivy-publish'
     * }
     *
     * publishing {
     *   publications {
     *     ivy(IvyPublication) {
     *       configurations {
     *           testCompile {}
     *           testRuntime {
     *               extend "testCompile"
     *           }
     *       }
     *     }
     *   }
     * }
     * </pre>
     *
     * @param config An action or closure to configure the values of the constructed {@link IvyConfiguration}.
     */
    void configurations(Action<? super IvyConfigurationContainer> config);

    /**
     * Returns the complete set of configurations for this publication.
     * @return the configurations
     */
    IvyConfigurationContainer getConfigurations();

    /**
     * Creates a custom {@link IvyArtifact} to be included in the publication.
     *
     * The <code>artifact</code> method can take a variety of input:
     * <ul>
     *     <li>A {@link org.gradle.api.artifacts.PublishArtifact} instance. Name, type, extension and classifier values are taken from the supplied instance.</li>
     *     <li>An {@link org.gradle.api.tasks.bundling.AbstractArchiveTask} instance. Name, type, extension and classifier values are taken from the supplied instance.</li>
     *     <li>Anything that can be resolved to a {@link java.io.File} via the {@link org.gradle.api.Project#file(Object)} method.
     *          Name, extension and classifier values are interpolated from the file name.</li>
     *     <li>A {@link java.util.Map} that contains a 'source' entry that can be resolved as any of the other input types, including file.
     *         This map can contain additional attributes to further configure the constructed artifact.</li>
     * </ul>
     *
     * The following example demonstrates the addition of various custom artifacts.
     * <pre class='autoTested'>
     * plugins {
     *     id 'ivy-publish'
     * }
     *
     * task sourceJar(type: Jar) {
     *   archiveClassifier = "source"
     * }
     *
     * task genDocs {
     *   doLast {
     *     // Generate 'my-docs-file.htm'
     *   }
     * }
     *
     * publishing {
     *   publications {
     *     ivy(IvyPublication) {
     *       artifact sourceJar // Publish the output of the sourceJar task
     *       artifact 'my-file-name.jar' // Publish a file created outside of the build
     *       artifact source: 'my-docs-file.htm', classifier: 'docs', extension: 'html', builtBy: genDocs // Publish a file generated by the 'genDocs' task
     *     }
     *   }
     * }
     * </pre>
     *
     * @param source The source of the artifact content.
     */
    IvyArtifact artifact(Object source);

    /**
     * Creates an {@link IvyArtifact} to be included in the publication, which is configured by the associated action.
     *
     * The first parameter is used to create a custom artifact and add it to the publication, as per {@link #artifact(Object)}.
     * The created {@link IvyArtifact} is then configured using the supplied action.
     * This method also accepts the configure action as a closure argument, by type coercion.
     *
     * <pre class='autoTested'>
     * plugins {
     *     id 'ivy-publish'
     * }
     *
     * task sourceJar(type: Jar) {
     *   archiveClassifier = "source"
     * }

     * task genDocs {
     *   doLast {
     *     // Generate 'my-docs-file.htm'
     *   }
     * }
     *
     * publishing {
     *   publications {
     *     ivy(IvyPublication) {
     *       artifact(sourceJar) {
     *         // These values will be used instead of the values from the task. The task values will not be updated.
     *         classifier = "src"
     *         extension = "zip"
     *         conf = "runtime-&gt;default"
     *       }
     *       artifact("my-docs-file.htm") {
     *         type = "documentation"
     *         extension = "html"
     *         builtBy genDocs
     *       }
     *     }
     *   }
     * }
     * </pre>
     *
     * @param source The source of the artifact.
     * @param config An action to configure the values of the constructed {@link IvyArtifact}.
     */
    IvyArtifact artifact(Object source, Action<? super IvyArtifact> config);

    /**
     * The complete set of artifacts for this publication.
     *
     * <p>
     * Setting this property will clear any previously added artifacts and create artifacts from the specified sources.
     * Each supplied source is interpreted as per {@link #artifact(Object)}.
     *
     * For example, to exclude the dependencies declared by a component and instead use a custom set of artifacts:
     * <pre class='autoTested'>
     * plugins {
     *     id 'java'
     *     id 'ivy-publish'
     * }
     *
     * task sourceJar(type: Jar) {
     *   archiveClassifier = "source"
     * }
     *
     * publishing {
     *   publications {
     *     ivy(IvyPublication) {
     *       from components.java
     *       artifacts = ["my-custom-jar.jar", sourceJar]
     *     }
     *   }
     * }
     * </pre>
     *
     * @return the artifacts.
     */
    IvyArtifactSet getArtifacts();

    /**
     * Sets the artifacts for this publication. Each supplied value is interpreted as per {@link #artifact(Object)}.
     *
     * @param sources The set of artifacts for this publication.
     */
    void setArtifacts(Iterable<?> sources);

    /**
     * The organisation for this publication.
     */
    @ReplacesEagerProperty
    Property<String> getOrganisation();

    /**
     * The module for this publication.
     */
    @ReplacesEagerProperty
    Property<String> getModule();

    /**
     * The revision for this publication.
     */
    @ReplacesEagerProperty
    Property<String> getRevision();

    /**
     * Configures the version mapping strategy.
     *
     * For example, to use resolved versions for runtime dependencies:
     * <pre class='autoTested'>
     * plugins {
     *     id 'java'
     *     id 'ivy-publish'
     * }
     *
     * publishing {
     *   publications {
     *     maven(IvyPublication) {
     *       from components.java
     *       versionMapping {
     *         usage('java-runtime'){
     *           fromResolutionResult()
     *         }
     *       }
     *     }
     *   }
     * }
     * </pre>
     *
     * @param configureAction the configuration
     *
     * @since 5.4
     */
    void versionMapping(Action<? super VersionMappingStrategy> configureAction);

    /**
     * Silences the compatibility warnings for the Ivy publication for the specified variant.
     *
     * Warnings are emitted when Gradle features are used that cannot be mapped completely to Ivy xml.
     *
     * @param variantName the variant to silence warning for
     *
     * @since 6.0
     */
    void suppressIvyMetadataWarningsFor(String variantName);

    /**
     * Silences all the compatibility warnings for the Ivy publication.
     *
     * Warnings are emitted when Gradle features are used that cannot be mapped completely to Ivy xml.
     *
     * @since 6.0
     */
    void suppressAllIvyMetadataWarnings();
}
