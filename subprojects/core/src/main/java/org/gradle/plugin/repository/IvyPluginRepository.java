/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.repository;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.repositories.AuthenticationSupported;
import org.gradle.api.artifacts.repositories.RepositoryLayout;

import java.net.URI;

/**
 * Represents an Ivy repository which contains Gradle plugins.
 */
@Incubating
public interface IvyPluginRepository extends PluginRepository, AuthenticationSupported {
    /**
     * The base URL of this repository. This URL is used to find Gradle plugins.
     *
     * @return The URL.
     */
    URI getUrl();

    /**
     * Sets the base URL of this repository.
     *
     * @param url The base URL.
     * @since 4.0
     */
    void setUrl(URI url);

    /**
     * Sets the base URL of this repository. This URL is used to find Gradle plugins.
     *
     * <p>The provided value is evaluated relative to the build's directory. This
     * means, for example, you can pass in a {@code File} object, or a relative path to be
     * evaluated relative to the directory of the {@code settings.gradle} file in which this
     * repository is declared.
     *
     * @param url The base URL.
     */
    void setUrl(Object url);

    /**
     * Adds an independent pattern that will be used to locate artifact files in this repository. This pattern will be used to locate ivy files as well, unless a specific
     * ivy pattern is supplied via {@link #ivyPattern(String)}.
     *
     * If this pattern is not a fully-qualified URL, it will be interpreted as a file relative to the project directory.
     * It is not interpreted relative the URL specified in {@link #setUrl(Object)}.
     *
     * Patterns added in this way will be in addition to any layout-based patterns added via {@link #setUrl}.
     *
     * @param pattern The artifact pattern.
     * @since 4.0
     */
    void artifactPattern(String pattern);

    /**
     * Adds an independent pattern that will be used to locate ivy files in this repository.
     *
     * If this pattern is not a fully-qualified URL, it will be interpreted as a file relative to the project directory.
     * It is not interpreted relative the URL specified in {@link #setUrl(Object)}.
     *
     * Patterns added in this way will be in addition to any layout-based patterns added via {@link #setUrl}.
     *
     * @param pattern The ivy pattern.
     * @since 4.0
     */
    void ivyPattern(String pattern);

    /**
     * Specifies the layout to use with this repository, based on the root url.
     * See {@link #layout(String, Action)}.
     *
     * @param layoutName The name of the layout to use.
     * @since 4.0
     */
    void layout(String layoutName);

    /**
     * Specifies how the items of the repository are organized.
     * <p>
     * The layout is configured with the supplied closure.
     * <p>
     * Recognised values are as follows:
     * </p>
     * <h4>'gradle'</h4>
     * <p>
     * A Repository Layout that applies the following patterns:
     * <ul>
     *     <li>Artifacts: <code>$baseUri/{@value org.gradle.api.artifacts.repositories.IvyArtifactRepository#GRADLE_ARTIFACT_PATTERN}</code></li>
     *     <li>Ivy: <code>$baseUri/{@value org.gradle.api.artifacts.repositories.IvyArtifactRepository#GRADLE_IVY_PATTERN}</code></li>
     * </ul>
     * </p>
     * <h4>'maven'</h4>
     * <p>
     * A Repository Layout that applies the following patterns:
     * <ul>
     *     <li>Artifacts: <code>$baseUri/{@value org.gradle.api.artifacts.repositories.IvyArtifactRepository#MAVEN_ARTIFACT_PATTERN}</code></li>
     *     <li>Ivy: <code>$baseUri/{@value org.gradle.api.artifacts.repositories.IvyArtifactRepository#MAVEN_IVY_PATTERN}</code></li>
     * </ul>
     * </p>
     * <p>
     * Following the Maven convention, the 'organisation' value is further processed by replacing '.' with '/'.
     * </p>
     * <h4>'ivy'</h4>
     * <p>
     * A Repository Layout that applies the following patterns:
     * <ul>
     *     <li>Artifacts: <code>$baseUri/{@value org.gradle.api.artifacts.repositories.IvyArtifactRepository#IVY_ARTIFACT_PATTERN}</code></li>
     *     <li>Ivy: <code>$baseUri/{@value org.gradle.api.artifacts.repositories.IvyArtifactRepository#IVY_ARTIFACT_PATTERN}</code></li>
     * </ul>
     * </p>
     * <h4>'pattern'</h4>
     * <p>
     * A repository layout that allows custom patterns to be defined. eg:
     * <pre>
     * repositories {
     *     ivy {
     *         layout 'pattern' , {
     *             artifact '[module]/[revision]/[artifact](.[ext])'
     *             ivy '[module]/[revision]/ivy.xml'
     *         }
     *     }
     * }
     * </pre>
     * </p>
     * <p>The available pattern tokens are listed as part of <a href="http://ant.apache.org/ivy/history/latest-milestone/concept.html#patterns">Ivy's Main Concepts documentation</a>.</p>
     *
     * @param layoutName The name of the layout to use.
     * @param config The action used to configure the layout.
     * @since 4.0
     */
    void layout(String layoutName, Action<? extends RepositoryLayout> config);
}
