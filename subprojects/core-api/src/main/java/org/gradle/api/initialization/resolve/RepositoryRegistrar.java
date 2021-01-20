/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.initialization.resolve;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.ExclusiveContentRepository;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

import java.util.Map;

/**
 * Allows registration of repositories, for example inside {@link DependencyResolutionManagement} in {@link org.gradle.api.initialization.Settings}.
 *
 * @since 6.8.1
 */
@Incubating
public interface RepositoryRegistrar {

    /**
     * Adds a resolver that looks into a number of directories for artifacts. The artifacts are expected to be located in the
     * root of the specified directories. The resolver ignores any group/organization information specified in the
     * dependency section of your build script. If you only use this kind of resolver you might specify your
     * dependencies like <code>":junit:4.4"</code> instead of <code>"junit:junit:4.4"</code>.
     *
     * The following parameter are accepted as keys for the map:
     *
     * <table summary="Shows property keys and associated values">
     * <tr><th>Key</th>
     *     <th>Description of Associated Value</th></tr>
     * <tr><td><code>name</code></td>
     *     <td><em>(optional)</em> The name of the repository.
     * The default is a Hash value of the rootdir paths. The name is used in the console output,
     * to point to information related to a particular repository. A name must be unique amongst a repository group.</td></tr>
     * <tr><td><code>dirs</code></td>
     *     <td>Specifies a list of rootDirs where to look for dependencies. These are evaluated as per {@link org.gradle.api.Project#files(Object...)}</td></tr>
     * </table>
     *
     * <p>Examples:</p>
     * <pre class='autoTested'>
     * repositories {
     *     flatDir name: 'libs', dirs: "$projectDir/libs"
     *     flatDir dirs: ["$projectDir/libs1", "$projectDir/libs2"]
     * }
     * </pre>
     *
     * @param args The arguments used to configure the repository.
     * @return the added resolver
     * @throws org.gradle.api.InvalidUserDataException In the case neither rootDir nor rootDirs is specified of if both
     * are specified.
     */
    RepositoryRegistrar flatDir(Map<String, ?> args);

    /**
     * Adds an configures a repository which will look for dependencies in a number of local directories.
     *
     * @param action The action to execute to configure the repository.
     * @return The repository.
     */
    RepositoryRegistrar flatDir(Action<? super FlatDirectoryArtifactRepository> action);

    /**
     * Adds a repository which looks in Gradle Central Plugin Repository for dependencies.
     *
     * @return The Gradle Central Plugin Repository
     */
    RepositoryRegistrar gradlePluginPortal();

    /**
     * Adds a repository which looks in Gradle Central Plugin Repository for dependencies.
     *
     * @param action a configuration action
     * @return the added resolver
     */
    RepositoryRegistrar gradlePluginPortal(Action<? super ArtifactRepository> action);

    /**
     * Adds a repository which looks in Bintray's JCenter repository for dependencies.
     * <p>
     * The URL used to access this repository is {@literal "https://jcenter.bintray.com/"}.
     * The behavior of this repository is otherwise the same as those added by {@link #maven(org.gradle.api.Action)}.
     * <p>
     * Examples:
     * <pre class='autoTested'>
     * repositories {
     *   jcenter {
     *     artifactUrls = ["http://www.mycompany.com/artifacts1", "http://www.mycompany.com/artifacts2"]
     *   }
     *   jcenter {
     *     name = "nonDefaultName"
     *     artifactUrls = ["http://www.mycompany.com/artifacts1"]
     *   }
     * }
     * </pre>
     *
     * @param action a configuration action
     * @return the added repository
     */
    RepositoryRegistrar jcenter(Action<? super MavenArtifactRepository> action);

    /**
     * Adds a repository which looks in Bintray's JCenter repository for dependencies.
     * <p>
     * The URL used to access this repository is {@literal "https://jcenter.bintray.com/"}.
     * The behavior of this repository is otherwise the same as those added by {@link #maven(org.gradle.api.Action)}.
     * <p>
     * Examples:
     * <pre class='autoTested'>
     * repositories {
     *     jcenter()
     * }
     * </pre>
     *
     * @return the added resolver
     * @see #jcenter(Action)
     */
    RepositoryRegistrar jcenter();

    /**
     * Adds a repository which looks in the Maven central repository for dependencies. The URL used to access this repository is
     * {@value org.gradle.api.artifacts.ArtifactRepositoryContainer#MAVEN_CENTRAL_URL}.
     *
     * <p>The following parameter are accepted as keys for the map:
     *
     * <table summary="Shows property keys and associated values">
     * <tr><th>Key</th>
     *     <th>Description of Associated Value</th></tr>
     * <tr><td><code>name</code></td>
     *     <td><em>(optional)</em> The name of the repository. The default is
     * {@value org.gradle.api.artifacts.ArtifactRepositoryContainer#DEFAULT_MAVEN_CENTRAL_REPO_NAME} is used as the name. A name
     * must be unique amongst a repository group.
     * </td></tr>
     * <tr><td><code>artifactUrls</code></td>
     *     <td>A single jar repository or a collection of jar repositories containing additional artifacts not found in the Maven central repository.
     * But be aware that the POM must exist in Maven central.
     * The provided values are evaluated as per {@link org.gradle.api.Project#uri(Object)}.</td></tr>
     * </table>
     *
     * <p>Examples:</p>
     * <pre class='autoTested'>
     * repositories {
     *     mavenCentral artifactUrls: ["http://www.mycompany.com/artifacts1", "http://www.mycompany.com/artifacts2"]
     *     mavenCentral name: "nonDefaultName", artifactUrls: ["http://www.mycompany.com/artifacts1"]
     * }
     * </pre>
     *
     * @param args A list of urls of repositories to look for artifacts only.
     * @return the added repository
     */
    RepositoryRegistrar mavenCentral(Map<String, ?> args);

