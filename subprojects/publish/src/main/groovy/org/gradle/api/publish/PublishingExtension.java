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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.dsl.RepositoryHandler;

/**
 * The configuration of how to “publish” the different components of a project.
 * <p>
 * This new publishing mechanism will eventually replace the current mechanism of upload tasks and configurations. At this time, it is an
 * incubating feature and under development.
 *
 * <p>
 * The PublishingExtension is a {@link org.gradle.api.plugins.DeferredConfigurable} model element, meaning that extension will be configured as late as possible in the build.
 * So any 'publishing' configuration blocks are not evaluated until either:
 * <ol>
 *     <li>The project is about to execute, or</li>
 *     <li>he publishing extension is referenced as an instance, as opposed to via a configuration closure.</li>
 * </ol>
 * <p>
 * A 'publishing' configuration block does not need to dereference the publishing extension, and so will be evaluated late. eg:
 * <pre>
 *     publishing {
 *         publications { ... }
 *         repositories.maven { ... }
 *     }
 * </pre>
 *
 * <p>
 * Any use that accesses the publishing extension as an instance does require the publishing extension to be realised, forcing all configuration blocks to be evaluated. eg:
 * <pre>
 *     publishing.publications { ... }
 *     publishing.repositories.maven { ... }
 * </pre>
 *
 * @since 1.3
 */
@Incubating
public interface PublishingExtension {

    /**
     * The name of this extension when installed by the {@link org.gradle.api.publish.plugins.PublishingPlugin} ({@value}).
     */
    String NAME = "publishing";

    /**
     * The container of possible repositories to publish to.
     * <p>
     * See {@link #repositories(org.gradle.api.Action)} for more information.
     *
     * @return The container of possible repositories to publish to.
     */
    RepositoryHandler getRepositories();

    /**
     * Configures the container of possible repositories to publish to.
     *
     * <pre autoTested="true">
     * apply plugin: 'publishing'
     *
     * publishing {
     *   repositories {
     *     // Create an ivy publication destination named “releases”
     *     ivy {
     *       name "releases"
     *       url "http://my.org/ivy-repos/releases"
     *     }
     *   }
     * }
     * </pre>
     *
     * The {@code repositories} block is backed by a {@link RepositoryHandler}, which is the same DSL as that that is used for declaring repositories to consume dependencies from. However,
     * certain types of repositories that can be created by the repository handler are not valid for publishing, such as {@link org.gradle.api.artifacts.dsl.RepositoryHandler#mavenCentral()}.
     * <p>
     * At this time, only repositories created by the {@code ivy()} factory method have any effect. Please see {@link org.gradle.api.publish.ivy.IvyPublication}
     * for information on how this can be used for publishing to Ivy repositories.
     *
     * @param configure The action to configure the container of repositories with.
     */
    void repositories(Action<? super RepositoryHandler> configure);

    /**
     * The publications of the project.
     * <p>
     * See {@link #publications(org.gradle.api.Action)} for more information.
     *
     * @return The publications of this project.
     */
    PublicationContainer getPublications();

    /**
     * Configures the publications of this project.
     * <p>
     * The publications container defines the outgoing publications of the project. That is, the consumable representations of things produced
     * by building the project. An example of a publication would be an Ivy Module (i.e. {@code ivy.xml} and artifacts), or
     * Maven Project (i.e. {@code pom.xml} and artifacts).
     * <p>
     * Actual publication implementations and the ability to create them are provided by different plugins. The “publishing” plugin itself does not provide any publication types.
     * For example, given that the 'maven-publish' plugin provides a {@link org.gradle.api.publish.maven.MavenPublication} type, you can create a publication like:
     * <pre autoTested="true">
     * apply plugin: 'maven-publish'
     *
     * publishing {
     *   publications {
     *     myPublicationName(MavenPublication) {
     *       // Configure the publication here
     *     }
     *   }
     * }
     * </pre>
     * <p>
     * Please see {@link org.gradle.api.publish.ivy.IvyPublication} and {@link org.gradle.api.publish.maven.MavenPublication} for more information on publishing in these specific formats.
     *
     * @param configure The action or closure to configure the publications with.
     */
    void publications(Action<? super PublicationContainer> configure);

}
