/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.artifacts.repositories;

import groovy.lang.Closure;

import java.net.URI;

/**
 * An artifact repository which uses an Ivy format to store artifacts and meta-data.
 */
public interface IvyArtifactRepository extends ArtifactRepository, AuthenticationSupported {

    String GRADLE_ARTIFACT_PATTERN = "[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])";
    String GRADLE_IVY_PATTERN = "[organisation]/[module]/[revision]/ivy-[revision].xml";

    String MAVEN_ARTIFACT_PATTERN = "[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])";
    String MAVEN_IVY_PATTERN = "[organisation]/[module]/[revision]/ivy-[revision].xml";

    /**
     * The base URL of this repository.
     *
     * @return The URL.
     */
    URI getUrl();

    /**
     * Sets the base URL of this repository. The provided value is evaluated as for {@link org.gradle.api.Project#uri(Object)}. This means,
     * for example, you can pass in a File object or a relative path which is evaluated relative to the project directory.
     *
     * File are resolved based on the supplied URL and the configured {@link #layout(String, Closure)} for this repository.
     *
     * @param url The base URL.
     */
    void setUrl(Object url);

    /**
     * Adds an Ivy artifact pattern to use to locate artifacts in this repository. This pattern will be in addition to any layout-based patterns added via {@link #setUrl}.
     *
     * @param pattern The artifact pattern.
     */
    void artifactPattern(String pattern);

    /**
     * Adds an Ivy pattern to use to locate ivy files in this repository. This pattern will be in addition to any layout-based patterns added via {@link #setUrl}.
     *
     * @param pattern The ivy pattern.
     */
    void ivyPattern(String pattern);

    /**
     * Specifies the layout to use with this repository, based on the root url.
     * See {@link #layout(String, Closure)}.
     *
     * @param layoutName The name of the layout to use.
     */
    void layout(String layoutName);

    /**
     * Specifies the layout to use with this repository, based on the root url. The returned layout is configured with the supplied closure.
     * Available layouts are outlined below.
     * <h4>'gradle'</h4>
     * A Repository Layout that applies the following patterns:
     * <ul>
     *     <li>Artifacts: <code>$baseUri/{@value #GRADLE_ARTIFACT_PATTERN}</code></li>
     *     <li>Ivy: <code>$baseUri/{@value #GRADLE_IVY_PATTERN}</code></li>
     * </ul>
     *
     * <h4>'maven'</h4>
     * A Repository Layout that applies the following patterns:
     * <ul>
     *     <li>Artifacts: <code>$baseUri/{@value #MAVEN_ARTIFACT_PATTERN}</code></li>
     *     <li>Ivy: <code>$baseUri/{@value #MAVEN_IVY_PATTERN}</code></li>
     * </ul>
     * Following the maven convention, the 'organisation' value is further processed by replacing '.' with '/'.
     *
     * <h4>'pattern'</h4>
     * A repository layout that allows custom patterns to be defined. eg:
     * <pre autoTested="">
     * repositories {
     *     ivy {
     *         layout 'pattern' , {
     *             artifact '[module]/[revision]/[artifact](.[ext])'
     *             ivy '[module]/[revision]/ivy.xml'
     *         }
     *     }
     * }
     * </pre>
     *
     * @param layoutName The name of the layout to use.
     * @param config The closure used to configure the layout.
     */
    void layout(String layoutName, Closure config);

    /**
     * Returns the username to use for authentication with this repository, if any.
     * 
     * @return the username, may be null.
     * @deprecated Use {@link #getCredentials()} and {@link PasswordCredentials#getUsername()} instead.
     */
    @Deprecated
    String getUserName();

    /**
     * Sets the username to use for authentication with this repository, if any.
     * 
     * @param username the username, may be null.
     * @deprecated Use {@link #getCredentials()} and {@link PasswordCredentials#setUsername(String)} instead.
     */
    @Deprecated
    void setUserName(String username);

    /**
     * Returns the password to use for authentication with this repository, if any.
     *
     * @return the password, may be null.
     * @deprecated Use {@link #getCredentials()} and {@link PasswordCredentials#getPassword()} instead.
     */
    @Deprecated
    String getPassword();

    /**
     * Sets the password to use for authentication with this repository, if any.
     *
     * @param password the password, may be null.
     * @deprecated Use {@link #getCredentials()} and {@link PasswordCredentials#setPassword(String)} instead.
     */
    @Deprecated
    void setPassword(String password);
}