    /**
     * Adds a repository which looks in the Maven central repository for dependencies. The URL used to access this repository is
     * {@value org.gradle.api.artifacts.ArtifactRepositoryContainer#MAVEN_CENTRAL_URL}. The name of the repository is
     * {@value org.gradle.api.artifacts.ArtifactRepositoryContainer#DEFAULT_MAVEN_CENTRAL_REPO_NAME}.
     *
     * <p>Examples:</p>
     * <pre class='autoTested'>
     * repositories {
     *     mavenCentral()
     * }
     * </pre>
     *
     * @return the added resolver
     * @see #mavenCentral(java.util.Map)
     */
    RepositoryRegistrar mavenCentral();

    /**
     * Adds a repository which looks in the Maven central repository for dependencies. The URL used to access this repository is
     * {@value org.gradle.api.artifacts.ArtifactRepositoryContainer#MAVEN_CENTRAL_URL}. The name of the repository is
     * {@value org.gradle.api.artifacts.ArtifactRepositoryContainer#DEFAULT_MAVEN_CENTRAL_REPO_NAME}.
     *
     * <p>Examples:</p>
     * <pre class='autoTested'>
     * repositories {
     *     mavenCentral()
     * }
     * </pre>
     *
     * @param action a configuration action
     * @return the added resolver
     */
    RepositoryRegistrar mavenCentral(Action<? super MavenArtifactRepository> action);

    /**
     * Adds a repository which looks in the local Maven cache for dependencies. The name of the repository is
     * {@value org.gradle.api.artifacts.ArtifactRepositoryContainer#DEFAULT_MAVEN_LOCAL_REPO_NAME}.
     *
     * <p>Examples:</p>
     * <pre class='autoTested'>
     * repositories {
     *     mavenLocal()
     * }
     * </pre>
     * <p>
     * The location for the repository is determined as follows (in order of precedence):
     * </p>
     * <ol>
     * <li>The value of system property 'maven.repo.local' if set;</li>
     * <li>The value of element &lt;localRepository&gt; of <code>~/.m2/settings.xml</code> if this file exists and element is set;</li>
     * <li>The value of element &lt;localRepository&gt; of <code>$M2_HOME/conf/settings.xml</code> (where <code>$M2_HOME</code> is the value of the environment variable with that name) if this file exists and element is set;</li>
     * <li>The path <code>~/.m2/repository</code>.</li>
     * </ol>
     *
     * @return the added resolver
     */
    RepositoryRegistrar mavenLocal();

    /**
     * Adds a repository which looks in the local Maven cache for dependencies. The name of the repository is
     * {@value org.gradle.api.artifacts.ArtifactRepositoryContainer#DEFAULT_MAVEN_LOCAL_REPO_NAME}.
     *
     * <p>Examples:</p>
     * <pre class='autoTested'>
     * repositories {
     *     mavenLocal()
     * }
     * </pre>
     * <p>
     * The location for the repository is determined as follows (in order of precedence):
     * </p>
     * <ol>
     * <li>The value of system property 'maven.repo.local' if set;</li>
     * <li>The value of element &lt;localRepository&gt; of <code>~/.m2/settings.xml</code> if this file exists and element is set;</li>
     * <li>The value of element &lt;localRepository&gt; of <code>$M2_HOME/conf/settings.xml</code> (where <code>$M2_HOME</code> is the value of the environment variable with that name) if this file exists and element is set;</li>
     * <li>The path <code>~/.m2/repository</code>.</li>
     * </ol>
     *
     * @param action a configuration action
     * @return the added resolver
     */
    RepositoryRegistrar mavenLocal(Action<? super MavenArtifactRepository> action);

    /**
     * Adds a repository which looks in Google's Maven repository for dependencies.
     * <p>
     * The URL used to access this repository is {@literal "https://dl.google.com/dl/android/maven2/"}.
     * <p>
     * Examples:
     * <pre class='autoTested'>
     * repositories {
     *     google()
     * }
     * </pre>
     *
     * @return the added resolver
     */
    RepositoryRegistrar google();

    /**
     * Adds a repository which looks in Google's Maven repository for dependencies.
     * <p>
     * The URL used to access this repository is {@literal "https://dl.google.com/dl/android/maven2/"}.
     * <p>
     * Examples:
     * <pre class='autoTested'>
     * repositories {
     *     google()
     * }
     * </pre>
     *
     * @param action a configuration action
     * @return the added resolver
     */
    RepositoryRegistrar google(Action<? super MavenArtifactRepository> action);

    /**
     * Adds and configures a Maven repository.
     *
     * @param action The action to use to configure the repository.
     * @return The added repository.
     */
    RepositoryRegistrar maven(Action<? super MavenArtifactRepository> action);

    /**
     * Adds and configures an Ivy repository.
     *
     * @param action The action to use to configure the repository.
     * @return The added repository.
     */
    RepositoryRegistrar ivy(Action<? super IvyArtifactRepository> action);

    /**
     * Declares exclusive content repositories. Exclusive content repositories are
     * repositories for which you can declare an inclusive content filter. Artifacts
     * matching the filter will then only be searched in the repositories which
     * exclusively match it.
     *
     * @param action the configuration of the repositories
     */
    RepositoryRegistrar exclusiveContent(Action<? super ExclusiveContentRepository> action);
}
